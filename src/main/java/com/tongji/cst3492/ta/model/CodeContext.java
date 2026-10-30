package com.tongji.cst3492.ta.model;

import java.util.List;

/**
 * IDE context for one question: an optional editor selection, optional read-only
 * snapshots of workspace code files, and an optional workspace outline.
 */
public record CodeContext(
        String fileName,
        String language,
        int startLine,
        int endLine,
        String methodSignature,
        String classSignature,
        String before,
        String selected,
        String after,
        List<CodeFileSnapshot> files,
        WorkspaceContext workspace) {

    public static final int CONTEXT_LINES = 20;

    public CodeContext {
        files = files == null ? List.of() : List.copyOf(files);
        workspace = workspace == null ? WorkspaceContext.empty() : workspace;
    }

    /** Selection-only context, kept for the editor action and existing callers. */
    public CodeContext(
            String fileName,
            String language,
            int startLine,
            int endLine,
            String methodSignature,
            String classSignature,
            String before,
            String selected,
            String after) {
        this(fileName, language, startLine, endLine, methodSignature, classSignature,
                before, selected, after, List.of(), WorkspaceContext.empty());
    }

    /** Selection plus attached files, without a workspace outline. */
    public CodeContext(
            String fileName,
            String language,
            int startLine,
            int endLine,
            String methodSignature,
            String classSignature,
            String before,
            String selected,
            String after,
            List<CodeFileSnapshot> files) {
        this(fileName, language, startLine, endLine, methodSignature, classSignature,
                before, selected, after, files, WorkspaceContext.empty());
    }

    public static CodeContext empty() {
        return new CodeContext("", "", 0, 0, "", "", "", "", "");
    }

    /** File-only context, used when the user attaches files without a selection. */
    public static CodeContext ofFiles(List<CodeFileSnapshot> files) {
        return new CodeContext("", "", 0, 0, "", "", "", "", "", files, WorkspaceContext.empty());
    }

    public CodeContext withFiles(List<CodeFileSnapshot> newFiles) {
        return new CodeContext(fileName, language, startLine, endLine, methodSignature,
                classSignature, before, selected, after, newFiles, workspace);
    }

    public CodeContext withWorkspace(WorkspaceContext newWorkspace) {
        return new CodeContext(fileName, language, startLine, endLine, methodSignature,
                classSignature, before, selected, after, files, newWorkspace);
    }

    public boolean hasSelection() {
        return selected != null && !selected.isBlank();
    }

    public boolean hasFiles() {
        return !files.isEmpty();
    }

    public boolean hasWorkspace() {
        return !workspace.isEmpty();
    }

    public boolean isEmpty() {
        return !hasSelection() && !hasFiles() && !hasWorkspace();
    }

    public String summary() {
        String range = startLine == endLine
                ? "L" + startLine
                : "L" + startLine + "-" + endLine;
        return fileName + " " + range + " (+" + CONTEXT_LINES + " lines)";
    }
}
