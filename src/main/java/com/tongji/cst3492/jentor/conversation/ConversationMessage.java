package com.tongji.cst3492.jentor.conversation;

import com.tongji.cst3492.jentor.llm.Answer;
import com.tongji.cst3492.jentor.llm.ChatMessage;
import com.tongji.cst3492.jentor.llm.CitationStatus;
import com.tongji.cst3492.jentor.model.SourceReference;

import java.util.List;

/** One visible message stored in a conversation history file. */
public record ConversationMessage(
        String role,
        String content,
        long timestamp,
        String attachmentSummary,
        boolean grounded,
        CitationStatus citationStatus,
        List<SourceReference> sources,
        List<String> warnings,
        String disclosure,
        String contextNote,
        boolean editResult) {

    public ConversationMessage {
        role = role == null ? "" : role;
        content = content == null ? "" : content;
        attachmentSummary = attachmentSummary == null ? "" : attachmentSummary;
        citationStatus = citationStatus == null
                ? CitationStatus.NOT_APPLICABLE : citationStatus;
        sources = sources == null ? List.of() : List.copyOf(sources);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        disclosure = disclosure == null ? "" : disclosure;
        contextNote = contextNote == null ? "" : contextNote;
    }

    public static ConversationMessage user(
            String content, long timestamp, String attachmentSummary) {
        return new ConversationMessage(
                "user", content, timestamp, attachmentSummary, false,
                CitationStatus.NOT_APPLICABLE, List.of(), List.of(), "", "", false);
    }

    public static ConversationMessage assistant(Answer answer, long timestamp) {
        return new ConversationMessage(
                "assistant",
                answer.text(),
                timestamp,
                "",
                answer.grounded(),
                answer.citationStatus(),
                answer.sources(),
                answer.warnings(),
                answer.disclosure(),
                answer.contextNote(),
                false);
    }

    public static ConversationMessage assistant(String text, long timestamp) {
        return plainAssistant(text, timestamp, false);
    }

    /** Includes applied, cancelled and unchanged edits so status survives reopening. */
    public static ConversationMessage editResult(String text, long timestamp) {
        return plainAssistant(text, timestamp, true);
    }

    private static ConversationMessage plainAssistant(
            String text, long timestamp, boolean editResult) {
        return new ConversationMessage(
                "assistant", text, timestamp, "", false,
                CitationStatus.NOT_APPLICABLE, List.of(), List.of(), "", "", editResult);
    }

    public ChatMessage toChatMessage() {
        return new ChatMessage(role, content);
    }

    public Answer toAnswer() {
        return new Answer(
                content,
                sources,
                List.of(),
                grounded,
                citationStatus,
                warnings,
                disclosure,
                contextNote);
    }
}
