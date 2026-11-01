package com.tongji.cst3492.jentor.ui;

/**
 * Converts rendered Markdown into plain text. Used by the copy affordances so a
 * copied answer reads like the rendered message instead of showing markup.
 */
final class MarkdownText {
    private MarkdownText() {
    }

    static String toPlainText(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return "";
        }
        StringBuilder result = new StringBuilder();
        boolean inFence = false;
        for (String raw : markdown.replace("\r\n", "\n").split("\n", -1)) {
            String trimmed = raw.strip();
            if (trimmed.startsWith("```")) {
                inFence = !inFence;
                continue;
            }
            if (inFence) {
                result.append(raw).append('\n');
                continue;
            }
            String line = raw.replaceFirst("^\\s{0,3}#{1,6}\\s*", "");
            line = line.replaceFirst("^\\s*>\\s?", "");
            line = line.replace("**", "").replace("__", "");
            line = line.replaceAll("(?<!\\*)\\*(?!\\*)", "");
            line = line.replace("`", "");
            line = line.replaceAll("\\[([^\\]]+)]\\(([^)]+)\\)", "$1 ($2)");
            result.append(line).append('\n');
        }
        String text = result.toString().replaceAll("\n{3,}", "\n\n");
        return text.strip();
    }
}
