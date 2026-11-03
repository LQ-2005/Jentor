package com.tongji.cst3492.jentor.context;

import java.util.Locale;

/**
 * Validation rules for applying an AI-proposed edit. Pure logic so every guard
 * can be unit tested without an IDE.
 */
public final class EditGuard {
    /** A single edit never replaces more than this many characters. */
    public static final int MAX_CHARS = 200_000;

    private EditGuard() {
    }

    /**
     * Only text files the workspace features already accept may be written:
     * source and build scripts. Config and data formats stay read-only on
     * purpose, so an AI edit can never rewrite a properties file with secrets.
     */
    public static boolean isWritablePath(String path) {
        String normalized = normalizeRelativePath(path);
        return !normalized.isBlank() && CodeFilePolicy.isSupported(normalized);
    }

    public static boolean isTooLarge(String text) {
        return text != null && text.length() > MAX_CHARS;
    }

    /** True when the proposed content is indistinguishable from the current one. */
    public static boolean isNoOp(String current, String next) {
        return normalize(current).equals(normalize(next));
    }

    /**
     * A conflict means the document changed after the model saw it. A zero base
     * stamp means "no baseline recorded" and never conflicts.
     */
    public static boolean isConflict(long baseModificationStamp, long currentModificationStamp) {
        return baseModificationStamp > 0 && currentModificationStamp != baseModificationStamp;
    }

    /**
     * Normalizes a model/user supplied project-relative path and rejects paths
     * that are absolute, contain parent traversal, or otherwise cannot be bound
     * safely beneath the project root.
     */
    public static String normalizeRelativePath(String path) {
        if (path == null) {
            return "";
        }
        String normalized = path.trim().replace('\\', '/');
        if (normalized.isEmpty()
                || normalized.startsWith("/")
                || normalized.matches("^[A-Za-z]:.*")
                || normalized.indexOf('\0') >= 0) {
            return "";
        }
        while (normalized.startsWith("./")) {
            normalized = normalized.substring(2);
        }
        String[] segments = normalized.split("/");
        for (String segment : segments) {
            if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment)) {
                return "";
            }
        }
        return String.join("/", segments);
    }

    /** Preserves the original file's dominant line separator. */
    public static String preserveLineSeparators(String current, String proposed) {
        String normalized = normalize(proposed);
        if (current != null && current.contains("\r\n")) {
            return normalized.replace("\n", "\r\n");
        }
        if (current != null && current.indexOf('\r') >= 0 && current.indexOf('\n') < 0) {
            return normalized.replace("\n", "\r");
        }
        return normalized;
    }

    /** Line endings are normalised before comparing or writing. */
    public static String normalize(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\r\n", "\n").replace('\r', '\n');
    }

    /** Lower-case extension without the leading dot, or an empty string. */
    public static String extension(String path) {
        String normalized = normalizeRelativePath(path);
        if (normalized.isEmpty()) {
            return "";
        }
        int slash = normalized.lastIndexOf('/');
        int dot = normalized.lastIndexOf('.');
        return dot <= slash ? "" : normalized.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
