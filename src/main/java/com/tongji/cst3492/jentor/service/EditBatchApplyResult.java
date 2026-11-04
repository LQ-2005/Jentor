package com.tongji.cst3492.jentor.service;

import java.util.List;

/** Result of validating and applying all files in one edited batch. */
public record EditBatchApplyResult(
        Status status,
        List<String> appliedPaths,
        List<String> errors) {

    public enum Status {
        APPLIED,
        NO_CHANGE,
        FAILED
    }

    public EditBatchApplyResult {
        appliedPaths = appliedPaths == null ? List.of() : List.copyOf(appliedPaths);
        errors = errors == null ? List.of() : List.copyOf(errors);
    }

    public static EditBatchApplyResult applied(List<String> paths) {
        return new EditBatchApplyResult(Status.APPLIED, paths, List.of());
    }

    public static EditBatchApplyResult noChange() {
        return new EditBatchApplyResult(Status.NO_CHANGE, List.of(), List.of());
    }

    public static EditBatchApplyResult failed(List<String> errors) {
        return new EditBatchApplyResult(Status.FAILED, List.of(), errors);
    }

    public boolean isApplied() {
        return status == Status.APPLIED;
    }
}
