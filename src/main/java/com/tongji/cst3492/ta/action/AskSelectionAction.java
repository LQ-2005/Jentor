package com.tongji.cst3492.ta.action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import com.tongji.cst3492.ta.model.CodeContext;
import com.tongji.cst3492.ta.service.CourseTaService;
import com.tongji.cst3492.ta.ui.ChatPanel;
import org.jetbrains.annotations.NotNull;

public final class AskSelectionAction extends AnAction {
    @Override
    public void update(@NotNull AnActionEvent event) {
        Editor editor = event.getData(CommonDataKeys.EDITOR);
        event.getPresentation().setEnabledAndVisible(
                editor != null && editor.getSelectionModel().hasSelection());
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        Editor editor = event.getData(CommonDataKeys.EDITOR);
        if (project == null || editor == null) {
            return;
        }
        if (!editor.getSelectionModel().hasSelection()) {
            return;
        }
        VirtualFile file = event.getData(CommonDataKeys.VIRTUAL_FILE);
        CodeContext context = SelectionContextFactory.create(editor, file);
        if (!context.hasSelection()) {
            return;
        }

        CourseTaService service = project.getService(CourseTaService.class);
        if (service != null) {
            service.setPendingContext(context);
        }

        ToolWindow window = ToolWindowManager.getInstance(project).getToolWindow("Course TA");
        if (window == null) {
            return;
        }
        window.show();

        Content content = window.getContentManager().getContent(0);
        if (content != null && content.getComponent() instanceof ChatPanel panel) {
            panel.setPendingContext(context);
            if (service != null) {
                service.consumePendingContext();
            }
        }
    }
}
