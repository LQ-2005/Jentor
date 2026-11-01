package com.tongji.cst3492.jentor.model;

/**
 * A read-only snapshot of one workspace source file, captured when the user
 * attached it.
 *
 * <p>The plugin never re-reads the file afterwards: the snapshot keeps
 * answering later questions even while the user keeps editing, and the answer
 * therefore carries a note about possible drift between this snapshot and the
 * current file (see {@code Answer#contextNote()}).</p>
 */
public record CodeFileSnapshot(
        String path,
        String fileName,
        String language,
        int startLine,
        int endLine,
        int totalLines,
        String content,
        boolean truncated) {

    public CodeFileSnapshot {
        path = path == null ? "" : path;
        fileName = fileName == null ? "" : fileName;
        language = language == null ? "" : language;
        content = content == null ? "" : content;
    }

    public boolean isEmpty() {
        return content.isBlank();
    }

    /** {@code L12} for one line, {@code L12-L34} for a range. */
    public String locator() {
        return startLine == endLine ? "L" + startLine : "L" + startLine + "-L" + endLine;
    }

    /** Prompt and dialog header, for example {@code App.java (L1-L120 of 340)}. */
    public String describe() {
        boolean complete = startLine == 1 && endLine == totalLines && !truncated;
        String range = complete ? locator() : locator() + " of " + totalLines;
        return path + " (" + range + ")";
    }

    /** Short label for the attachment bar. */
    public String summary() {
        return fileName + " " + locator() + (truncated ? " (truncated)" : "");
    }
}
