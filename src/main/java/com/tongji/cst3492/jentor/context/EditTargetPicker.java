package com.tongji.cst3492.jentor.context;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Decides which file an edit request targets: an attached workspace file wins,
 * otherwise the file currently selected in the editor.
 */
public final class EditTargetPicker {
    private EditTargetPicker() {
    }

    public record Result(EditTarget target, String error) {
        public boolean hasTarget() {
            return target != null && !target.path().isBlank();
        }

        static Result target(EditTarget target) {
            return new Result(target, "");
        }

        static Result error(String message) {
            return new Result(null, message);
        }
    }

    public static Result pick(
            List<String> attachedPaths,
            String selectedEditorPath,
            String editorFileName,
            String selectionFileName,
            int startLine,
            int endLine) {
        String editorPath = EditGuard.normalizeRelativePath(selectedEditorPath);
        Set<String> attached = new LinkedHashSet<>();
        if (attachedPaths != null) {
            for (String path : attachedPaths) {
                String normalized = EditGuard.normalizeRelativePath(path);
                if (!normalized.isBlank()) {
                    attached.add(normalized);
                }
            }
        }

        if (attached.size() > 1) {
            return Result.error("multiple attached target files");
        }
        if (attached.size() == 1) {
            String path = attached.iterator().next();
            boolean rangeMatches = startLine > 0 && path.equals(editorPath);
            return Result.target(rangeMatches
                    ? new EditTarget(path, startLine, endLine)
                    : new EditTarget(path, 0, 0));
        }

        if (editorPath.isBlank()) {
            return Result.error("no target file");
        }
        boolean rangeMatches = startLine > 0
                && clean(editorFileName).equals(clean(selectionFileName));
        return Result.target(rangeMatches
                ? new EditTarget(editorPath, startLine, endLine)
                : new EditTarget(editorPath, 0, 0));
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

}
