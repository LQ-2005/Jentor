package com.tongji.cst3492.jentor.context;

/**
 * Renders a bounded excerpt of one file for the prompt. Pure logic so the
 * truncation rules are unit tested without an IDE.
 */
public final class WorkspaceExcerpt {
    /** Never more than this many lines of one file. */
    public static final int MAX_LINES = 120;

    private WorkspaceExcerpt() {
    }

    /**
     * @return the header plus the first lines that fit, or an empty string when
     *         there is nothing to show.
     */
    public static String render(String path, String text, int maxChars) {
        if (path == null || path.isBlank() || text == null || text.isBlank() || maxChars <= 0) {
            return "";
        }
        String[] lines = text.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        StringBuilder result = new StringBuilder("--- FILE: ").append(path).append(" ---\n");
        int budget = maxChars - result.length();
        int taken = 0;
        for (String line : lines) {
            if (taken >= MAX_LINES || line.length() + 1 > budget) {
                break;
            }
            result.append(line).append('\n');
            budget -= line.length() + 1;
            taken++;
        }
        if (taken < lines.length) {
            result.append("... (truncated)\n");
        }
        return result.toString();
    }
}
