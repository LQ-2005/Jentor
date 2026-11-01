package com.tongji.cst3492.jentor.context;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.tongji.cst3492.jentor.model.WorkspaceIndex;
import com.tongji.cst3492.jentor.model.WorkspaceOutline;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds and maintains the workspace index. Everything here must run inside a
 * read action; the service owns the threading.
 */
public final class WorkspaceIndexBuilder {
    /** Safety valve for very large repositories. */
    public static final int MAX_FILES = 2_000;

    private WorkspaceIndexBuilder() {
    }

    public static WorkspaceIndex build(Project project) {
        ProjectFileIndex fileIndex = ProjectFileIndex.getInstance(project);
        List<WorkspaceOutline> outlines = new ArrayList<>();
        fileIndex.iterateContent(file -> {
            if (outlines.size() >= MAX_FILES) {
                return false;
            }
            if (file.isDirectory() || fileIndex.isInGeneratedSources(file)) {
                return true;
            }
            WorkspaceOutline outline = outlineOf(project, file);
            if (outline != null) {
                outlines.add(outline);
            }
            return true;
        });
        return new WorkspaceIndex(basePath(project), System.currentTimeMillis(), outlines);
    }

    /** Parses one file; {@code null} when it is not a file we index. */
    public static WorkspaceOutline outlineOf(Project project, VirtualFile file) {
        if (file == null || !file.isValid() || file.isDirectory()) {
            return null;
        }
        String path = WorkspaceFileReader.projectRelativePath(project, file);
        if (!WorkspaceChangeClassifier.isRelevant(path)) {
            return null;
        }
        String text = WorkspaceFileReader.textOf(file);
        if (text == null || text.isBlank()) {
            return null;
        }
        return OutlineExtractor.extract(path, text, file.getModificationStamp());
    }

    /**
     * Re-parses only the files that changed; every other entry is kept. This is
     * what makes the background monitor cheap enough to run on every save.
     */
    public static WorkspaceIndex refresh(
            Project project,
            WorkspaceIndex current,
            List<String> changedPaths) {
        Map<String, WorkspaceOutline> byPath = new LinkedHashMap<>();
        for (WorkspaceOutline outline : current.files()) {
            byPath.put(outline.path(), outline);
        }
        for (String changed : changedPaths) {
            String path = relativize(project, changed);
            if (path == null || path.isBlank() || !WorkspaceChangeClassifier.isRelevant(path)) {
                continue;
            }
            VirtualFile file = find(project, path);
            WorkspaceOutline outline = outlineOf(project, file);
            if (outline == null) {
                byPath.remove(path);
            } else {
                byPath.put(path, outline);
            }
        }
        return new WorkspaceIndex(basePath(project), System.currentTimeMillis(),
                new ArrayList<>(byPath.values()));
    }

    /** Counts indexable files without reading any of them. */
    public static int countOutlinable(Project project) {
        ProjectFileIndex fileIndex = ProjectFileIndex.getInstance(project);
        int[] count = {0};
        fileIndex.iterateContent(file -> {
            if (count[0] >= MAX_FILES) {
                return false;
            }
            if (!file.isDirectory() && !fileIndex.isInGeneratedSources(file)
                    && WorkspaceChangeClassifier.isRelevant(
                            WorkspaceFileReader.projectRelativePath(project, file))) {
                count[0]++;
            }
            return true;
        });
        return count[0];
    }

    /**
     * Checks a cached index against project path, file set and VFS modification
     * stamps. A count-only check misses the common case where a file changed while
     * the IDE was closed but no file was added or removed.
     */
    public static boolean isFresh(Project project, WorkspaceIndex index) {
        if (project == null || index == null
                || !basePath(project).equals(index.projectPath())) {
            return false;
        }
        Map<String, WorkspaceOutline> byPath = new LinkedHashMap<>();
        for (WorkspaceOutline outline : index.files()) {
            byPath.put(outline.path(), outline);
        }
        ProjectFileIndex fileIndex = ProjectFileIndex.getInstance(project);
        boolean[] fresh = {true};
        int[] relevant = {0};
        fileIndex.iterateContent(file -> {
            if (!fresh[0]) {
                return false;
            }
            if (file.isDirectory() || fileIndex.isInGeneratedSources(file)) {
                return true;
            }
            String path = WorkspaceFileReader.projectRelativePath(project, file);
            if (!WorkspaceChangeClassifier.isRelevant(path)) {
                return true;
            }
            if (relevant[0] >= MAX_FILES) {
                return false;
            }
            relevant[0]++;
            if (file.getLength() == 0) {
                return true;
            }
            WorkspaceOutline stored = byPath.remove(path);
            if (stored == null || stored.modifiedAt() != file.getModificationStamp()) {
                fresh[0] = false;
                return false;
            }
            return true;
        });
        return fresh[0] && byPath.isEmpty();
    }

    private static VirtualFile find(Project project, String path) {
        String base = project.getBasePath();
        if (base == null) {
            return null;
        }
        return LocalFileSystem.getInstance().findFileByPath(base + "/" + path);
    }

    /** Converts an absolute VFS path into a project-relative one. */
    private static String relativize(Project project, String absolute) {
        String base = project.getBasePath();
        if (base == null || absolute == null) {
            return null;
        }
        String normalizedBase = base.replace('\\', '/');
        String normalized = absolute.replace('\\', '/');
        if (normalized.equals(normalizedBase)) {
            return "";
        }
        if (!normalized.startsWith(normalizedBase + "/")) {
            return null;
        }
        return normalized.substring(normalizedBase.length() + 1);
    }

    private static String basePath(Project project) {
        String base = project.getBasePath();
        return base == null ? "" : base;
    }
}
