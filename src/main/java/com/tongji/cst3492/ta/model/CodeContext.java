package com.tongji.cst3492.ta.model;

public record CodeContext(
        String fileName,
        String language,
        int startLine,
        int endLine,
        String methodSignature,
        String classSignature,
        String before,
        String selected,
        String after) {
    public static final int CONTEXT_LINES = 20;

    public static CodeContext empty() {
        return new CodeContext("", "", 0, 0, "", "", "", "", "");
    }

    public boolean hasSelection() {
        return selected != null && !selected.isBlank();
    }

    public String summary() {
        String range = startLine == endLine
                ? "L" + startLine
                : "L" + startLine + "-" + endLine;
        return fileName + " " + range + " (+" + CONTEXT_LINES + " lines)";
    }
}
