package com.tongji.cst3492.jentor.context;

import com.tongji.cst3492.jentor.model.WorkspaceIndex;
import com.tongji.cst3492.jentor.model.WorkspaceOutline;

import java.util.List;

/** Chooses a likely edit target from natural language and the workspace index. */
public final class EditTargetResolver {
    private static final int MAX_SIGNAL_CHARS = 12_000;

    private EditTargetResolver() {
    }

    public static String pick(
            WorkspaceIndex index,
            String activeEditorPath,
            String question,
            String assistantAdvice) {
        String active = EditGuard.normalizeRelativePath(activeEditorPath);
        String signal = ((question == null ? "" : question) + "\n"
                + (assistantAdvice == null ? "" : assistantAdvice)).trim();
        if (signal.length() > MAX_SIGNAL_CHARS) {
            signal = signal.substring(0, MAX_SIGNAL_CHARS);
        }
        if (index != null && !signal.isBlank()) {
            WorkspaceOutlineSelector.Ranking ranking =
                    WorkspaceOutlineSelector.rank(index, signal, 4);
            List<WorkspaceOutline> files = ranking.files();
            if (ranking.matched() && !files.isEmpty()) {
                return files.get(0).path();
            }
        }
        return active.isBlank() ? null : active;
    }
}
