package com.tongji.cst3492.jentor.service;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.tongji.cst3492.jentor.context.EditGuard;
import com.tongji.cst3492.jentor.context.WorkspaceFileReader;
import com.tongji.cst3492.jentor.model.EditProposal;

import java.util.ArrayList;
import java.util.List;

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
        if (!file.isWritable()) {
            return EditApplyResult.refused("the file is read-only: " + proposal.path());
        }
        Document document = FileDocumentManager.getInstance().getDocument(file);
        if (document == null) {
            return EditApplyResult.refused("not a text file: " + proposal.path());
        }
        if (EditGuard.isConflict(proposal.baseModificationStamp(), document.getModificationStamp())) {
            return EditApplyResult.conflict(
                    "the file changed after the suggestion was produced; review it again");
        }
        String current = document.getText();
        if (EditGuard.isNoOp(current, proposal.newText())) {
            return EditApplyResult.noChange(proposal.path());
        }
        String next = EditGuard.preserveLineSeparators(current, proposal.newText());
        String syntaxError = JavaEditValidator.validationError(project, file, next);
        if (!syntaxError.isBlank()) {
            return EditApplyResult.refused(
                    "the suggested Java file has syntax errors: " + syntaxError);
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

    public static EditBatchApplyResult applyAll(
            Project project, List<EditProposal> proposals) {
        if (project == null || project.isDisposed()) {
            return EditBatchApplyResult.failed(List.of("The project is not available."));
        }
        if (proposals == null || proposals.isEmpty()) {
            return EditBatchApplyResult.failed(List.of("There are no changes to apply."));
        }
        List<PreparedEdit> prepared = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        for (EditProposal proposal : proposals) {
            PreparedEdit edit = prepare(project, proposal, errors);
            if (edit != null) {
                prepared.add(edit);
            }
        }
        if (!errors.isEmpty()) {
            return EditBatchApplyResult.failed(errors);
        }
        if (prepared.isEmpty()) {
            return EditBatchApplyResult.noChange();
        }
        try {
            WriteCommandAction.writeCommandAction(project)
                    .withName(COMMAND_NAME)
                    .run(() -> {
                        for (PreparedEdit edit : prepared) {
                            if (EditGuard.isConflict(
                                    edit.proposal().baseModificationStamp(),
                                    edit.document().getModificationStamp())) {
                                throw new IllegalStateException(
                                        "File changed while applying: " + edit.proposal().path());
                            }
                            edit.document().replaceString(
                                    0, edit.document().getTextLength(), edit.nextText());
                        }
                    });
            return EditBatchApplyResult.applied(
                    prepared.stream().map(edit -> edit.proposal().path()).toList());
        } catch (RuntimeException error) {
            String message = error.getMessage();
            return EditBatchApplyResult.failed(List.of(
                    message == null ? error.getClass().getSimpleName() : message));
        }
    }

    private static PreparedEdit prepare(
            Project project, EditProposal proposal, List<String> errors) {
        if (proposal == null) {
            errors.add("An edit proposal is missing.");
            return null;
        }
        if (!EditGuard.isWritablePath(proposal.path())) {
            errors.add("Unsupported or unsafe file: " + proposal.path());
            return null;
        }
        if (EditGuard.isTooLarge(proposal.newText())) {
            errors.add("Proposed file is too large: " + proposal.path());
            return null;
        }
        VirtualFile file = WorkspaceFileReader.resolve(project, proposal.path());
        if (file == null || !file.isValid() || file.isDirectory()) {
            errors.add("File not found: " + proposal.path());
            return null;
        }
        if (!file.isWritable()) {
            errors.add("File is read-only: " + proposal.path());
            return null;
        }
        Document document = FileDocumentManager.getInstance().getDocument(file);
        if (document == null) {
            errors.add("Not a text file: " + proposal.path());
            return null;
        }
        if (EditGuard.isConflict(
                proposal.baseModificationStamp(), document.getModificationStamp())) {
            errors.add("File changed after the suggestion was generated: " + proposal.path());
            return null;
        }
        String current = document.getText();
        if (EditGuard.isNoOp(current, proposal.newText())) {
            return null;
        }
        String next = EditGuard.preserveLineSeparators(current, proposal.newText());
        String syntaxError = JavaEditValidator.validationError(project, file, next);
        if (!syntaxError.isBlank()) {
            errors.add("Java syntax error in " + proposal.path() + ": " + syntaxError);
            return null;
        }
        return new PreparedEdit(proposal, document, next);
    }

    private record PreparedEdit(
            EditProposal proposal, Document document, String nextText) {
    }
}
