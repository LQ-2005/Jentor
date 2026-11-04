package com.tongji.cst3492.jentor.context;

import com.tongji.cst3492.jentor.model.WorkspaceIndex;
import com.tongji.cst3492.jentor.model.WorkspaceOutline;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Recovers one unambiguous Java file named by a model's relocation refusal. */
public final class EditRefusalTarget {
    private static final Pattern JAVA_FILE =
            Pattern.compile("([A-Za-z0-9_.-]+\\.java)\\b");

    private EditRefusalTarget() {
    }

    public static String resolve(
            String refusal, String currentTarget, WorkspaceIndex index) {
        if (refusal == null || refusal.isBlank() || index == null) {
            return null;
        }
        String current = EditGuard.normalizeRelativePath(currentTarget);
        Set<String> candidates = new LinkedHashSet<>();
        Matcher matcher = JAVA_FILE.matcher(refusal);
        while (matcher.find()) {
            String fileName = matcher.group(1);
            for (WorkspaceOutline outline : index.files()) {
                String path = EditGuard.normalizeRelativePath(outline.path());
                if (fileName.equalsIgnoreCase(fileName(path)) && !path.equals(current)) {
                    candidates.add(path);
                }
            }
        }
        return candidates.size() == 1 ? candidates.iterator().next() : null;
    }

    private static String fileName(String path) {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? path : path.substring(slash + 1);
    }
}
