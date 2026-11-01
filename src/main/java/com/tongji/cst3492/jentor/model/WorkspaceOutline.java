package com.tongji.cst3492.jentor.model;

import java.util.List;

/**
 * Structural outline of one workspace source file: package, type declaration and
 * member signatures. Method bodies are never kept, which is what makes the
 * workspace index cheap enough to build on project open.
 */
public record WorkspaceOutline(
        String path,
        String packageName,
        String typeName,
        int lineCount,
        List<String> members,
        long modifiedAt) {

    public WorkspaceOutline {
        path = path == null ? "" : path;
        packageName = packageName == null ? "" : packageName;
        typeName = typeName == null ? "" : typeName;
        members = members == null ? List.of() : List.copyOf(members);
    }

    /** Outline without a modification stamp; used by tests and callers that do not need it. */
    public WorkspaceOutline(
            String path,
            String packageName,
            String typeName,
            int lineCount,
            List<String> members) {
        this(path, packageName, typeName, lineCount, members, 0L);
    }
}
