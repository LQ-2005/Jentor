package com.tongji.cst3492.jentor.model;

import java.util.List;

/** Structured multi-file plan produced before generating code changes. */
public record EditPlan(
        String summary,
        List<EditPlanEntry> files,
        List<SkippedEditFile> skipped,
        String clarification) {

    public static final int MAX_FILES = 8;

    public EditPlan {
        summary = summary == null ? "" : summary.strip();
        files = files == null ? List.of() : List.copyOf(files);
        skipped = skipped == null ? List.of() : List.copyOf(skipped);
        clarification = clarification == null ? "" : clarification.strip();
    }

    public boolean hasFiles() {
        return !files.isEmpty();
    }
}
