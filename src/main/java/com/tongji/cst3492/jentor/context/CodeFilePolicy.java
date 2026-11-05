package com.tongji.cst3492.jentor.context;

import com.tongji.cst3492.jentor.model.CodeFileSnapshot;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

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
    public static final int MAX_TOTAL_CHARS = 10_000;

    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(
            "java", "kt", "groovy", "gradle", "kts");
    private static final Set<String> READABLE_CONTEXT_EXTENSIONS = Set.of(
            "java", "kt", "groovy", "gradle", "kts",
            "properties", "yml", "yaml", "xml", "json");

    private static final List<String> SENSITIVE_NAME_PARTS = List.of(
            ".env", "secret", "credential", ".pem", ".key", ".p12", ".jks", "id_rsa");
    private static final Pattern SECRET_ASSIGNMENT = Pattern.compile(
            "(?i)(api[_-]?key|secret|token|password|passwd|pwd|private[_-]?key)"
                    + "\\s*[:=]\\s*[\"'][^\"'\\s]{8,}[\"']");
    private static final Pattern TOKEN_VALUE = Pattern.compile(
            "(?:sk-[A-Za-z0-9_-]{20,}|ghp_[A-Za-z0-9]{20,}|"
                    + "github_pat_[A-Za-z0-9_]{20,}|AKIA[0-9A-Z]{16})");

    private CodeFilePolicy() {
    }

    /** Whether a project-relative path may be attached at all. */
    public static boolean isSupported(String path) {
        return isAllowed(path, SUPPORTED_EXTENSIONS);
    }

    /**
     * Configuration files are useful read-only context but remain forbidden as
     * AI write targets through {@link #isSupported(String)}.
     */
    public static boolean isReadableContext(String path) {
        return isAllowed(path, READABLE_CONTEXT_EXTENSIONS);
    }

    private static boolean isAllowed(String path, Set<String> extensions) {
        if (path == null || path.isBlank()) {
            return false;
        }
        String lower = path.toLowerCase(Locale.ROOT);
        for (String sensitive : SENSITIVE_NAME_PARTS) {
            if (lower.contains(sensitive)) {
                return false;
            }
        }
        return extensions.contains(extension(path));
    }

    /**
     * Conservative secret check for text admitted to the model. IntelliJ stores
     * user credentials in PasswordSafe, but source files can still contain
     * accidentally committed API keys or passwords.
     */
    public static boolean containsLikelySecret(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        if (text.contains("-----BEGIN PRIVATE KEY-----")
                || text.contains("-----BEGIN RSA PRIVATE KEY-----")
                || text.contains("-----BEGIN OPENSSH PRIVATE KEY-----")) {
            return true;
        }
        if (SECRET_ASSIGNMENT.matcher(text).find()) {
            return true;
        }
        return TOKEN_VALUE.matcher(text).find();
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
