package com.tongji.cst3492.jentor.model;

import com.tongji.cst3492.jentor.context.EditGuard;

/** A file deliberately excluded from an edit plan. */
public record SkippedEditFile(String path, String reason) {
    public SkippedEditFile {
        path = EditGuard.normalizeRelativePath(path);
        reason = reason == null ? "" : reason.strip();
    }
}
