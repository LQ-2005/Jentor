package com.tongji.cst3492.jentor.context;

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
        return CodeFilePolicy.isSupported(path);
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

    /** Line endings are normalised before comparing or writing. */
    public static String normalize(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\r\n", "\n").replace('\r', '\n');
    }
}
