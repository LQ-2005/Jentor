package com.tongji.cst3492.ta.model;

/**
 * The rendered workspace outline for one question. {@code text} is empty when the
 * feature is off or the index is not ready; {@code stale} means the workspace
 * changed after the index was built, so the answer must say it may be outdated.
 */
public record WorkspaceContext(String text, boolean stale, int fileCount) {
    public static WorkspaceContext empty() {
        return new WorkspaceContext("", false, 0);
    }

    public WorkspaceContext {
        text = text == null ? "" : text;
    }

    public boolean isEmpty() {
        return text.isBlank();
    }
}
