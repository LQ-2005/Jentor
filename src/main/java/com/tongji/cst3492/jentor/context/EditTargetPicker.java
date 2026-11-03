package com.tongji.cst3492.jentor.context;

import java.util.List;

/**
 * Decides which file an edit request targets: an attached workspace file wins,
 * otherwise the file currently selected in the editor.
 */
public final class EditTargetPicker {
    private EditTargetPicker() {
    }

    public static String pick(List<String> attachedPaths, String selectedEditorPath) {
        if (attachedPaths != null) {
            for (String path : attachedPaths) {
                if (path != null && !path.isBlank()) {
                    return path;
                }
            }
        }
        if (selectedEditorPath != null && !selectedEditorPath.isBlank()) {
            return selectedEditorPath;
        }
        return null;
    }
}
