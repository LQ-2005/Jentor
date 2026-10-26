package com.tongji.cst3492.ta.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public final class OpenAiCompatibleClient implements ChatClient {
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    @Override
    public String complete(
            LlmConfig config,
            List<ChatMessage> messages,
            CancellationToken token) throws IOException {
        token.throwIfCancelled();
        if (config.baseUrl() == null || config.baseUrl().isBlank()) {
            throw new IOException("Base URL is not configured.");
        }
        if (config.apiKey() == null || config.apiKey().isBlank()) {
            throw new IOException("API key is not configured.");
        }
        if (config.model() == null || config.model().isBlank()) {
            throw new IOException("Chat model is not configured.");
        }

        ObjectNode payload = mapper.createObjectNode();
        payload.put("model", config.model());
        payload.put("temperature", config.temperature());
        payload.put("max_tokens", config.maxTokens());
        payload.put("stream", false);
        ArrayNode messageArray = payload.putArray("messages");
        for (ChatMessage message : messages) {
            ObjectNode messageNode = messageArray.addObject();
            messageNode.put("role", message.role());
            messageNode.put("content", message.content());
        }

        HttpRequest request = HttpRequest.newBuilder(URI.create(chatCompletionsUrl(config.baseUrl())))
                .timeout(Duration.ofSeconds(90))
                .header("Authorization", "Bearer " + config.apiKey())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        mapper.writeValueAsString(payload), StandardCharsets.UTF_8))
                .build();

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

        JsonNode root = mapper.readTree(response.body());
        JsonNode content = root.path("choices").path(0).path("message").path("content");
        if (content.isMissingNode() || content.isNull() || content.asText().isBlank()) {
            throw new IOException("The LLM response did not contain message content: "
                    + truncate(response.body(), 600));
        }
        token.throwIfCancelled();
        return content.asText();
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
