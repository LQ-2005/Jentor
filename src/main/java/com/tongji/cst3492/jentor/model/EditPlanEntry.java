package com.tongji.cst3492.jentor.model;

import com.tongji.cst3492.jentor.context.EditGuard;

/** One file selected by the edit planner, with the exact intended changes. */
public record EditPlanEntry(
        String path, EditOperation operation, String instruction) {
    public EditPlanEntry {
        path = EditGuard.normalizeRelativePath(path);
        operation = operation == null ? EditOperation.MODIFY : operation;
        instruction = instruction == null ? "" : instruction.strip();
    }

    public EditPlanEntry(String path, String instruction) {
        this(path, EditOperation.MODIFY, instruction);
    }
}
