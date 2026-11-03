package com.tongji.cst3492.jentor.llm;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic client used by unit tests and the offline demo. It extracts the
 * first context block and cites it, so the whole answer path can be exercised
 * without an API key. In edit mode it returns a complete file with one extra
 * comment, which exercises the diff and apply path offline.
 */
public final class MockChatClient implements ChatClient {
    private static final Pattern CONTEXT =
            Pattern.compile("document=\"([^\"]+)\" locator=\"([^\"]+)\"");
    private static final Pattern FILE_LINE = Pattern.compile("(?m)^FILE:\\s*(.+)$");
    private static final Pattern FILE_BODY =
            Pattern.compile("--- BEGIN FILE ---\\R([\\s\\S]*?)\\R--- END FILE ---");
    private static final int STREAM_CHUNK_CHARS = 24;

    @Override
    public String complete(
            LlmConfig config,
            List<ChatMessage> messages,
            CancellationToken token) {
        token.throwIfCancelled();
        String userPrompt = messages.stream()
                .filter(message -> "user".equals(message.role()))
                .map(ChatMessage::content)
                .reduce((first, second) -> second)
                .orElse("");
        boolean editRequest = messages.stream().anyMatch(message ->
                "system".equals(message.role())
                        && message.content().contains("jentor-edit"));
        if (editRequest) {
            return mockEdit(userPrompt);
        }
        boolean chinese = messages.stream().anyMatch(message ->
                "system".equals(message.role())
                        && message.content().contains("Simplified Chinese"));
        Matcher matcher = CONTEXT.matcher(userPrompt);
        if (matcher.find()) {
            return "[" + matcher.group(1) + ", " + matcher.group(2) + "] "
                    + (chinese
                    ? "这是一个用于验证检索和引用链路的模拟回答。"
                    : "This is a mock answer used to verify the retrieval and citation path.");
        }
        return (chinese ? PromptBuilder.FALLBACK_SENTENCE_ZH : PromptBuilder.FALLBACK_SENTENCE)
                + "\n\n"
                + (chinese
                ? "这是一个用于验证通用知识路径的模拟回答。"
                : "This is a mock answer used to verify the general-knowledge path.");
    }

    private static String mockEdit(String prompt) {
        Matcher file = FILE_LINE.matcher(prompt);
        Matcher body = FILE_BODY.matcher(prompt);
        if (!file.find() || !body.find()) {
            return "REFUSE: the mock model needs a file to edit";
        }
        String path = file.group(1).strip();
        String content = body.group(1);
        return "```jentor-edit path=" + path + "\n"
                + content + "\n// jentor mock edit\n"
                + "```";
    }

    /**
     * Splits the deterministic answer into small pieces so the streaming UI path
     * can be exercised without network access.
     */
    @Override
    public boolean supportsStreaming() {
        return true;
    }

    @Override
    public String stream(
            LlmConfig config,
            List<ChatMessage> messages,
            CancellationToken token,
            StreamSink sink) {
        String text = complete(config, messages, token);
        for (int start = 0; start < text.length(); start += STREAM_CHUNK_CHARS) {
            token.throwIfCancelled();
            sink.append(text.substring(start, Math.min(text.length(), start + STREAM_CHUNK_CHARS)));
        }
        return text;
    }
}
