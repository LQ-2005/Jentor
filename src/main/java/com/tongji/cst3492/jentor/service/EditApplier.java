package com.tongji.cst3492.jentor.service;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.tongji.cst3492.jentor.context.EditGuard;
import com.tongji.cst3492.jentor.context.WorkspaceFileReader;
import com.tongji.cst3492.jentor.model.EditProposal;

import java.io.IOException;
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
                            if (edit.proposal().isCreate()) {
                                if (WorkspaceFileReader.resolve(
                                        project, edit.proposal().path()) != null) {
                                    throw new IllegalStateException(
                                            "File was created during review: "
                                                    + edit.proposal().path());
                                }
                                try {
                                    VirtualFile created = edit.parent().createChildData(
                                            project, edit.fileName());
                                    VfsUtil.saveText(created, edit.nextText());
                                } catch (IOException error) {
                                    throw new IllegalStateException(
                                            "Cannot write new file: "
                                                    + edit.proposal().path(), error);
                                }
                            } else {
                                if (EditGuard.isConflict(
                                        edit.proposal().baseModificationStamp(),
                                        edit.document().getModificationStamp())) {
                                    throw new IllegalStateException(
                                            "File changed while applying: "
                                                    + edit.proposal().path());
                                }
                                edit.document().replaceString(
                                        0, edit.document().getTextLength(), edit.nextText());
                            }
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
        if (proposal.isCreate()) {
            if (proposal.newText().isBlank()) {
                errors.add("New file is empty: " + proposal.path());
                return null;
            }
            VirtualFile existing = WorkspaceFileReader.resolve(project, proposal.path());
            if (existing != null) {
                errors.add("Cannot create; file already exists: " + proposal.path());
                return null;
            }
            int slash = proposal.path().lastIndexOf('/');
            VirtualFile parent = slash < 0
                    ? ProjectUtil.guessProjectDir(project)
                    : WorkspaceFileReader.resolve(project, proposal.path().substring(0, slash));
            if (parent == null || !parent.isValid()
                    || !parent.isDirectory() || !parent.isWritable()) {
                errors.add("Parent directory is unavailable or read-only: "
                        + proposal.path());
                return null;
            }
            String next = EditGuard.normalize(proposal.newText());
            String syntaxError = JavaEditValidator.validationError(
                    project, proposal.path(), next);
            if (!syntaxError.isBlank()) {
                errors.add("Java syntax error in " + proposal.path() + ": " + syntaxError);
                return null;
            }
            return new PreparedEdit(
                    proposal,
                    null,
                    parent,
                    proposal.path().substring(slash + 1),
                    next);
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
        return new PreparedEdit(proposal, document, null, "", next);
    }

    private record PreparedEdit(
            EditProposal proposal,
            Document document,
            VirtualFile parent,
            String fileName,
            String nextText) {
    }
}
