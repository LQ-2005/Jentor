package com.tongji.cst3492.jentor.context;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Builds a bounded, method-aware excerpt for project files that are too large to
 * send in full. Unlike the old prefix truncation this keeps the declarations,
 * matching methods and tail of a file, and marks every omitted line range.
 */
public final class SmartCodeExcerpt {
    private static final Pattern IDENTIFIER =
            Pattern.compile("(?<![A-Za-z0-9_$])[A-Za-z_$][A-Za-z0-9_$]{2,}");
    private static final Pattern METHOD_HEAD = Pattern.compile(
            "^\\s*(?:(?:public|protected|private|static|final|abstract|synchronized|"
                    + "native|default|strictfp)\\s+)*"
                    + "(?:<[^>]+>\\s+)?[\\w$<>\\[\\],.? ]+\\s+([\\w$]+)\\s*"
                    + "\\([^;]*\\)\\s*(?:throws\\s+[\\w$.,\\s]+)?\\{?\\s*$");
    private static final Set<String> STOP_WORDS = Set.of(
            "the", "and", "for", "with", "this", "that", "what", "why", "how",
            "code", "file", "class", "method", "problem", "issue", "please",
            "检查", "分析", "修改", "代码", "文件", "问题", "方法", "这个", "一下",
            "什么", "为什么", "怎么", "可以", "帮我");

    private static final int HEADER_LINES = 80;
    private static final int TAIL_LINES = 48;
    private static final int WINDOW_BEFORE = 8;
    private static final int WINDOW_AFTER = 16;
    private static final int MAX_METHOD_SCAN_LINES = 320;

    private SmartCodeExcerpt() {
    }

    /**
     * @return the original text when it fits, otherwise selected whole-line ranges
     *         with omitted-range markers.
     */
    public static String render(String text, String question, int maxChars) {
        String normalized = text == null ? ""
                : text.replace("\r\n", "\n").replace('\r', '\n');
        if (normalized.isEmpty() || maxChars <= 0) {
            return "";
        }
        if (normalized.length() <= maxChars) {
            return normalized;
        }

        List<String> lines = new ArrayList<>(List.of(normalized.split("\n", -1)));
        if (!lines.isEmpty() && lines.get(lines.size() - 1).isEmpty()) {
            lines.remove(lines.size() - 1);
        }
        if (lines.isEmpty()) {
            return "";
        }

        List<Candidate> candidates = new ArrayList<>();
        candidates.add(new Candidate(0, Math.min(lines.size() - 1, HEADER_LINES - 1), 30));
        int tailStart = Math.max(0, lines.size() - TAIL_LINES);
        candidates.add(new Candidate(tailStart, lines.size() - 1, 20));

        Set<String> signals = signals(question);
        for (int line = 0; line < lines.size(); line++) {
            if (!matchesAny(lines.get(line), signals)) {
                continue;
            }
            Range method = enclosingMethod(lines, line);
            if (method != null) {
                candidates.add(new Candidate(method.start(), method.end(), 100));
            } else {
                candidates.add(new Candidate(
                        Math.max(0, line - WINDOW_BEFORE),
                        Math.min(lines.size() - 1, line + WINDOW_AFTER),
                        60));
            }
        }

        List<Candidate> merged = merge(candidates, lines.size());
        merged.sort(Comparator.comparingInt(Candidate::priority).reversed()
                .thenComparingInt(Candidate::start));

        List<Candidate> selected = new ArrayList<>();
        int used = 0;
        for (Candidate candidate : merged) {
            String block = renderBlock(lines, candidate.start(), candidate.end());
            if (selected.isEmpty() || used + block.length() <= maxChars) {
                selected.add(candidate);
                used += block.length();
                continue;
            }

            int remaining = maxChars - used;
            if (remaining < 160) {
                continue;
            }
            int availableLines = Math.max(1, (remaining - 48) / 32);
            Candidate shortened;
            if (candidate.end() == lines.size() - 1) {
                shortened = new Candidate(
                        Math.max(candidate.start(), candidate.end() - availableLines + 1),
                        candidate.end(),
                        candidate.priority());
            } else {
                shortened = new Candidate(
                        candidate.start(),
                        Math.min(candidate.end(), candidate.start() + availableLines - 1),
                        candidate.priority());
            }
            String shortenedBlock = renderBlock(lines, shortened.start(), shortened.end());
            if (shortenedBlock.length() <= remaining) {
                selected.add(shortened);
                used += shortenedBlock.length();
            }
        }
        if (selected.isEmpty()) {
            selected.add(new Candidate(0, Math.min(lines.size() - 1, HEADER_LINES - 1), 1));
        }
        selected.sort(Comparator.comparingInt(Candidate::start)
                .thenComparingInt(Candidate::end));

        StringBuilder result = new StringBuilder();
        int nextLine = 0;
        for (Candidate candidate : selected) {
            if (candidate.start() > nextLine) {
                result.append("[omitted lines ")
                        .append(nextLine + 1).append('-').append(candidate.start())
                        .append("]\n");
            }
            result.append(renderBlock(lines, candidate.start(), candidate.end()));
            nextLine = Math.max(nextLine, candidate.end() + 1);
        }
        if (nextLine < lines.size()) {
            result.append("[omitted lines ")
                    .append(nextLine + 1).append('-').append(lines.size())
                    .append("]\n");
        }
        String excerpt = result.toString().stripTrailing();
        if (excerpt.length() <= maxChars) {
            return excerpt;
        }
        int cut = Math.max(0, maxChars - 32);
        return excerpt.substring(0, cut) + "\n[excerpt character limit reached]\n";
    }

    private static Set<String> signals(String question) {
        Set<String> result = new LinkedHashSet<>();
        String source = question == null ? "" : question;
        Matcher matcher = IDENTIFIER.matcher(source);
        while (matcher.find()) {
            String token = matcher.group(0);
            String lower = token.toLowerCase(Locale.ROOT);
            if (!STOP_WORDS.contains(lower)) {
                result.add(lower);
            }
        }
        for (String part : source.split("\\s+|[,:;()]+")) {
            String lower = part.toLowerCase(Locale.ROOT).strip();
            if (lower.length() >= 3 && !STOP_WORDS.contains(lower)) {
                result.add(lower);
            }
        }
        return result;
    }

    private static boolean matchesAny(String line, Set<String> signals) {
        if (signals.isEmpty()) {
            return false;
        }
        String lower = line.toLowerCase(Locale.ROOT);
        for (String signal : signals) {
            if (lower.contains(signal)) {
                return true;
            }
        }
        return false;
    }

    private static Range enclosingMethod(List<String> lines, int targetLine) {
        int first = Math.max(0, targetLine - MAX_METHOD_SCAN_LINES);
        for (int line = targetLine; line >= first; line--) {
            Matcher matcher = METHOD_HEAD.matcher(lines.get(line));
            if (!matcher.matches() || excludedMethod(matcher.group(1))) {
                continue;
            }
            int end = matchingBraceEnd(lines, line);
            return new Range(line, end >= line ? end : Math.min(
                    lines.size() - 1, targetLine + WINDOW_AFTER));
        }
        return null;
    }

    private static int matchingBraceEnd(List<String> lines, int methodStart) {
        int balance = 0;
        boolean opened = false;
        for (int line = methodStart; line < lines.size(); line++) {
            String value = stripLineCommentAndStrings(lines.get(line));
            for (int index = 0; index < value.length(); index++) {
                char character = value.charAt(index);
                if (character == '{') {
                    balance++;
                    opened = true;
                } else if (character == '}') {
                    balance--;
                }
            }
            if (opened && balance <= 0) {
                return line;
            }
            if (!opened && line - methodStart > 3) {
                return line;
            }
        }
        return lines.size() - 1;
    }

    private static boolean excludedMethod(String name) {
        return Set.of("if", "for", "while", "switch", "catch", "try", "return",
                "new", "else", "do", "assert", "throw").contains(name);
    }

    private static String stripLineCommentAndStrings(String line) {
        String withoutComment = line.replaceFirst("//.*$", "");
        return withoutComment.replaceAll("\"(?:\\\\.|[^\"])*\"", "\"\"")
                .replaceAll("'(?:\\\\.|[^'])*'", "''");
    }

    private static List<Candidate> merge(List<Candidate> candidates, int lineCount) {
        List<Candidate> sorted = new ArrayList<>();
        for (Candidate candidate : candidates) {
            int start = Math.max(0, Math.min(candidate.start(), lineCount - 1));
            int end = Math.max(start, Math.min(candidate.end(), lineCount - 1));
            sorted.add(new Candidate(start, end, candidate.priority()));
        }
        sorted.sort(Comparator.comparingInt(Candidate::start)
                .thenComparingInt(Candidate::end));
        List<Candidate> merged = new ArrayList<>();
        for (Candidate next : sorted) {
            if (merged.isEmpty()) {
                merged.add(next);
                continue;
            }
            Candidate previous = merged.get(merged.size() - 1);
            if (next.start() <= previous.end() + 3) {
                merged.set(merged.size() - 1, new Candidate(
                        previous.start(),
                        Math.max(previous.end(), next.end()),
                        Math.max(previous.priority(), next.priority())));
            } else {
                merged.add(next);
            }
        }
        return merged;
    }

    private static String renderBlock(List<String> lines, int start, int end) {
        StringBuilder result = new StringBuilder("[lines ")
                .append(start + 1).append('-').append(end + 1).append("]\n");
        for (int line = start; line <= end; line++) {
            result.append(lines.get(line)).append('\n');
        }
        return result.toString();
    }

    private record Range(int start, int end) {
    }

    private record Candidate(int start, int end, int priority) {
    }
}
