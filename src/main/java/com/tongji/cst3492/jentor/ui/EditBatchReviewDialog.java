package com.tongji.cst3492.jentor.ui;

import com.intellij.diff.DiffContentFactory;
import com.intellij.diff.DiffManager;
import com.intellij.diff.DiffRequestPanel;
import com.intellij.diff.requests.SimpleDiffRequest;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.util.ui.JBUI;
import com.tongji.cst3492.jentor.context.WorkspaceFileReader;
import com.tongji.cst3492.jentor.model.EditBatchResult;
import com.tongji.cst3492.jentor.model.EditProposal;
import com.tongji.cst3492.jentor.model.EditReviewState;
import com.tongji.cst3492.jentor.model.SkippedEditFile;
import com.tongji.cst3492.jentor.service.EditApplier;
import com.tongji.cst3492.jentor.service.EditBatchApplyResult;
import com.tongji.cst3492.jentor.service.JavaEditValidator;
import com.tongji.cst3492.jentor.settings.TaSettings;
import org.jetbrains.annotations.NotNull;

import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.List;

/** Reviews a multi-file edit batch using IntelliJ's built-in two-pane diff. */
public final class EditBatchReviewDialog extends DialogWrapper implements Disposable {
    private final Project project;
    private final EditBatchResult batch;
    private final JBLabel banner = new JBLabel();
    private final List<ReviewItem> items;
    private final List<String> invalidFiles = new ArrayList<>();
    private final List<MonitorBinding> monitors = new ArrayList<>();
    private EditBatchApplyResult applyResult;

    public EditBatchReviewDialog(Project project, EditBatchResult batch) {
        super(project, true);
        this.project = project;
        this.batch = batch;
        this.items = resolveItems(project, batch.proposals());
        setTitle(I18n.t(language(), "editReviewTitle"));
        setOKButtonText(I18n.t(language(), "editApplyAll"));
        init();
        updateBanner();
    }

    @Override
    protected void init() {
        super.init();
        for (ReviewItem item : items) {
            if (item.document() == null) {
                continue;
            }
            DocumentListener monitor = new DocumentListener() {
                @Override
                public void documentChanged(@NotNull DocumentEvent event) {
                    if (item.state().onSourceChanged(
                            item.document().getModificationStamp())) {
                        updateBanner();
                    }
                }
            };
            item.document().addDocumentListener(monitor);
            monitors.add(new MonitorBinding(item.document(), monitor));
        }
    }

    @Override
    protected JComponent createCenterPanel() {
        JPanel root = new JPanel(new BorderLayout(0, 8));
        banner.setBorder(JBUI.Borders.empty(4, 10));
        banner.setForeground(JBColor.GRAY);
        root.add(banner, BorderLayout.NORTH);

        JBTabbedPane tabs = new JBTabbedPane();
        DiffContentFactory factory = DiffContentFactory.getInstance();
        for (ReviewItem item : items) {
            if (!item.proposal().isCreate()
                    && (item.file() == null || item.document() == null)) {
                invalidFiles.add(item.proposal().path());
                continue;
            }
            DiffRequestPanel panel =
                    DiffManager.getInstance().createRequestPanel(project, this, null);
            if (item.proposal().isCreate()) {
                String fileName = fileName(item.proposal().path());
                var fileType = FileTypeManager.getInstance()
                        .getFileTypeByFileName(fileName);
                panel.setRequest(new SimpleDiffRequest(
                        fileName,
                        factory.createEmpty(),
                        factory.create(project, item.proposal().newText(), fileType),
                        I18n.t(language(), "editReviewCurrent"),
                        I18n.t(language(), "editReviewSuggested")));
                tabs.addTab("[NEW] " + fileName, panel.getComponent());
            } else {
                panel.setRequest(new SimpleDiffRequest(
                        item.file().getName(),
                        factory.create(project, item.document(), item.file()),
                        factory.create(project, item.proposal().newText(), item.file()),
                        I18n.t(language(), "editReviewCurrent"),
                        I18n.t(language(), "editReviewSuggested")));
                tabs.addTab(item.file().getName(), panel.getComponent());
            }
            tabs.setToolTipTextAt(tabs.getTabCount() - 1, item.proposal().path());
        }
        if (tabs.getTabCount() == 0) {
            tabs.addTab("No changes", new JBLabel("No reviewable files."));
        }
        root.add(tabs, BorderLayout.CENTER);
        root.setPreferredSize(new Dimension(1180, 760));
        return root;
    }

    @Override
    protected void doOKAction() {
        EditBatchApplyResult result = EditApplier.applyAll(project, batch.proposals());
        applyResult = result;
        if (result.status() == EditBatchApplyResult.Status.APPLIED
                || result.status() == EditBatchApplyResult.Status.NO_CHANGE) {
            super.doOKAction();
            return;
        }
        setErrorText(String.join("\n", result.errors()));
    }

    public EditBatchApplyResult applyResult() {
        return applyResult;
    }

    private void updateBanner() {
        boolean stale = items.stream().anyMatch(item -> item.state().isStale());
        boolean invalid = !invalidFiles.isEmpty()
                || items.stream().anyMatch(item -> !item.syntaxError().isBlank());
        banner.setForeground(stale || invalid ? JBColor.RED : JBColor.GRAY);
        if (stale) {
            banner.setText(I18n.t(language(), "editBatchStale"));
        } else if (invalid) {
            banner.setText(I18n.t(language(), "editBatchInvalid"));
        } else {
            StringBuilder text = new StringBuilder("<html>")
                    .append(escape(batch.summary()))
                    .append("<br/>")
                    .append(escape(I18n.t(language(), "editBatchHint")));
            if (!batch.skipped().isEmpty()) {
                text.append("<br/>")
                        .append(escape(I18n.t(language(), "editSkippedFiles")))
                        .append(' ');
                for (int index = 0; index < batch.skipped().size(); index++) {
                    if (index > 0) {
                        text.append("; ");
                    }
                    SkippedEditFile skipped = batch.skipped().get(index);
                    text.append(escape(skipped.path()));
                    if (!skipped.reason().isBlank()) {
                        text.append(" (").append(escape(skipped.reason())).append(')');
                    }
                }
            }
            banner.setText(text.append("</html>").toString());
        }
        getOKAction().setEnabled(!stale && !invalid && !items.isEmpty());
    }

    @Override
    public void dispose() {
        for (MonitorBinding binding : monitors) {
            binding.document().removeDocumentListener(binding.listener());
        }
        super.dispose();
    }

    private List<ReviewItem> resolveItems(Project project, List<EditProposal> proposals) {
        List<ReviewItem> resolved = new ArrayList<>();
        for (EditProposal proposal : proposals) {
            if (proposal.isCreate()) {
                String syntaxError = JavaEditValidator.validationError(
                        project, proposal.path(), proposal.newText());
                resolved.add(new ReviewItem(
                        proposal, null, null, syntaxError, new EditReviewState(0)));
                continue;
            }
            VirtualFile file = WorkspaceFileReader.resolve(project, proposal.path());
            Document document = file == null ? null
                    : com.intellij.openapi.fileEditor.FileDocumentManager
                    .getInstance().getDocument(file);
            String syntaxError = file == null ? "File not found"
                    : JavaEditValidator.validationError(project, file, proposal.newText());
            resolved.add(new ReviewItem(
                    proposal,
                    file,
                    document,
                    syntaxError,
                    document == null
                            ? new EditReviewState(0)
                            : new EditReviewState(document.getModificationStamp())));
        }
        return List.copyOf(resolved);
    }

    private static String fileName(String path) {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? path : path.substring(slash + 1);
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

    private record ReviewItem(
            EditProposal proposal,
            VirtualFile file,
            Document document,
            String syntaxError,
            EditReviewState state) {
    }

    private record MonitorBinding(
            Document document, DocumentListener listener) {
    }
}
