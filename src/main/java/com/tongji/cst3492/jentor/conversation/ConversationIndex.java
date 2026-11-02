package com.tongji.cst3492.jentor.conversation;

import java.util.List;

/** Lightweight index for fast history-list loading. */
public record ConversationIndex(
        int version,
        String lastActiveId,
        List<ConversationSummary> conversations) {

    public ConversationIndex {
        lastActiveId = lastActiveId == null ? "" : lastActiveId;
        conversations = conversations == null ? List.of() : List.copyOf(conversations);
    }

    public static ConversationIndex empty() {
        return new ConversationIndex(1, "", List.of());
    }
}
