package com.tongji.cst3492.jentor.model;

import com.tongji.cst3492.jentor.context.EditGuard;

/** One file selected by the edit planner, with the exact intended changes. */
public record EditPlanEntry(String path, String instruction) {
    public EditPlanEntry {
        path = EditGuard.normalizeRelativePath(path);
        instruction = instruction == null ? "" : instruction.strip();
    }
}
