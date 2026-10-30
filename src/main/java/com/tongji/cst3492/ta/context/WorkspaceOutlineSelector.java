package com.tongji.cst3492.ta.context;

import com.tongji.cst3492.ta.index.Tokens;
import com.tongji.cst3492.ta.model.WorkspaceIndex;
import com.tongji.cst3492.ta.model.WorkspaceOutline;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Picks the parts of the workspace index that are relevant to a question and
 * renders them for the prompt. Pure logic, so the budget and ranking are unit
 * tested without an IDE.
 */
public final class WorkspaceOutlineSelector {
    public static final int MAX_FILES = 6;
    public static final int MAX_CHARS = 4_000;

    private WorkspaceOutlineSelector() {
    }

    /** The files chosen for a question, and whether a name actually matched. */
    public record Ranking(List<WorkspaceOutline> files, boolean matched) {
        public Ranking {
            files = files == null ? List.of() : List.copyOf(files);
        }
    }

    public static String select(WorkspaceIndex index, String question) {
        Ranking ranking = rank(index, question, MAX_FILES);
        StringBuilder result = new StringBuilder("PROJECT OUTLINE (")
                .append(index == null ? 0 : index.size())
                .append(" source files, structure only - no method bodies):\n");
        if (ranking.files().isEmpty()) {
            result.append("(the workspace index has no source files yet)\n");
            return result.toString();
        }
        if (!ranking.matched()) {
            result.append("(no file or member name matched the question; showing the ")
                    .append("most recently modified files)\n");
        }
        int budget = MAX_CHARS - result.length();
        for (WorkspaceOutline file : ranking.files()) {
            String block = render(file);
            if (block.length() > budget) {
                break;
            }
            budget -= block.length();
            result.append(block);
        }
        return result.toString();
    }

    public static Ranking rank(WorkspaceIndex index, String question, int limit) {
        if (index == null || index.size() == 0 || limit <= 0) {
            return new Ranking(List.of(), false);
        }
        List<String> tokens = Tokens.tokenize(question == null ? "" : question);
        List<Scored> scored = new ArrayList<>();
        for (WorkspaceOutline file : index.files()) {
            int score = score(file, tokens);
            if (score > 0) {
                scored.add(new Scored(file, score));
            }
        }
        if (!scored.isEmpty()) {
            scored.sort(Comparator.comparingInt(Scored::score).reversed());
            List<WorkspaceOutline> files = new ArrayList<>();
            for (Scored entry : scored) {
                if (files.size() >= limit) {
                    break;
                }
                files.add(entry.file());
            }
            return new Ranking(files, true);
        }
        // Chinese questions rarely contain Java identifiers. Falling back to the
        // most recently modified files is far more useful than injecting nothing.
        List<WorkspaceOutline> recent = new ArrayList<>(index.files());
        recent.sort(Comparator.comparingLong(WorkspaceOutline::modifiedAt).reversed());
        return new Ranking(recent.subList(0, Math.min(limit, recent.size())), false);
    }

    private static int score(WorkspaceOutline file, List<String> tokens) {
        String path = file.path().toLowerCase(Locale.ROOT);
        String type = file.typeName().toLowerCase(Locale.ROOT);
        String members = String.join(" ", file.members()).toLowerCase(Locale.ROOT);
        int score = 0;
        for (String token : tokens) {
            if (token.length() < 2) {
                continue;
            }
            if (type.contains(token)) {
                score += 4;
            } else if (path.contains(token)) {
                score += 3;
            } else if (members.contains(token)) {
                score += 1;
            }
        }
        return score;
    }

    private static String render(WorkspaceOutline file) {
        StringBuilder block = new StringBuilder("--- ").append(file.path());
        if (!file.typeName().isBlank()) {
            block.append(" -- ").append(file.typeName());
        }
        block.append(" ---\n");
        for (String member : file.members()) {
            block.append("    ").append(member).append('\n');
        }
        return block.toString();
    }

    private record Scored(WorkspaceOutline file, int score) {
    }
}
