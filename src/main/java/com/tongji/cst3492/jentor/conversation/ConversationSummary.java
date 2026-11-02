package com.tongji.cst3492.jentor.conversation;

/** Lightweight metadata shown in the history list without loading full messages. */
public record ConversationSummary(
        String id,
        String title,
        long createdAt,
        long updatedAt,
        int messageCount,
        String preview) {

    public ConversationSummary {
        id = id == null ? "" : id;
        title = title == null || title.isBlank() ? "New conversation" : title;
        preview = preview == null ? "" : preview;
    }
}
