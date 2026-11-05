package com.tongji.cst3492.jentor.context;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.tongji.cst3492.jentor.model.CodeFileSnapshot;

import java.io.IOException;

/**
 * Reads a workspace file into a snapshot. The caller owns the read action and the
 * background thread; this class only knows how to turn a {@link VirtualFile} into
 * text.
 *
 * <p>The document is preferred over the file on disk so unsaved editor changes are
 * captured. Once a snapshot is returned the plugin never reads the file again:
 * attaching is the only moment the content is taken.</p>
 */
public final class WorkspaceFileReader {
    /** Files larger than this are refused instead of truncated mid-character. */
    public static final long MAX_BYTES = 512 * 1024;

    private WorkspaceFileReader() {
    }

    /**
     * @return the snapshot, or {@code null} when the file is outside the project,
     *         excluded, unsupported, too large or unreadable.
     */
    public static CodeFileSnapshot read(Project project, VirtualFile file) {
        return readInternal(project, file, false, "", CodeFilePolicy.MAX_CHARS_PER_FILE);
    }

    /**
     * Reads source or configuration context for an automatic analysis request.
     * Small files are kept whole. Large files use method/range-aware selection so
     * a bug near the end of a file is not silently omitted by prefix truncation.
     */
    public static CodeFileSnapshot readForContext(
            Project project, VirtualFile file, String question, int maxChars) {
        return readInternal(project, file, true, question, maxChars);
    }

    private static CodeFileSnapshot readInternal(
            Project project,
            VirtualFile file,
            boolean readableConfig,
            String question,
            int maxChars) {
        if (project == null || file == null || !file.isValid() || file.isDirectory()) {
            return null;
        }
        ProjectFileIndex index = ProjectFileIndex.getInstance(project);
        if (index.isExcluded(file) || index.isInLibrary(file)
                || index.isInGeneratedSources(file)) {
            return null;
        }
        // A file inside an imported module is accepted directly. Files under the
        // project directory are also accepted, because a folder opened without a
        // successful Gradle/Maven import has no content roots and isInContent()
        // returns false for everything - that must not silently disable the feature.
        if (!index.isInContent(file) && !isUnderProjectDir(project, file)) {
            return null;
        }
        String path = projectRelativePath(project, file);
        if (readableConfig
                ? !CodeFilePolicy.isReadableContext(path)
                : !CodeFilePolicy.isSupported(path)) {
            return null;
        }
        if (file.getLength() > MAX_BYTES) {
            return null;
        }
        String text = textOf(file);
        if (text == null) {
            return null;
        }
        if (CodeFilePolicy.containsLikelySecret(text)) {
            return null;
        }
        if (!readableConfig) {
            return CodeFilePolicy.snapshot(path, file.getFileType().getName(), text);
        }

        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        int totalLines = countLines(normalized);
        if (normalized.length() <= maxChars) {
            return new CodeFileSnapshot(
                    path,
                    file.getName(),
                    file.getFileType().getName(),
                    1,
                    totalLines,
                    totalLines,
                    normalized,
                    false);
        }
        String excerpt = SmartCodeExcerpt.render(normalized, question, maxChars);
        return new CodeFileSnapshot(
                path,
                file.getName(),
                file.getFileType().getName(),
                1,
                totalLines,
                totalLines,
                excerpt,
                true);
    }

    private static int countLines(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        String[] lines = text.split("\n", -1);
        int count = lines.length;
        if (count > 0 && lines[count - 1].isEmpty()) {
            count--;
        }
        return Math.max(1, count);
    }

    private static boolean isUnderProjectDir(Project project, VirtualFile file) {
        VirtualFile base = ProjectUtil.guessProjectDir(project);
        return base != null && VfsUtilCore.isAncestor(base, file, false);
    }

    /** Resolves a project-relative path to a file, or {@code null}. */
    public static VirtualFile resolve(Project project, String relativePath) {
        VirtualFile base = project == null ? null : ProjectUtil.guessProjectDir(project);
        String safePath = EditGuard.normalizeRelativePath(relativePath);
        if (base == null || safePath.isBlank()) {
            return null;
        }
        VirtualFile resolved = base.findFileByRelativePath(safePath);
        if (resolved == null || !VfsUtilCore.isAncestor(base, resolved, false)) {
            return null;
        }
        return resolved;
    }

    /** Package-visible so the index builder can reuse the same path rules. */
    static String projectRelativePath(Project project, VirtualFile file) {
        VirtualFile base = ProjectUtil.guessProjectDir(project);
        if (base != null) {
            String relative = VfsUtilCore.getRelativePath(file, base, '/');
            if (relative != null && !relative.isBlank()) {
                return relative;
            }
        }
        return file.getName();
    }

    /** Reads the text of a file, preferring the in-memory document. */
    public static String textOf(VirtualFile file) {
        Document document = FileDocumentManager.getInstance().getDocument(file);
        if (document != null) {
            return document.getText();
        }
        try {
            return VfsUtilCore.loadText(file);
        } catch (IOException unreadable) {
            return null;
        }
    }
}
