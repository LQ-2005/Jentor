package com.tongji.cst3492.jentor.action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import com.tongji.cst3492.jentor.context.EditGuard;
import com.tongji.cst3492.jentor.model.CodeContext;
import com.tongji.cst3492.jentor.service.CourseTaService;
import com.tongji.cst3492.jentor.ui.ChatPanel;
import com.tongji.cst3492.jentor.ui.JentorToolWindowPanel;
import org.jetbrains.annotations.NotNull;

/** Opens Jentor with an optional shorthand for editing the active writable file. */
public final class EditCodeAction extends AnAction {
    @Override
    public void update(@NotNull AnActionEvent event) {
        VirtualFile file = event.getData(CommonDataKeys.VIRTUAL_FILE);
        boolean available = event.getProject() != null
                && event.getData(CommonDataKeys.EDITOR) != null
                && file != null
                && file.isValid()
                && file.isWritable()
                && EditGuard.isWritablePath(file.getName());
        event.getPresentation().setEnabledAndVisible(available);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        Editor editor = event.getData(CommonDataKeys.EDITOR);
        VirtualFile file = event.getData(CommonDataKeys.VIRTUAL_FILE);
        if (project == null || editor == null || file == null
                || !file.isWritable() || !EditGuard.isWritablePath(file.getName())) {
            return;
        }

        CodeContext context = editor.getSelectionModel().hasSelection()
                ? SelectionContextFactory.create(editor, file) : null;
        CourseTaService service = project.getService(CourseTaService.class);
        if (service != null && context != null) {
            service.setPendingContext(context);
        }

        ToolWindow window = ToolWindowManager.getInstance(project).getToolWindow("Jentor");
        if (window == null) {
            return;
        }
        window.show();
        Content content = window.getContentManager().getContent(0);
        if (content != null && content.getComponent() instanceof JentorToolWindowPanel panel) {
            ChatPanel chatPanel = panel.chatPanel();
            VirtualFile projectDir = ProjectUtil.guessProjectDir(project);
            String path = projectDir == null
                    ? null : VfsUtilCore.getRelativePath(file, projectDir, '/');
            chatPanel.prepareCodeEdit(context, path);
            if (service != null && context != null) {
                service.consumePendingContext();
            }
        }
    }
}
