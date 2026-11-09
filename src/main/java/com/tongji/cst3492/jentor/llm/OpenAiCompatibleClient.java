package com.tongji.cst3492.jentor.llm;

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
    private static final int MAX_LENGTH_RETRIES = 2;
    private static final int MIN_RETRY_TOKENS = 4_096;
    private static final int MAX_RETRY_TOKENS = 32_768;

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

        LlmConfig attemptConfig = config;
        for (int attempt = 0; ; attempt++) {
            token.throwIfCancelled();
            HttpResponse<String> response = send(
                    buildRequest(attemptConfig, messages, false),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8), token);
            token.throwIfCancelled();
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IOException(
                        "LLM request failed with HTTP " + response.statusCode() + ": "
                                + truncate(response.body(), 600));
            }
            try {
                return messageContent(response.body());
            } catch (CompletionLimitException exhausted) {
                token.throwIfCancelled();
                attemptConfig = retryConfig(attemptConfig, attempt);
            }
        }
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

        LlmConfig attemptConfig = config;
        for (int attempt = 0; ; attempt++) {
            token.throwIfCancelled();
            try {
                return streamOnce(attemptConfig, messages, token, sink);
            } catch (CompletionLimitException exhausted) {
                token.throwIfCancelled();
                attemptConfig = retryConfig(attemptConfig, attempt);
                // A retry starts a fresh completion, not a continuation of partial code.
                sink.reset();
            }
        }
    }

    private String streamOnce(
            LlmConfig config,
            List<ChatMessage> messages,
            CancellationToken token,
            StreamSink sink) throws IOException {
        HttpResponse<InputStream> response = send(
                buildRequest(config, messages, true),
                HttpResponse.BodyHandlers.ofInputStream(), token);
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
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(body, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                token.throwIfCancelled();
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
        if (accumulated.toString().isBlank()) {
            throw missingContent();
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
        if (root == null) {
            return null;
        }
        requireNoError(root);
        JsonNode choice = root.path("choices").path(0);
        requireComplete(choice);
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
                    "The LLM response was not valid JSON.", malformed);
        }
        if (root == null) {
            throw missingContent();
        }
        requireNoError(root);
        JsonNode choice = root.path("choices").path(0);
        requireComplete(choice);
        JsonNode content = choice.path("message").path("content");
        if (!content.isTextual() || content.asText().isBlank()) {
            throw missingContent();
        }
        return content.asText();
    }

    private static void requireNoError(JsonNode root) throws IOException {
        JsonNode error = root.path("error");
        if (!error.isMissingNode() && !error.isNull()) {
            throw new IOException("LLM request failed: "
                    + truncate(error.path("message").asText("Provider returned an error."), 600));
        }
    }

    private static void requireComplete(JsonNode choice) throws CompletionLimitException {
        String finishReason = choice.path("finish_reason").asText("");
        if ("length".equals(finishReason) || "max_tokens".equals(finishReason)) {
            throw new CompletionLimitException();
        }
    }

    private static IOException missingContent() {
        // Reasoning is not an answer or an edit proposal. Do not expose it as diagnostics.
        return new IOException("The LLM returned no answer content (it may have returned only "
                + "reasoning). Increase max_tokens or use a non-reasoning model, then retry.");
    }

    private static LlmConfig retryConfig(LlmConfig config, int attempt) throws IOException {
        int nextTokens = (int) Math.min(MAX_RETRY_TOKENS,
                Math.max(MIN_RETRY_TOKENS, (long) config.maxTokens() * 2));
        if (attempt >= MAX_LENGTH_RETRIES || nextTokens <= config.maxTokens()) {
            throw new IOException("The LLM output was truncated after reaching max_tokens="
                    + config.maxTokens() + ". Increase the output token limit or reduce the "
                    + "requested change, then retry. Incomplete output was not accepted.");
        }
        return new LlmConfig(config.baseUrl(), config.apiKey(), config.model(),
                config.temperature(), nextTokens);
    }

    private <T> HttpResponse<T> send(
            HttpRequest request,
            HttpResponse.BodyHandler<T> handler,
            CancellationToken token) throws IOException {
        token.throwIfCancelled();
        CompletableFuture<HttpResponse<T>> future = httpClient.sendAsync(request, handler);
        token.onCancel(() -> future.cancel(true));
        try {
            return future.get();
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new IOException("Generation cancelled", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof CancellationException cancellation) {
                throw cancellation;
            }
            throw new IOException("LLM request failed", cause);
        }
    }

    private static final class CompletionLimitException extends IOException {
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
