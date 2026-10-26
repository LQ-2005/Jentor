package com.tongji.cst3492.ta.action;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.tongji.cst3492.ta.model.CodeContext;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SelectionContextFactory {
    private static final int MAX_SIGNATURE_SCAN_LINES = 120;
    private static final Pattern METHOD = Pattern.compile(
            "^\\s*(?:(?:public|protected|private|static|final|abstract|synchronized|native|default|strictfp)\\s+)*"
                    + "(?:<[^>]+>\\s+)?[\\w$<>\\[\\],.?]+\\s+([\\w$]+)\\s*\\([^;]*\\)"
                    + "\\s*(?:throws\\s+[\\w$.,\\s]+)?\\{?\\s*$");
    private static final Pattern CONSTRUCTOR = Pattern.compile(
            "^\\s*(?:(?:public|protected|private)\\s+)?([A-Z][\\w$]*)\\s*\\([^;]*\\)"
                    + "\\s*(?:throws\\s+[\\w$.,\\s]+)?\\{?\\s*$");
    private static final Pattern CLASS = Pattern.compile(
            "^\\s*(?:(?:public|protected|private|static|final|abstract|sealed|non-sealed)\\s+)*"
                    + "(?:class|interface|enum|record)\\s+([\\w$]+).*");
    private static final Set<String> NOT_METHODS = Set.of(
            "if", "for", "while", "switch", "catch", "try", "synchronized",
            "return", "new", "else", "do", "assert", "throw");

    private SelectionContextFactory() {
    }

    public static CodeContext create(Editor editor, VirtualFile file) {
        Document document = editor.getDocument();
        int selectionStart = Math.max(0, editor.getSelectionModel().getSelectionStart());
        int selectionEnd = Math.min(document.getTextLength(),
                editor.getSelectionModel().getSelectionEnd());
        if (selectionEnd < selectionStart) {
            int swap = selectionStart;
            selectionStart = selectionEnd;
            selectionEnd = swap;
        }

        int startLine = document.getLineNumber(selectionStart);
        int endLine = document.getLineNumber(selectionEnd);
        int beforeStartLine = Math.max(0, startLine - CodeContext.CONTEXT_LINES);
        int afterEndLine = Math.min(
                document.getLineCount() - 1,
                endLine + CodeContext.CONTEXT_LINES);

        String selected = document.getText(new TextRange(selectionStart, selectionEnd));
        String before = document.getText(new TextRange(
                document.getLineStartOffset(beforeStartLine),
                selectionStart));
        String after = document.getText(new TextRange(
                selectionEnd,
                document.getLineEndOffset(afterEndLine)));
        String method = findSignature(document, startLine, METHOD, CONSTRUCTOR, NOT_METHODS);
        String declaringClass = findClassSignature(document, startLine);
        String language = file == null ? "" : file.getFileType().getName();
        String fileName = file == null ? "current editor" : file.getName();

        return new CodeContext(
                fileName,
                language,
                startLine + 1,
                endLine + 1,
                method,
                declaringClass,
                before,
                selected,
                after);
    }

    private static String findSignature(
            Document document,
            int startLine,
            Pattern methodPattern,
            Pattern constructorPattern,
            Set<String> excludedNames) {
        int firstLine = Math.max(0, startLine - MAX_SIGNATURE_SCAN_LINES);
        for (int line = startLine; line >= firstLine; line--) {
            String text = lineText(document, line);
            String trimmed = text.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("//")
                    || trimmed.startsWith("*") || trimmed.startsWith("/*")
                    || trimmed.startsWith("@")) {
                continue;
            }
            Matcher methodMatcher = methodPattern.matcher(text);
            if (methodMatcher.matches()
                    && !excludedNames.contains(methodMatcher.group(1))) {
                return cleanSignature(text);
            }
            Matcher constructorMatcher = constructorPattern.matcher(text);
            if (constructorMatcher.matches()) {
                return cleanSignature(text);
            }
        }
        return "";
    }

    private static String findClassSignature(Document document, int startLine) {
        int firstLine = Math.max(0, startLine - MAX_SIGNATURE_SCAN_LINES);
        for (int line = startLine; line >= firstLine; line--) {
            String text = lineText(document, line);
            if (CLASS.matcher(text).matches()) {
                return cleanSignature(text);
            }
        }
        return "";
    }

    private static String lineText(Document document, int line) {
        return document.getText(new TextRange(
                document.getLineStartOffset(line),
                document.getLineEndOffset(line)));
    }

    private static String cleanSignature(String text) {
        return text.trim().replaceAll("\\s*\\{\\s*$", "").trim();
    }
}
