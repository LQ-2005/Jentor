package com.tongji.cst3492.jentor.service;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.tongji.cst3492.jentor.context.EditGuard;
import com.tongji.cst3492.jentor.context.WorkspaceFileReader;
import com.tongji.cst3492.jentor.model.EditProposal;

/**
 * Applies an AI-proposed edit to the workspace. This is the plugin's only write
 * path: it replaces the whole document inside an undoable command and refuses
 * when the file changed after the suggestion was produced.
 *
 * <p>Must be called on the EDT, after the user approved the change in the diff
 * viewer.</p>
 */
public final class EditApplier {
    public static final String COMMAND_NAME = "Jentor: apply suggested change";

    private EditApplier() {
    }

    public static EditApplyResult apply(Project project, EditProposal proposal) {
        if (project == null || project.isDisposed()) {
            return EditApplyResult.refused("the project is not available");
        }
        if (proposal == null) {
            return EditApplyResult.refused("there is no suggestion to apply");
        }
        if (!EditGuard.isWritablePath(proposal.path())) {
            return EditApplyResult.refused("this file type cannot be edited: " + proposal.path());
        }
        if (EditGuard.isTooLarge(proposal.newText())) {
            return EditApplyResult.refused("the proposed file is too large to apply");
        }
        VirtualFile file = WorkspaceFileReader.resolve(project, proposal.path());
        if (file == null || !file.isValid() || file.isDirectory()) {
            return EditApplyResult.notFound(proposal.path());
        }
        Document document = FileDocumentManager.getInstance().getDocument(file);
        if (document == null) {
            return EditApplyResult.refused("not a text file: " + proposal.path());
        }
        if (EditGuard.isConflict(proposal.baseModificationStamp(), document.getModificationStamp())) {
            return EditApplyResult.conflict(
                    "the file changed after the suggestion was produced; review it again");
        }
        String next = EditGuard.normalize(proposal.newText());
        if (EditGuard.isNoOp(document.getText(), next)) {
            return EditApplyResult.noChange(proposal.path());
        }
        try {
            WriteCommandAction.writeCommandAction(project)
                    .withName(COMMAND_NAME)
                    .run(() -> document.replaceString(0, document.getTextLength(), next));
            return EditApplyResult.applied(proposal.path());
        } catch (RuntimeException error) {
            String message = error.getMessage();
            return EditApplyResult.failed(message == null ? error.getClass().getSimpleName() : message);
        }
    }
}
