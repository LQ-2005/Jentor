package com.tongji.cst3492.ta.context;

import java.util.List;

/**
 * Decides which changed paths matter for the workspace index, and whether a batch
 * of changes is large enough to look like a VCS operation. Pure logic, so the
 * staleness and bulk-notification rules are unit tested without an IDE.
 */
public final class WorkspaceChangeClassifier {
    /** More changed code files than this in one batch looks like a VCS operation. */
    public static final int BULK_CHANGE_THRESHOLD = 30;

    private WorkspaceChangeClassifier() {
    }

    /** Only files the outline extractor understands count as relevant. */
    public static boolean isRelevant(String path) {
        if (path == null || path.isBlank()) {
            return false;
        }
        String extension = CodeFilePolicy.extension(path);
        return extension.equals("java") || extension.equals("kt") || extension.equals("groovy");
    }

    public static int countRelevant(List<String> paths) {
        if (paths == null || paths.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (String path : paths) {
            if (isRelevant(path)) {
                count++;
            }
        }
        return count;
    }

    public static boolean isBulk(int relevantCount) {
        return relevantCount >= BULK_CHANGE_THRESHOLD;
    }
}
