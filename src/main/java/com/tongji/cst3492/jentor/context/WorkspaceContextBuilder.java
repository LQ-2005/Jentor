package com.tongji.cst3492.jentor.context;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import com.tongji.cst3492.jentor.model.CodeContext;
import com.tongji.cst3492.jentor.model.CodeFileSnapshot;
import com.tongji.cst3492.jentor.model.WorkspaceIndex;
import com.tongji.cst3492.jentor.model.WorkspaceOutline;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Builds the bounded source snapshot for one request. The active file and files
 * named by the user are read before broad workspace candidates, and large files
 * use {@link SmartCodeExcerpt} instead of prefix truncation.
 */
public final class WorkspaceContextBuilder {
    public static final int MAX_CONTEXT_FILES = 6;
    public static final int MAX_CHARS_PER_FILE = 48_000;
    public static final int MAX_TOTAL_CHARS = 64_000;

    private WorkspaceContextBuilder() {
    }

    public static CodeContext enrich(
            Project project,
            WorkspaceIndex index,
            String question,
            String activeFile,
            CodeContext context) {
        return enrich(project, index, question, activeFile, context, List.of());
    }

    public static CodeContext enrich(
            Project project,
            WorkspaceIndex index,
            String question,
            String activeFile,
            CodeContext context,
            List<String> preferredPaths) {
        if (project == null || project.isDisposed()) {
            return context == null ? CodeContext.empty() : context;
        }
        return ReadAction.computeBlocking(() -> {
            CodeContext base = context == null ? CodeContext.empty() : context;
            Map<String, CodeFileSnapshot> files = new LinkedHashMap<>();
            for (CodeFileSnapshot file : base.files()) {
                String path = file == null ? "" : EditGuard.normalizeRelativePath(file.path());
                if (!path.isBlank() && !file.isEmpty() && files.size() < MAX_CONTEXT_FILES) {
                    files.putIfAbsent(path, file);
                }
            }

            List<String> candidates = candidatePaths(
                    project, index, question, activeFile, preferredPaths);
            int used = files.values().stream().mapToInt(file -> file.content().length()).sum();
            for (String path : candidates) {
                if (files.size() >= MAX_CONTEXT_FILES) {
                    break;
                }
                String safePath = EditGuard.normalizeRelativePath(path);
                if (safePath.isBlank() || files.containsKey(safePath)) {
                    continue;
                }
                int remaining = MAX_TOTAL_CHARS - used;
                if (remaining < 1_000) {
                    break;
                }
                VirtualFile file = WorkspaceFileReader.resolve(project, safePath);
                if (file == null || file.isDirectory()) {
                    continue;
                }
                int fileBudget = Math.min(MAX_CHARS_PER_FILE, remaining);
                CodeFileSnapshot snapshot =
                        WorkspaceFileReader.readForContext(project, file, question, fileBudget);
                if (snapshot == null || snapshot.isEmpty()) {
                    continue;
                }
                files.put(safePath, snapshot);
                used += snapshot.content().length();
            }
            return base.withFiles(new ArrayList<>(files.values()));
        });
    }

    /** Files whose content differs between a previous analysis and the new snapshot. */
    public static List<String> changedPaths(
            CodeContext previous, CodeContext current) {
        if (previous == null || !previous.hasFiles()) {
            return List.of();
        }
        Map<String, String> currentByPath = new LinkedHashMap<>();
        Map<String, Boolean> currentComplete = new LinkedHashMap<>();
        if (current != null) {
            for (CodeFileSnapshot file : current.files()) {
                String path = EditGuard.normalizeRelativePath(file.path());
                currentByPath.put(path, file.content());
                currentComplete.put(path, !file.truncated());
            }
        }
        List<String> changed = new ArrayList<>();
        for (CodeFileSnapshot file : previous.files()) {
            String path = EditGuard.normalizeRelativePath(file.path());
            String latest = currentByPath.get(path);
            if (latest == null) {
                changed.add(path);
            } else if (!file.truncated()
                    && Boolean.TRUE.equals(currentComplete.get(path))
                    && !latest.equals(file.content())) {
                changed.add(path);
            }
        }
        return List.copyOf(changed);
    }

    /** Compact, user-facing description of the source snapshot. */
    public static String summary(CodeContext context, boolean chinese) {
        if (context == null || !context.hasFiles()) {
            return "";
        }
        long complete = context.files().stream().filter(file -> !file.truncated()).count();
        long partial = context.files().size() - complete;
        String names = context.files().stream()
                .map(CodeFileSnapshot::fileName)
                .limit(3)
                .reduce((left, right) -> left + ", " + right)
                .orElse("");
        if (chinese) {
            return context.files().size() + " 个文件（" + complete + " 个完整"
                    + (partial == 0 ? "" : "，" + partial + " 个按方法摘录")
                    + "）：" + names;
        }
        return context.files().size() + " files (" + complete + " full"
                + (partial == 0 ? "" : ", " + partial + " method-aware") + "): " + names;
    }

    private static List<String> candidatePaths(
            Project project,
            WorkspaceIndex index,
            String question,
            String activeFile,
            List<String> preferredPaths) {
        Set<String> result = new LinkedHashSet<>();
        String active = EditGuard.normalizeRelativePath(activeFile);
        if (!active.isBlank()) {
            result.add(active);
        }
        if (preferredPaths != null) {
            for (String preferred : preferredPaths) {
                String path = EditGuard.normalizeRelativePath(preferred);
                if (!path.isBlank()) {
                    result.add(path);
                }
            }
        }
        if (index != null) {
            String signal = active.isBlank() ? question : active + "\n" + question;
            WorkspaceOutlineSelector.Ranking ranking =
                    WorkspaceOutlineSelector.rank(index, signal, MAX_CONTEXT_FILES * 2);
            for (WorkspaceOutline outline : ranking.files()) {
                result.add(outline.path());
            }
        }
        result.addAll(configurationCandidates(project, question));
        return List.copyOf(result);
    }

    private static List<String> configurationCandidates(Project project, String question) {
        String lower = question == null ? "" : question.toLowerCase(Locale.ROOT);
        boolean configurationQuestion = containsAny(lower,
                "config", "configuration", "property", "properties", "spring",
                "database", "jdbc", "datasource", "profile", "port", "url",
                "配置", "数据库", "连接", "端口", "依赖");
        if (!configurationQuestion) {
            return List.of();
        }

        List<ScoredPath> candidates = new ArrayList<>();
        ProjectFileIndex fileIndex = ProjectFileIndex.getInstance(project);
        fileIndex.iterateContent(file -> {
            if (candidates.size() >= 80) {
                return false;
            }
            if (file.isDirectory()) {
                return true;
            }
            String path = WorkspaceFileReader.projectRelativePath(project, file);
            if (!CodeFilePolicy.isReadableContext(path)
                    || CodeFilePolicy.isSupported(path)) {
                return true;
            }
            String pathLower = path.toLowerCase(Locale.ROOT);
            int score = configurationScore(pathLower, lower);
            if (score > 0) {
                candidates.add(new ScoredPath(path, score));
            }
            return true;
        });
        candidates.sort(Comparator.comparingInt(ScoredPath::score).reversed()
                .thenComparing(ScoredPath::path));
        return candidates.stream().limit(2).map(ScoredPath::path).toList();
    }

    private static int configurationScore(String path, String question) {
        int score = 0;
        String fileName = path.substring(path.lastIndexOf('/') + 1);
        if (question.contains(fileName)) {
            score += 20;
        }
        int dot = fileName.lastIndexOf('.');
        String stem = dot < 0 ? fileName : fileName.substring(0, dot);
        if (stem.length() >= 3 && question.contains(stem)) {
            score += 10;
        }
        if (fileName.startsWith("application") || fileName.startsWith("bootstrap")) {
            score += 8;
        }
        if (fileName.equals("pom.xml") || fileName.equals("web.xml")
                || fileName.equals("persistence.xml")) {
            score += 7;
        }
        if (path.contains("src/main/resources")) {
            score += 3;
        }
        if (path.contains("test")) {
            score -= 2;
        }
        return score;
    }

    private static boolean containsAny(String text, String... values) {
        for (String value : values) {
            if (text.contains(value)) {
                return true;
            }
        }
        return false;
    }

    private record ScoredPath(String path, int score) {
    }
}
