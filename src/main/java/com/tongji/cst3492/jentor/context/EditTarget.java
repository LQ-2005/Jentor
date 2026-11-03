package com.tongji.cst3492.jentor.context;

/** One file and an optional same-file line range selected for an AI edit. */
public record EditTarget(String path, int startLine, int endLine) {
    public EditTarget {
        path = EditGuard.normalizeRelativePath(path);
        if (startLine <= 0 || endLine < startLine) {
            startLine = 0;
            endLine = 0;
        }
    }

    public boolean hasRange() {
        return startLine > 0;
    }
}
