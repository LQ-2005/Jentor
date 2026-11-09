package com.tongji.cst3492.jentor.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAiCompatibleClientTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String REASONING = "private reasoning must not become the answer";
    private static final List<ChatMessage> MESSAGES = List.of(
            ChatMessage.system("Return a code edit."),
            ChatMessage.user("帮我减少代码的冗余程度"));

    private final List<JsonNode> requests = new CopyOnWriteArrayList<>();
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void reasoningOnlyLengthResponseRetriesWithMoreBudgetAndOriginalMessages() throws IOException {
        start(json(completion(null, "length", "reasoning")),
                json(completion("complete edit", "stop", "reasoning")));

        String result = new OpenAiCompatibleClient().complete(
                config(600), MESSAGES, new CancellationToken());

        assertEquals("complete edit", result);
        assertEquals(List.of(600, 4096), requestedBudgets());
        assertEquals(requests.getFirst().path("messages"), requests.getLast().path("messages"));
        assertEquals(MESSAGES.getLast().content(),
                requests.getLast().path("messages").path(1).path("content").asText());
        assertFalse(requests.getLast().path("stream").asBoolean());
    }

    @Test
    void incompleteCodeIsRegeneratedInsteadOfReturnedAsSuccessfulEdit() throws IOException {
        start(json(completion("```jentor-edit path=Main.java\nclass Main {", "length", null)),
                json(completion("```jentor-edit path=Main.java\nclass Main {}\n```", "stop", null)));

        String result = new OpenAiCompatibleClient().complete(
                config(1500), MESSAGES, new CancellationToken());

        assertEquals("```jentor-edit path=Main.java\nclass Main {}\n```", result);
        assertEquals(List.of(1500, 4096), requestedBudgets());
    }

    @Test
    void repeatedTruncationStopsAfterTwoRetriesWithActionableError() throws IOException {
        Reply truncated = json(completion(null, "length", "reasoning_content"));
        start(truncated, truncated, truncated);

        IOException error = assertThrows(IOException.class, () -> new OpenAiCompatibleClient()
                .complete(config(600), MESSAGES, new CancellationToken()));

        assertEquals(List.of(600, 4096, 8192), requestedBudgets());
        assertTrue(error.getMessage().contains("max_tokens"));
        assertTrue(error.getMessage().contains("8192"));
        assertFalse(error.getMessage().contains(REASONING));
    }

    @Test
    void automaticBudgetGrowthStopsAtItsCeiling() throws IOException {
        Reply truncated = json(completion(null, "max_tokens", "reasoning"));
        start(truncated, truncated);

        IOException error = assertThrows(IOException.class, () -> new OpenAiCompatibleClient()
                .complete(config(20000), MESSAGES, new CancellationToken()));

        assertEquals(List.of(20000, 32768), requestedBudgets());
        assertTrue(error.getMessage().contains("32768"));
    }

    @Test
    void completeAnswerDoesNotRetryOrExposeReasoning() throws IOException {
        start(json(completion("finished", "stop", "reasoning_content")));

        String result = new OpenAiCompatibleClient().complete(
                config(1500), MESSAGES, new CancellationToken());

        assertEquals("finished", result);
        assertEquals(1, requests.size());
    }

    @Test
    void reasoningWithoutLengthIsAnErrorAndDoesNotLeakResponseBody() throws IOException {
        start(json(completion(null, "stop", "reasoning")));

        IOException error = assertThrows(IOException.class, () -> new OpenAiCompatibleClient()
                .complete(config(600), MESSAGES, new CancellationToken()));

        assertEquals(1, requests.size());
        assertFalse(error.getMessage().contains(REASONING));
        assertFalse(error.getMessage().contains("choices"));
    }

    @Test
    void reasoningOnlyStreamRetriesWithoutShowingTheThinking() throws IOException {
        start(events(chunk(null, "reasoning_content", null), chunk(null, null, "length")),
                events(chunk("finished", null, "stop")));
        RecordingSink sink = new RecordingSink();

        String result = new OpenAiCompatibleClient().stream(
                config(600), MESSAGES, new CancellationToken(), sink);

        assertEquals("finished", result);
        assertEquals("finished", sink.visible.toString());
        assertFalse(String.join("", sink.appended).contains(REASONING));
        assertEquals(1, sink.resets);
        assertEquals(List.of(600, 4096), requestedBudgets());
        assertTrue(requests.getLast().path("stream").asBoolean());
    }

    @Test
    void streamRetryClearsPartialAnswerBeforeRegeneration() throws IOException {
        start(events(chunk("incomplete code", null, null), chunk(null, null, "length")),
                events(chunk("complete code", null, null), chunk(null, null, "stop")));
        RecordingSink sink = new RecordingSink();

        String result = new OpenAiCompatibleClient().stream(
                config(1500), MESSAGES, new CancellationToken(), sink);

        assertEquals("complete code", result);
        assertEquals("complete code", sink.visible.toString());
        assertEquals(List.of("incomplete code", "complete code"), sink.appended);
        assertEquals(1, sink.resets);
    }

    @Test
    void plainJsonResponseToStreamingRequestUsesTheSameTruncationRecovery() throws IOException {
        start(json(completion(null, "length", "reasoning")),
                json(completion("finished", "stop", null)));
        RecordingSink sink = new RecordingSink();

        String result = new OpenAiCompatibleClient().stream(
                config(600), MESSAGES, new CancellationToken(), sink);

        assertEquals("finished", result);
        assertEquals("finished", sink.visible.toString());
        assertEquals(List.of(600, 4096), requestedBudgets());
        assertEquals(1, sink.resets);
    }

    @Test
    void reasoningOnlyStreamWithoutLengthFailsWithoutLeakingThinking() throws IOException {
        start(events(chunk(null, "reasoning", null), chunk(null, null, "stop")));
        RecordingSink sink = new RecordingSink();

        IOException error = assertThrows(IOException.class, () -> new OpenAiCompatibleClient()
                .stream(config(600), MESSAGES, new CancellationToken(), sink));

        assertEquals(1, requests.size());
        assertTrue(sink.appended.isEmpty());
        assertFalse(error.getMessage().contains(REASONING));
        assertFalse(error.getMessage().contains("choices"));
    }

    @Test
    void cancellationDuringStreamResetPreventsTheRetryRequest() throws IOException {
        start(events(chunk("partial", null, "length")));
        CancellationToken token = new CancellationToken();
        StreamSink sink = new StreamSink() {
            @Override
            public void append(String delta) {
            }

            @Override
            public void reset() {
                token.cancel();
            }
        };

        assertThrows(CancellationException.class, () -> new OpenAiCompatibleClient()
                .stream(config(600), MESSAGES, token, sink));

        assertEquals(1, requests.size());
    }

    @Test
    void alreadyCancelledRequestsDoNotContactTheServer() throws IOException {
        start(json(completion("unused", "stop", null)));
        CancellationToken token = new CancellationToken();
        token.cancel();
        OpenAiCompatibleClient client = new OpenAiCompatibleClient();

        assertThrows(CancellationException.class, () -> client.complete(config(600), MESSAGES, token));
        assertThrows(CancellationException.class, () -> client.stream(
                config(600), MESSAGES, token, StreamSink.NONE));

        assertTrue(requests.isEmpty());
    }

    private void start(Reply... replies) throws IOException {
        AtomicInteger next = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            try (exchange) {
                requests.add(MAPPER.readTree(exchange.getRequestBody()));
                int index = next.getAndIncrement();
                Reply reply = index < replies.length ? replies[index]
                        : new Reply("application/json", "{\"error\":\"unexpected request\"}");
                byte[] body = reply.body().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", reply.contentType());
                exchange.sendResponseHeaders(index < replies.length ? 200 : 500, body.length);
                exchange.getResponseBody().write(body);
            }
        });
        server.start();
    }

    private LlmConfig config(int maxTokens) {
        return new LlmConfig("http://127.0.0.1:" + server.getAddress().getPort(),
                "test-key", "MiniMax-M3.1-Flash", 0.0, maxTokens);
    }

    private List<Integer> requestedBudgets() {
        return requests.stream().map(request -> request.path("max_tokens").asInt()).toList();
    }

    private static String completion(String content, String finishReason, String reasoningField) {
        ObjectNode root = MAPPER.createObjectNode();
        ObjectNode choice = root.putArray("choices").addObject();
        choice.put("finish_reason", finishReason);
        ObjectNode message = choice.putObject("message");
        message.put("role", "assistant");
        message.put("content", content);
        if (reasoningField != null) {
            message.put(reasoningField, REASONING);
        }
        return root.toString();
    }

    private static String chunk(String content, String reasoningField, String finishReason) {
        ObjectNode root = MAPPER.createObjectNode();
        ObjectNode choice = root.putArray("choices").addObject();
        choice.put("finish_reason", finishReason);
        ObjectNode delta = choice.putObject("delta");
        if (content != null) {
            delta.put("content", content);
        }
        if (reasoningField != null) {
            delta.put(reasoningField, REASONING);
        }
        return root.toString();
    }

    private static Reply json(String body) {
        return new Reply("application/json", body);
    }

    private static Reply events(String... chunks) {
        StringBuilder body = new StringBuilder();
        for (String chunk : chunks) {
            body.append("data: ").append(chunk).append("\n\n");
        }
        return new Reply("text/event-stream", body.append("data: [DONE]\n\n").toString());
    }

    private record Reply(String contentType, String body) {
    }

    private static final class RecordingSink implements StreamSink {
        private final StringBuilder visible = new StringBuilder();
        private final List<String> appended = new java.util.ArrayList<>();
        private int resets;

        @Override
        public void append(String delta) {
            appended.add(delta);
            visible.append(delta);
        }

        @Override
        public void reset() {
            resets++;
            visible.setLength(0);
        }
    }
}
