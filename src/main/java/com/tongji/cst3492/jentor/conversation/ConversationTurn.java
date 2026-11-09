package com.tongji.cst3492.jentor.conversation;

import java.util.Objects;
import java.util.UUID;

/** Original user input captured before intent routing or asynchronous generation. */
public record ConversationTurn(String question, String attachmentSummary, long askedAt) {
    public ConversationTurn {
        question = question == null ? "" : question;
        attachmentSummary = attachmentSummary == null ? "" : attachmentSummary;
    }

    public Conversation appendTo(Conversation current, ConversationMessage assistant) {
        Objects.requireNonNull(assistant, "assistant");
        Conversation conversation = current == null
                ? Conversation.create(UUID.randomUUID().toString(), question, askedAt)
                : current;
        return conversation.append(
                ConversationMessage.user(question, askedAt, attachmentSummary),
                assistant,
                assistant.timestamp());
    }
}
