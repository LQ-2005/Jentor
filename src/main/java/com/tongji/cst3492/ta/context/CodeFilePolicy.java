package com.tongji.cst3492.ta.context;

import com.tongji.cst3492.ta.model.CodeFileSnapshot;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Decides which workspace files may become context and how much of them survives
 * truncation. This is deliberately free of IntelliJ types so the rules can be
 * unit tested without an IDE.
 *
 * <p>Only text source files are accepted. Compiled {@code .class} files are
 * rejected on purpose: their bytes are not readable text and the plugin does not
 * decompile them.</p>
 */
public final class CodeFilePolicy {
    /** Files attached per conversation, before the user clears them. */
    public static final int MAX_FILES = 5;
    public static final int MAX_LINES_PER_FILE = 400;
    public static final int MAX_CHARS_PER_FILE = 4_000;
    /**
     * Budget for the whole workspace-file section of the prompt. Two full files
     * fit; further files are dropped with an explicit marker rather than silently
     * cut in half.
     */
    public static final int MAX_TOTAL_CHARS = 12_000;

    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(
            "java", "kt", "groovy", "xml", "properties", "gradle", "kts", "sql", "json");

    private static final List<String> SENSITIVE_NAME_PARTS = List.of(
            ".env", "secret", "credential", ".pem", ".key", ".p12", ".jks", "id_rsa");

    private CodeFilePolicy() {
    }

    /** Whether a project-relative path may be attached at all. */
    public static boolean isSupported(String path) {
        if (path == null || path.isBlank()) {
            return false;
        }
        String lower = path.toLowerCase(Locale.ROOT);
        for (String sensitive : SENSITIVE_NAME_PARTS) {
            if (lower.contains(sensitive)) {
                return false;
            }
        }
        return SUPPORTED_EXTENSIONS.contains(extension(path));
    }

    /** Lower-case extension without the dot, or an empty string. */
    public static String extension(String path) {
        if (path == null) {
            return "";
        }
        int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        int dot = path.lastIndexOf('.');
        if (dot <= slash || dot == path.length() - 1) {
            return "";
        }
        return path.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    public static String fileName(String path) {
        if (path == null || path.isBlank()) {
            return "";
        }
        int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return path.substring(slash + 1);
    }

    public static CodeFileSnapshot snapshot(String path, String language, String text) {
        return snapshot(path, language, text, MAX_LINES_PER_FILE, MAX_CHARS_PER_FILE);
    }

    /** Captures the file text, keeping whole lines up to the limits. */
    public static CodeFileSnapshot snapshot(
            String path,
            String language,
            String text,
            int maxLines,
            int maxChars) {
        String source = text == null ? "" : text
                .replace("\r\n", "\n")
                .replace('\r', '\n');
        List<String> lines = new java.util.ArrayList<>(List.of(source.split("\n", -1)));
        if (!lines.isEmpty() && lines.get(lines.size() - 1).isEmpty()) {
            lines.remove(lines.size() - 1);
        }
        if (lines.isEmpty()) {
            lines.add("");
        }
        int totalLines = lines.size();

        StringBuilder kept = new StringBuilder();
        int taken = 0;
        boolean truncated = false;
        for (String line : lines) {
            if (taken >= maxLines) {
                truncated = true;
                break;
            }
            int needed = kept.length() == 0 ? line.length() : kept.length() + 1 + line.length();
            if (needed > maxChars) {
                if (taken == 0) {
                    kept.append(line, 0, Math.min(line.length(), maxChars));
                    taken = 1;
                }
                truncated = true;
                break;
            }
            if (kept.length() > 0) {
                kept.append('\n');
            }
            kept.append(line);
            taken++;
        }

        return new CodeFileSnapshot(
                path,
                fileName(path),
                language,
                taken == 0 ? 0 : 1,
                taken,
                totalLines,
                kept.toString(),
                truncated);
    }
}
