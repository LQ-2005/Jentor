package com.tongji.cst3492.jentor.action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import com.tongji.cst3492.jentor.context.CodeFilePolicy;
import com.tongji.cst3492.jentor.context.WorkspaceFileReader;
import com.tongji.cst3492.jentor.model.CodeFileSnapshot;
import com.tongji.cst3492.jentor.service.CourseTaService;
import com.tongji.cst3492.jentor.ui.ChatPanel;
import com.tongji.cst3492.jentor.ui.JentorToolWindowPanel;
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
        List<VirtualFile> candidates = candidateFiles(event);
        if (project == null || candidates.isEmpty()) {
            return;
        }
        final List<VirtualFile> files = List.copyOf(candidates.subList(
                0, Math.min(candidates.size(), CodeFilePolicy.MAX_FILES)));
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            List<CodeFileSnapshot> snapshots = ReadAction.computeBlocking(() -> {
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
        if (project.isDisposed()) {
            return;
        }
        if (snapshots.isEmpty()) {
            Messages.showMessageDialog(project,
                    "没有可附加的文件：只支持 .java、.kt、.groovy、.gradle 和 .kts 源码文件，"
                            + "文件必须位于当前项目内，且不能包含明显的密钥或密码。\n"
                            + "No file was attached: only project source/build-script files "
                            + "without obvious embedded secrets are supported.",
                    "Attach File to Jentor",
                    Messages.getWarningIcon());
            return;
        }
        CourseTaService service = project.getService(CourseTaService.class);
        if (service != null) {
            service.addPendingFiles(snapshots);
        }
        ToolWindow window = ToolWindowManager.getInstance(project).getToolWindow("Jentor");
        if (window == null) {
            return;
        }
        window.show();
        Content content = window.getContentManager().getContent(0);
        if (content != null && content.getComponent() instanceof JentorToolWindowPanel panel) {
            ChatPanel chatPanel = panel.chatPanel();
            chatPanel.addFiles(snapshots);
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
