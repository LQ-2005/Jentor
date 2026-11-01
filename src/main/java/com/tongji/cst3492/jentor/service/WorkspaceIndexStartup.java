package com.tongji.cst3492.jentor.service;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import org.jetbrains.annotations.NotNull;

/** Builds the workspace index once when a project opens, off the EDT. */
public final class WorkspaceIndexStartup implements StartupActivity {
    @Override
    public void runActivity(@NotNull Project project) {
        project.getService(WorkspaceIndexService.class).ensureBuiltAsync();
    }
}
