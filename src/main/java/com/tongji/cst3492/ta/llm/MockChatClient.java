package com.tongji.cst3492.ta.llm;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic client used by unit tests and the offline demo. It extracts the
 * first context block and cites it, so the whole answer path can be exercised
 * without an API key.
 */
public final class MockChatClient implements ChatClient {
    private static final Pattern CONTEXT =
            Pattern.compile("document=\"([^\"]+)\" locator=\"([^\"]+)\"");

    @Override
    public String complete(
            LlmConfig config,
            List<ChatMessage> messages,
            CancellationToken token) {
        token.throwIfCancelled();
        boolean chinese = messages.stream().anyMatch(message ->
                "system".equals(message.role())
                        && message.content().contains("Simplified Chinese"));
        String userPrompt = messages.stream()
                .filter(message -> "user".equals(message.role()))
                .map(ChatMessage::content)
                .reduce((first, second) -> second)
                .orElse("");
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
}
