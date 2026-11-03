package com.tongji.cst3492.jentor.ui;

import com.intellij.diff.DiffContentFactory;
import com.intellij.diff.DiffManager;
import com.intellij.diff.DiffRequestPanel;
import com.intellij.diff.requests.SimpleDiffRequest;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBUI;
import com.tongji.cst3492.jentor.model.EditProposal;
import com.tongji.cst3492.jentor.model.EditReviewState;
import com.tongji.cst3492.jentor.service.EditApplier;
import com.tongji.cst3492.jentor.service.EditApplyResult;
import com.tongji.cst3492.jentor.service.JavaEditValidator;
import com.tongji.cst3492.jentor.settings.TaSettings;
import org.jetbrains.annotations.NotNull;

import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Dimension;

/**
 * Reviews one AI-proposed change in the IDE's built-in diff viewer.
 *
 * <p>The left pane is bound to the live {@link Document}, so the comparison keeps
 * following the file while the dialog is open. A document listener watches the
 * same document and marks the suggestion stale as soon as it changes, which
 * disables applying until the user regenerates.</p>
 */
public final class EditReviewDialog extends DialogWrapper implements Disposable {
    private final Project project;
    private final VirtualFile file;
    private final Document document;
    private final String proposedText;
    private final String instruction;
    private final EditReviewState state;
    private final String syntaxError;
    private final JBLabel banner = new JBLabel();

    private final DocumentListener monitor = new DocumentListener() {
        @Override
        public void documentChanged(@NotNull DocumentEvent event) {
            if (state.onSourceChanged(document.getModificationStamp())) {
                updateBanner();
            }
        }
    };

    public EditReviewDialog(
            Project project,
            VirtualFile file,
            Document document,
            String proposedText,
            String instruction) {
        super(project, true);
        this.project = project;
        this.file = file;
        this.document = document;
        this.proposedText = proposedText;
        this.instruction = instruction;
        this.state = new EditReviewState(document.getModificationStamp());
        this.syntaxError = JavaEditValidator.validationError(project, file, proposedText);
        setTitle(I18n.t(language(), "editReviewTitle"));
        setOKButtonText(I18n.t(language(), "editApply"));
        init();
        updateBanner();
    }

    @Override
    protected void init() {
        super.init();
        document.addDocumentListener(monitor);
    }

    @Override
    protected JComponent createCenterPanel() {
        JPanel root = new JPanel(new BorderLayout(0, 8));
        banner.setBorder(JBUI.Borders.empty(4, 10));
        banner.setText(I18n.t(language(), "editReviewHint"));
        banner.setForeground(JBColor.GRAY);

        diffPanel = DiffManager.getInstance().createRequestPanel(project, this, null);
        DiffContentFactory factory = DiffContentFactory.getInstance();
        SimpleDiffRequest request = new SimpleDiffRequest(
                file.getName(),
                factory.create(project, document, file),
                factory.create(project, proposedText, file),
                I18n.t(language(), "editReviewCurrent"),
                I18n.t(language(), "editReviewSuggested"));
        diffPanel.setRequest(request);

        root.add(banner, BorderLayout.NORTH);
        root.add(diffPanel.getComponent(), BorderLayout.CENTER);
        root.setPreferredSize(new Dimension(1120, 720));
        return root;
    }

    private DiffRequestPanel diffPanel;

    @Override
    protected void doOKAction() {
        String path = VfsUtilCore.getRelativePath(
                file, ProjectUtil.guessProjectDir(project), '/');
        if (path == null) {
            setErrorText("Cannot resolve the project-relative path of " + file.getName());
            return;
        }
        EditApplyResult result = EditApplier.apply(project,
                new EditProposal(path, state.baseModificationStamp(), proposedText, instruction));
        if (result.isApplied() || result.status() == EditApplyResult.Status.NO_CHANGE) {
            super.doOKAction();
            return;
        }
        setErrorText(result.status() + ": " + result.message());
    }

    private void updateBanner() {
        boolean stale = state.isStale();
        boolean invalid = !syntaxError.isBlank();
        banner.setForeground(stale || invalid ? JBColor.RED : JBColor.GRAY);
        if (stale) {
            banner.setText(I18n.t(language(), "editStale"));
        } else if (invalid) {
            banner.setText(I18n.t(language(), "editSyntaxError") + " " + syntaxError);
        } else {
            banner.setText("<html>" + escape(instruction) + "<br/>"
                    + I18n.t(language(), "editReviewHint") + "</html>");
        }
        getOKAction().setEnabled(!stale && !invalid);
    }

    @Override
    public void dispose() {
        document.removeDocumentListener(monitor);
        super.dispose();
    }

    private String language() {
        return "en".equals(TaSettings.getInstance().getState().language) ? "en" : "zh";
    }

    private static String escape(String text) {
        return text == null ? "" : text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
