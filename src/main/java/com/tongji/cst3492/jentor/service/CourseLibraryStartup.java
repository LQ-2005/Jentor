package com.tongji.cst3492.jentor.service;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import org.jetbrains.annotations.NotNull;

/** Starts the managed course PDF library before the tool window is opened. */
public final class CourseLibraryStartup implements StartupActivity {
    @Override
    public void runActivity(@NotNull Project project) {
        CourseLibraryService.getInstance().ensureStarted();
    }
}
