package com.tongji.cst3492.jentor.service;

/** Outcome of applying an AI-proposed edit. */
public record EditApplyResult(Status status, String message) {
    public enum Status {
        APPLIED,
        NO_CHANGE,
        CONFLICT,
        NOT_FOUND,
        REFUSED,
        FAILED
    }

    public boolean isApplied() {
        return status == Status.APPLIED;
    }

    public static EditApplyResult applied(String path) {
        return new EditApplyResult(Status.APPLIED, path);
    }

    public static EditApplyResult noChange(String path) {
        return new EditApplyResult(Status.NO_CHANGE, path);
    }

    public static EditApplyResult conflict(String message) {
        return new EditApplyResult(Status.CONFLICT, message);
    }

    public static EditApplyResult notFound(String path) {
        return new EditApplyResult(Status.NOT_FOUND, path);
    }

    public static EditApplyResult refused(String message) {
        return new EditApplyResult(Status.REFUSED, message);
    }

    public static EditApplyResult failed(String message) {
        return new EditApplyResult(Status.FAILED, message);
    }
}
