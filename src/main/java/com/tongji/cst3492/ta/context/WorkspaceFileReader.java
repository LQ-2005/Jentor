package com.tongji.cst3492.ta.context;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.tongji.cst3492.ta.model.CodeFileSnapshot;

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
        if (project == null || file == null || !file.isValid() || file.isDirectory()) {
            return null;
        }
        ProjectFileIndex index = ProjectFileIndex.getInstance(project);
        if (index.isExcluded(file) || index.isInLibrary(file) || !index.isInContent(file)) {
            return null;
        }
        String path = projectRelativePath(project, file);
        if (!CodeFilePolicy.isSupported(path)) {
            return null;
        }
        if (file.getLength() > MAX_BYTES) {
            return null;
        }
        String text = textOf(file);
        if (text == null) {
            return null;
        }
        return CodeFilePolicy.snapshot(path, file.getFileType().getName(), text);
    }

    private static String projectRelativePath(Project project, VirtualFile file) {
        VirtualFile base = ProjectUtil.guessProjectDir(project);
        if (base != null) {
            String relative = VfsUtilCore.getRelativePath(file, base, '/');
            if (relative != null && !relative.isBlank()) {
                return relative;
            }
        }
        return file.getName();
    }

    private static String textOf(VirtualFile file) {
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
