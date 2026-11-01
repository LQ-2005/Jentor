package com.tongji.cst3492.jentor.context;

import com.tongji.cst3492.jentor.model.WorkspaceOutline;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts a file outline with plain regexes. No PSI, no Java plugin, no dumb-mode
 * handling: this is the "simple parsing" that runs when the plugin loads.
 */
public final class OutlineExtractor {
    /** Members kept per file; enough to recognise a class, small enough to stay cheap. */
    public static final int MAX_MEMBERS = 40;

    private static final Pattern PACKAGE =
            Pattern.compile("^\\s*package\\s+([\\w.]+)\\s*;");
    private static final Pattern TYPE = Pattern.compile(
            "^\\s*(?:(?:public|protected|private|static|final|abstract|sealed|non-sealed)\\s+)*"
                    + "(class|interface|enum|record)\\s+([\\w$]+)(.*)$");
    private static final Pattern METHOD = Pattern.compile(
            "^\\s*(?:(?:public|protected|private|static|final|abstract|synchronized|native|default|strictfp)\\s+)*"
                    + "(?:<[^>]+>\\s+)?[\\w$<>\\[\\].,? ]+\\s+([\\w$]+)\\s*\\([^;]*\\)\\s*"
                    + "(?:throws\\s+[\\w$.,\\s]+)?[\\s{;].*$");
    private static final Set<String> NOT_METHOD_HEADS = Set.of(
            "if", "for", "while", "switch", "catch", "try", "return", "throw",
            "new", "else", "do", "assert", "case", "break", "continue", "yield");
    private static final Pattern ANNOTATION = Pattern.compile("^\\s*@");
    private static final Set<String> NOT_METHODS = Set.of(
            "if", "for", "while", "switch", "catch", "try", "synchronized",
            "return", "new", "else", "do", "assert", "throw");

    private OutlineExtractor() {
    }

    public static WorkspaceOutline extract(String path, String text) {
        return extract(path, text, 0L);
    }

    public static WorkspaceOutline extract(String path, String text, long modifiedAt) {
        String source = text == null ? "" : text.replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = source.split("\n", -1);

        String packageName = "";
        String typeName = "";
        List<String> members = new ArrayList<>();

        for (String raw : lines) {
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("//") || line.startsWith("*")
                    || line.startsWith("/*") || ANNOTATION.matcher(line).find()) {
                continue;
            }
            if (packageName.isEmpty()) {
                Matcher packageMatcher = PACKAGE.matcher(line);
                if (packageMatcher.find()) {
                    packageName = packageMatcher.group(1);
                    continue;
                }
            }
            if (typeName.isEmpty()) {
                Matcher typeMatcher = TYPE.matcher(line);
                if (typeMatcher.matches()) {
                    String tail = typeMatcher.group(3).replaceAll("\\{\\s*$", "").strip();
                    typeName = (typeMatcher.group(1) + " " + typeMatcher.group(2) + " " + tail)
                            .replaceAll("\\s{2,}", " ").strip();
                    continue;
                }
            }
            if (members.size() >= MAX_MEMBERS) {
                continue;
            }
            String head = line.replaceFirst(
                    "^(?:(?:public|protected|private|static|final|abstract|synchronized|native"
                            + "|default|strictfp)\\s+)+", "");
            String headKeyword = head.split("\\s+", 2)[0];
            if (NOT_METHOD_HEADS.contains(headKeyword)) {
                continue;
            }
            Matcher methodMatcher = METHOD.matcher(line);
            if (methodMatcher.matches() && !NOT_METHODS.contains(methodMatcher.group(1))) {
                members.add(line.replaceFirst("\\s*[{;].*$", "").strip());
            }
        }

        List<String> unique = new ArrayList<>(new LinkedHashSet<>(members));
        return new WorkspaceOutline(path, packageName, typeName, lineCount(lines), unique,
                modifiedAt);
    }

    private static int lineCount(String[] lines) {
        int count = lines.length;
        if (count > 0 && lines[count - 1].isEmpty()) {
            count--;
        }
        return Math.max(count, 0);
    }
}
