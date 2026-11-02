package com.tongji.cst3492.jentor.conversation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** A complete locally persisted chat conversation. */
public record Conversation(
        int version,
        String id,
        String title,
        long createdAt,
        long updatedAt,
        List<ConversationMessage> messages) {

    public static final int SCHEMA_VERSION = 1;
    private static final int MAX_TITLE_CHARS = 48;

    public Conversation {
        id = id == null ? "" : id;
        title = title == null || title.isBlank() ? "New conversation" : title;
        messages = messages == null ? List.of() : List.copyOf(messages);
    }

    public static Conversation create(String id, String firstQuestion, long timestamp) {
        return new Conversation(
                SCHEMA_VERSION, id, titleFrom(firstQuestion), timestamp, timestamp, List.of());
    }

    public Conversation append(
            ConversationMessage user,
            ConversationMessage assistant,
            long timestamp) {
        List<ConversationMessage> updated = new ArrayList<>(messages);
        updated.add(user);
        updated.add(assistant);
        return new Conversation(SCHEMA_VERSION, id, title, createdAt, timestamp, updated);
    }

    public Conversation withTitle(String newTitle) {
        return new Conversation(
                SCHEMA_VERSION, id, titleFrom(newTitle),
                createdAt, System.currentTimeMillis(), messages);
    }

    public ConversationSummary summary() {
        String preview = "";
        for (int index = messages.size() - 1; index >= 0; index--) {
            ConversationMessage message = messages.get(index);
            if ("assistant".equals(message.role()) && !message.content().isBlank()) {
                preview = oneLine(message.content(), 100);
                break;
            }
        }
        return new ConversationSummary(
                id, title, createdAt, updatedAt, messages.size(), preview);
    }

    public static String titleFrom(String question) {
        String title = oneLine(question, MAX_TITLE_CHARS);
        return title.isBlank() ? "New conversation" : title;
    }

    private static String oneLine(String value, int limit) {
        String normalized = value == null ? ""
                : value.replaceAll("\\s+", " ").trim();
        if (normalized.codePointCount(0, normalized.length()) <= limit) {
            return normalized;
        }
        int end = normalized.offsetByCodePoints(0, Math.max(0, limit - 1));
        return normalized.substring(0, end) + "…";
    }
}
