package com.tongji.cst3492.ta.model;

import java.util.List;

/** The whole workspace index: one outline per source file, plus build metadata. */
public record WorkspaceIndex(String projectPath, long builtAt, List<WorkspaceOutline> files) {
    public WorkspaceIndex {
        projectPath = projectPath == null ? "" : projectPath;
        files = files == null ? List.of() : List.copyOf(files);
    }

    public static WorkspaceIndex empty(String projectPath, long builtAt) {
        return new WorkspaceIndex(projectPath, builtAt, List.of());
    }

    public int size() {
        return files.size();
    }
}
