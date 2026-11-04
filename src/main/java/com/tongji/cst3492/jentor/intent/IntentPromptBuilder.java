package com.tongji.cst3492.jentor.intent;

import com.tongji.cst3492.jentor.llm.ChatMessage;

import java.util.ArrayList;
import java.util.List;

/** Prompt for the small semantic-routing request made before an answer or edit. */
public final class IntentPromptBuilder {
    private static final int MAX_HISTORY_MESSAGES = 6;
    private static final int MAX_MESSAGE_CHARS = 1_200;
    private static final int MAX_LATEST_ASSISTANT_CHARS = 6_000;
    private static final int MAX_ACTIVE_SNIPPET_CHARS = 2_000;

    public List<ChatMessage> build(
            String question,
            List<ChatMessage> history,
            String lastAppliedEdit,
            String activeFile,
            String activeSnippet,
            List<String> workspaceFiles,
            String language) {
        boolean zh = "zh".equals(language);
        String system = """
                You are the semantic intent router for Jentor, an IntelliJ IDEA course assistant.
                Decide what the latest USER QUESTION asks Jentor to do. Return JSON only.

                Allowed intents:
                - CHAT: explain, discuss, answer a question, or review code without changing it.
                - ANALYZE: inspect code and identify concrete problems, but do not change it yet.
                - EDIT: make a code change, or adopt fixes proposed in an earlier assistant answer.
                - STATUS: ask what was changed, whether a previous edit succeeded, or which files changed.
                - CLARIFY: the intent or target is genuinely ambiguous.

                Important rules:
                - A question containing a word such as "change" or "修改" is not automatically EDIT.
                - "What changed?" and "修改了哪些？" are STATUS.
                - A request to apply the previous analysis is EDIT with usePreviousAnalysis=true.
                - Only EDIT means code may be modified later.
                - For EDIT, put the actionable instruction in "instruction".
                - Use workspace file/type/member names from context as targetHints.
                - Never include code changes, patches, or prose outside JSON.
                - If confidence is below 0.65, return CLARIFY.

                Return exactly:
                {
                  "intent": "CHAT|ANALYZE|EDIT|STATUS|CLARIFY",
                  "confidence": 0.0,
                  "instruction": "",
                  "usePreviousAnalysis": false,
                  "targetHints": [],
                  "clarification": ""
                }
                """;
        if (zh) {
            system += "\n用户使用中文时，clarification 使用简体中文。";
        } else {
            system += "\nUse English for clarification when the user writes in English.";
        }

        StringBuilder user = new StringBuilder("RECENT CONVERSATION:\n");
        List<ChatMessage> recent = trimHistory(history);
        if (recent.isEmpty()) {
            user.append("(none)\n");
        } else {
            for (int index = 0; index < recent.size(); index++) {
                ChatMessage message = recent.get(index);
                int limit = index == recent.size() - 1
                        && "assistant".equals(message.role())
                        ? MAX_LATEST_ASSISTANT_CHARS : MAX_MESSAGE_CHARS;
                user.append(message.role().toUpperCase())
                        .append(": ")
                        .append(limit(message.content(), limit))
                        .append('\n');
            }
        }
        user.append("\nLAST APPLIED EDIT:\n")
                .append(blankAsNone(lastAppliedEdit))
                .append("\n\nACTIVE FILE:\n")
                .append(blankAsNone(activeFile))
                .append("\n\nACTIVE CODE SNIPPET:\n")
                .append(blankAsNone(limit(activeSnippet, MAX_ACTIVE_SNIPPET_CHARS)))
                .append("\n\nWORKSPACE FILES:\n")
                .append(workspaceFiles == null || workspaceFiles.isEmpty()
                        ? "(none)" : String.join("\n", workspaceFiles))
                .append("\n\nUSER QUESTION:\n")
                .append(question == null ? "" : question.strip());
        return List.of(
                ChatMessage.system(system.strip()),
                ChatMessage.user(user.toString()));
    }

    private static List<ChatMessage> trimHistory(List<ChatMessage> history) {
        if (history == null || history.isEmpty()) {
            return List.of();
        }
        List<ChatMessage> recent = new ArrayList<>();
        for (int index = Math.max(0, history.size() - MAX_HISTORY_MESSAGES);
                index < history.size(); index++) {
            ChatMessage message = history.get(index);
            if (message != null && !"system".equals(message.role())
                    && message.content() != null && !message.content().isBlank()) {
                recent.add(message);
            }
        }
        return List.copyOf(recent);
    }

    private static String blankAsNone(String value) {
        return value == null || value.isBlank() ? "(none)" : value.strip();
    }

    private static String limit(String value, int maxChars) {
        if (value == null) {
            return "";
        }
        return value.length() <= maxChars
                ? value : value.substring(0, maxChars) + "\n[truncated]";
    }
}
