package com.tongji.cst3492.ta.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public final class OpenAiCompatibleClient implements ChatClient {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(20);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(90);
    private static final Duration STREAM_TIMEOUT = Duration.ofMinutes(5);
    private static final String DATA_PREFIX = "data:";
    private static final String DONE_MARKER = "[DONE]";

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .build();

    @Override
    public String complete(
            LlmConfig config,
            List<ChatMessage> messages,
            CancellationToken token) throws IOException {
        token.throwIfCancelled();
        requireConfig(config);

        HttpRequest request = buildRequest(config, messages, false);
        token.throwIfCancelled();
        CompletableFuture<HttpResponse<String>> future = httpClient.sendAsync(
                request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        token.onCancel(() -> future.cancel(true));
        HttpResponse<String> response;
        try {
            response = future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Generation cancelled", e);
        } catch (CancellationException e) {
            throw e;
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof CancellationException cancellation) {
                throw cancellation;
            }
            throw new IOException("LLM request failed", cause);
        }
        token.throwIfCancelled();

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException(
                    "LLM request failed with HTTP " + response.statusCode() + ": "
                            + truncate(response.body(), 600));
        }
        return messageContent(response.body());
    }

    @Override
    public boolean supportsStreaming() {
        return true;
    }

    /**
     * Streams the answer with server-sent events. Cancelling the token closes the
     * response body, which interrupts the blocking read.
     */
    @Override
    public String stream(
            LlmConfig config,
            List<ChatMessage> messages,
            CancellationToken token,
            StreamSink sink) throws IOException {
        token.throwIfCancelled();
        requireConfig(config);

        HttpResponse<InputStream> response;
        try {
            response = httpClient.send(
                    buildRequest(config, messages, true),
                    HttpResponse.BodyHandlers.ofInputStream());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Generation cancelled", e);
        } catch (IOException e) {
            throw new IOException("LLM request failed: " + e.getMessage(), e);
        }

        InputStream body = response.body();
        token.onCancel(() -> closeQuietly(body));
        try {
            token.throwIfCancelled();
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IOException(
                        "LLM request failed with HTTP " + response.statusCode() + ": "
                                + truncate(readAll(body), 600));
            }
            String text = isEventStream(response)
                    ? readEventStream(body, token, sink)
                    : readPlainJson(body, sink);
            token.throwIfCancelled();
            return text;
        } catch (IOException e) {
            if (token.isCancelled()) {
                throw new CancellationException("Generation cancelled");
            }
            throw e;
        } finally {
            closeQuietly(body);
        }
    }

    private String readEventStream(
            InputStream body,
            CancellationToken token,
            StreamSink sink) throws IOException {
        StringBuilder accumulated = new StringBuilder();
        StringBuilder diagnostics = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(body, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                token.throwIfCancelled();
                if (diagnostics.length() < 400 && !line.isBlank()) {
                    diagnostics.append(line).append('\n');
                }
                if (line.isBlank() || line.startsWith(":")) {
                    continue;
                }
                if (!line.startsWith(DATA_PREFIX)) {
                    continue;
                }
                String data = line.substring(DATA_PREFIX.length()).trim();
                if (data.isEmpty()) {
                    continue;
                }
                if (DONE_MARKER.equals(data)) {
                    break;
                }
                String delta = deltaContent(data);
                if (delta == null || delta.isEmpty()) {
                    continue;
                }
                accumulated.append(delta);
                sink.append(delta);
            }
        }
        if (accumulated.length() == 0) {
            throw new IOException("The LLM stream completed without content: "
                    + truncate(diagnostics.toString(), 400));
        }
        return accumulated.toString();
    }

    /** Handles providers that ignore {@code stream} and answer with a normal JSON body. */
    private String readPlainJson(InputStream body, StreamSink sink) throws IOException {
        String payload = readAll(body);
        String text = messageContent(payload);
        sink.append(text);
        return text;
    }

    /**
     * Extracts the incremental text of one streamed chunk.
     * Returns {@code null} for chunks that carry only metadata, such as the first
     * chunk that announces the assistant role.
     */
    static String deltaContent(String data) throws IOException {
        JsonNode root;
        try {
            root = MAPPER.readTree(data);
        } catch (IOException malformed) {
            // Some providers send keep-alive text that is not JSON.
            return null;
        }
        JsonNode error = root.path("error");
        if (error.isObject()) {
            throw new IOException("LLM request failed: "
                    + truncate(error.path("message").asText(error.toString()), 600));
        }
        JsonNode choice = root.path("choices").path(0);
        JsonNode delta = choice.path("delta").path("content");
        if (delta.isTextual()) {
            return delta.asText();
        }
        JsonNode message = choice.path("message").path("content");
        if (message.isTextual()) {
            return message.asText();
        }
        return null;
    }

    private static String messageContent(String payload) throws IOException {
        JsonNode root;
        try {
            root = MAPPER.readTree(payload);
        } catch (IOException malformed) {
            throw new IOException(
                    "The LLM response was not valid JSON: " + truncate(payload, 600), malformed);
        }
        JsonNode content = root.path("choices").path(0).path("message").path("content");
        if (content.isMissingNode() || content.isNull() || content.asText().isBlank()) {
            throw new IOException(
                    "The LLM response did not contain message content: " + truncate(payload, 600));
        }
        return content.asText();
    }

    private HttpRequest buildRequest(
            LlmConfig config,
            List<ChatMessage> messages,
            boolean stream) throws IOException {
        ObjectNode payload = MAPPER.createObjectNode();
        payload.put("model", config.model());
        payload.put("temperature", config.temperature());
        payload.put("max_tokens", config.maxTokens());
        payload.put("stream", stream);
        ArrayNode messageArray = payload.putArray("messages");
        for (ChatMessage message : messages) {
            ObjectNode messageNode = messageArray.addObject();
            messageNode.put("role", message.role());
            messageNode.put("content", message.content());
        }

        return HttpRequest.newBuilder(URI.create(chatCompletionsUrl(config.baseUrl())))
                .timeout(stream ? STREAM_TIMEOUT : REQUEST_TIMEOUT)
                .header("Authorization", "Bearer " + config.apiKey())
                .header("Content-Type", "application/json")
                .header("Accept", stream ? "text/event-stream" : "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        MAPPER.writeValueAsString(payload), StandardCharsets.UTF_8))
                .build();
    }

    private static void requireConfig(LlmConfig config) throws IOException {
        if (config.baseUrl() == null || config.baseUrl().isBlank()) {
            throw new IOException("Base URL is not configured.");
        }
        if (config.apiKey() == null || config.apiKey().isBlank()) {
            throw new IOException("API key is not configured.");
        }
        if (config.model() == null || config.model().isBlank()) {
            throw new IOException("Chat model is not configured.");
        }
    }

    private static boolean isEventStream(HttpResponse<?> response) {
        return response.headers().firstValue("content-type")
                .map(value -> value.toLowerCase(Locale.ROOT).contains("text/event-stream"))
                .orElse(false);
    }

    private static String readAll(InputStream input) throws IOException {
        return new String(input.readAllBytes(), StandardCharsets.UTF_8);
    }

    private static void closeQuietly(Closeable closeable) {
        try {
            closeable.close();
        } catch (IOException ignored) {
            // Closing a cancelled or finished stream is best-effort.
        }
    }

    static String chatCompletionsUrl(String baseUrl) {
        String base = baseUrl.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (base.endsWith("/chat/completions")) {
            return base;
        }
        return base + "/chat/completions";
    }

    private static String truncate(String text, int limit) {
        if (text == null) {
            return "";
        }
        return text.length() <= limit ? text : text.substring(0, limit) + "...";
    }
}
