package com.tongji.cst3492.ta.action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import com.tongji.cst3492.ta.context.WorkspaceFileReader;
import com.tongji.cst3492.ta.model.CodeFileSnapshot;
import com.tongji.cst3492.ta.service.CourseTaService;
import com.tongji.cst3492.ta.ui.ChatPanel;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Attaches workspace source files to the conversation. Files are read once, in a
 * read action on a background thread, and the plugin never reads them again.
 */
public final class AttachFileAction extends AnAction {
    @Override
    public void update(@NotNull AnActionEvent event) {
        event.getPresentation().setEnabledAndVisible(!candidateFiles(event).isEmpty());
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        List<VirtualFile> files = candidateFiles(event);
        if (project == null || files.isEmpty()) {
            return;
        }
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            List<CodeFileSnapshot> snapshots = ReadAction.compute(() -> {
                List<CodeFileSnapshot> loaded = new ArrayList<>();
                for (VirtualFile file : files) {
                    CodeFileSnapshot snapshot = WorkspaceFileReader.read(project, file);
                    if (snapshot != null && !snapshot.isEmpty()) {
                        loaded.add(snapshot);
                    }
                }
                return loaded;
            });
            ApplicationManager.getApplication().invokeLater(() -> attach(project, snapshots));
        });
    }

    private static void attach(Project project, List<CodeFileSnapshot> snapshots) {
        if (snapshots.isEmpty() || project.isDisposed()) {
            return;
        }
        CourseTaService service = project.getService(CourseTaService.class);
        if (service != null) {
            service.addPendingFiles(snapshots);
        }
        ToolWindow window = ToolWindowManager.getInstance(project).getToolWindow("Course TA");
        if (window == null) {
            return;
        }
        window.show();
        Content content = window.getContentManager().getContent(0);
        if (content != null && content.getComponent() instanceof ChatPanel panel) {
            panel.addFiles(snapshots);
            if (service != null) {
                service.consumePendingFiles();
            }
        }
    }

    private static List<VirtualFile> candidateFiles(AnActionEvent event) {
        List<VirtualFile> files = new ArrayList<>();
        VirtualFile[] selection = event.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
        if (selection != null) {
            for (VirtualFile file : selection) {
                if (file != null && !file.isDirectory()) {
                    files.add(file);
                }
            }
        }
        if (files.isEmpty()) {
            VirtualFile current = event.getData(CommonDataKeys.VIRTUAL_FILE);
            if (current != null && !current.isDirectory()) {
                files.add(current);
            }
        }
        return files;
    }
}
