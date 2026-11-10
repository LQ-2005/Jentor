package com.tongji.cst3492.jentor.ui;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.ui.Messages;
import com.intellij.icons.AllIcons;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.util.ui.JBUI;
import com.tongji.cst3492.jentor.conversation.Conversation;
import com.tongji.cst3492.jentor.conversation.ConversationMessage;
import com.tongji.cst3492.jentor.conversation.ConversationStore;
import com.tongji.cst3492.jentor.conversation.ConversationSummary;
import com.tongji.cst3492.jentor.conversation.ConversationTurn;
import com.tongji.cst3492.jentor.llm.Answer;
import com.tongji.cst3492.jentor.llm.ChatMessage;
import com.tongji.cst3492.jentor.llm.CitationStatus;
import com.tongji.cst3492.jentor.llm.PromptBuilder;
import com.tongji.cst3492.jentor.llm.StreamSink;
import com.tongji.cst3492.jentor.context.CodeFilePolicy;
import com.tongji.cst3492.jentor.context.EditGuard;
import com.tongji.cst3492.jentor.intent.ConversationIntent;
import com.tongji.cst3492.jentor.intent.RouteDecision;
import com.tongji.cst3492.jentor.model.CodeContext;
import com.tongji.cst3492.jentor.model.CodeFileSnapshot;
import com.tongji.cst3492.jentor.model.CourseDocumentSource;
import com.tongji.cst3492.jentor.model.EditBatchResult;
import com.tongji.cst3492.jentor.model.SourceReference;
import com.tongji.cst3492.jentor.model.WorkspaceIndex;
import com.tongji.cst3492.jentor.service.ChatRequest;
import com.tongji.cst3492.jentor.service.CourseTaService;
import com.tongji.cst3492.jentor.service.EditBatchApplyResult;
import com.tongji.cst3492.jentor.service.EditBatchRequest;
import com.tongji.cst3492.jentor.service.IntentRequest;
import com.tongji.cst3492.jentor.service.WorkspaceIndexService;
import com.tongji.cst3492.jentor.settings.TaConfigurable;
import com.tongji.cst3492.jentor.settings.TaSettings;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.BorderLayout;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.RenderingHints;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.InputMethodEvent;
import java.awt.event.InputMethodListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ChatPanel extends JBPanel<ChatPanel> implements Disposable {
    private static final Logger LOG = Logger.getInstance(ChatPanel.class);
    private static final int MAX_BODY_WIDTH = 760;
    private static final int BUBBLE_HORIZONTAL_PADDING = 10;
    private static final double USER_WIDTH_RATIO = 0.75;
    private static final int STREAM_FLUSH_MS = 80;

    private static final JBColor USER_BUBBLE =
            new JBColor(new Color(0xE8F0FE), new Color(0x2B3A55));
    private static final JBColor ASSISTANT_BUBBLE =
            new JBColor(new Color(0xF4F4F4), new Color(0x2B2D30));
    private static final JBColor ERROR_BUBBLE =
            new JBColor(new Color(0xFDECEA), new Color(0x4A2B2B));
    private static final JBColor USER_ROLE =
            new JBColor(new Color(0x1A73E8), new Color(0x8AB4F8));
    private static final JBColor ASSISTANT_ROLE =
            new JBColor(new Color(0x5F6368), new Color(0x9AA0A6));

    private final Project project;
    private final CourseTaService service;
    private final ConversationStore conversationStore;
    private final ExecutorService conversationExecutor;
    private final ConversationHistoryPanel historyPanel;
    private final JPanel transcriptPanel = new JPanel();
    private final JBScrollPane transcriptScroll = new JBScrollPane(transcriptPanel);
    private final JBTextArea input = new JBTextArea();
    private final ComposerSendButton sendButton = new ComposerSendButton();
    private final JButton settingsButton = new JButton();
    private final JButton historyButton = new JButton();
    private final JButton newConversationButton = new JButton();
    private final JButton newReplyButton = new JButton();
    private final JBLabel conversationTitleLabel = new JBLabel();
    private final JBLabel progressLabel = new JBLabel();
    private final JCheckBox workspaceBox = new JCheckBox();
    private final JComboBox<String> languageBox =
            new JComboBox<>(new String[]{"中文", "English"});
    private final ComposerModeButton modeButton = new ComposerModeButton();
    private final JPopupMenu modeMenu = new JPopupMenu();
    private final JMenuItem mockModeItem = new JMenuItem("Mock");
    private final JMenuItem onlineModeItem = new JMenuItem();
    private final JPanel attachmentBar = new JPanel(new BorderLayout(6, 0));
    private final JBLabel attachmentLabel = new JBLabel();
    private final JButton viewAttachmentButton = new JButton();
    private final JButton cancelEditButton = new JButton();
    private final JButton removeAttachmentButton = new JButton();
    private final Timer settingsTimer;
    private final Timer reflowTimer;
    private final Timer streamTimer;

    private final List<ChatMessage> history = new ArrayList<>();
    private final StringBuilder streamedText = new StringBuilder();
    private final List<MessageLayout> messageBodies = new ArrayList<>();
    private final List<JBTextArea> footnotes = new ArrayList<>();
    private CodeContext pendingCodeContext;
    private String pendingEditTargetPath;
    private boolean pendingCodeEditRequested;
    private final List<CodeFileSnapshot> pendingFiles = new ArrayList<>();
    private ChatRequest currentRequest;
    private EditBatchRequest currentEditRequest;
    private IntentRequest currentIntentRequest;
    private volatile StreamState streamState;
    private AssistantBubble streamCard;
    private MessageView welcomeMessage;
    private String lastAssistantTextForIntent = "";
    private String lastEditStatus = "";
    private CodeContext lastAnalysisContext = CodeContext.empty();
    private Conversation currentConversation;
    private String displayedLanguage;
    private boolean syncingLanguageSelection;
    private boolean busy;
    private int requestGeneration;
    private int historyLoadGeneration;
    private boolean imeComposing;
    private volatile boolean disposed;

    public ChatPanel(Project project) {
        super(new BorderLayout(0, 8));
        this.project = project;
        CourseTaService resolvedService = project.getService(CourseTaService.class);
        if (resolvedService == null) {
            resolvedService = new CourseTaService(project);
        }
        this.service = resolvedService;
        this.conversationStore = ConversationStore.forProject(project);
        this.conversationExecutor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "Jentor conversation store");
            thread.setDaemon(true);
            return thread;
        });
        this.historyPanel = new ConversationHistoryPanel(
                new ConversationHistoryPanel.Listener() {
                    @Override
                    public void open(ConversationSummary summary) {
                        openConversation(summary);
                    }

                    @Override
                    public void rename(ConversationSummary summary) {
                        renameConversation(summary);
                    }

                    @Override
                    public void delete(ConversationSummary summary) {
                        deleteConversation(summary);
                    }
                });
        setBorder(JBUI.Borders.empty(8));

        transcriptPanel.setLayout(new BoxLayout(transcriptPanel, BoxLayout.Y_AXIS));
        transcriptPanel.setOpaque(false);
        transcriptPanel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        transcriptScroll.setBorder(JBUI.Borders.empty());
        transcriptScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        transcriptScroll.getVerticalScrollBar().setUnitIncrement(16);

        JPanel chatColumn = new JPanel(new BorderLayout(0, 8));
        chatColumn.setOpaque(false);
        chatColumn.add(createTopPanel(), BorderLayout.NORTH);
        chatColumn.add(createCenterPanel(), BorderLayout.CENTER);
        chatColumn.add(createBottomPanel(), BorderLayout.SOUTH);

        JPanel workspace = new JPanel(new BorderLayout(6, 0));
        workspace.setOpaque(false);
        historyPanel.setVisible(false);
        workspace.add(historyPanel, BorderLayout.WEST);
        workspace.add(chatColumn, BorderLayout.CENTER);
        add(workspace, BorderLayout.CENTER);

        appendWelcome();
        updateLanguageUi();
        settingsTimer = new Timer(2000, event -> syncSettingsUi());
        settingsTimer.start();
        reflowTimer = new Timer(150, event -> reflowMessages());
        reflowTimer.setRepeats(false);
        streamTimer = new Timer(STREAM_FLUSH_MS, event -> flushStream());
        streamTimer.setRepeats(true);
        streamTimer.setCoalesce(true);
        transcriptScroll.getViewport().addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent event) {
                reflowTimer.restart();
            }
        });
        SwingUtilities.invokeLater(this::reflowMessages);

        CodeContext pending = service.consumePendingContext();
        if (pending != null) {
            setPendingContext(pending);
        }
        List<CodeFileSnapshot> queuedFiles = service.consumePendingFiles();
        if (!queuedFiles.isEmpty()) {
            addFiles(queuedFiles);
        }
        loadInitialConversationAsync(pending == null && queuedFiles.isEmpty());
    }

    public void setPendingContext(CodeContext context) {
        if (context == null || !context.hasSelection()) {
            return;
        }
        pendingCodeContext = context;
        pendingEditTargetPath = null;
        pendingCodeEditRequested = false;
        refreshAttachmentBar();
        updateLanguageUi();
        input.requestFocusInWindow();
    }

    /** Activates the F2 code-edit flow and optionally attaches the selection. */
    public void prepareCodeEdit(CodeContext context, String targetPath) {
        pendingCodeContext =
                context != null && context.hasSelection() ? context : null;
        pendingEditTargetPath = targetPath;
        pendingCodeEditRequested = true;
        refreshAttachmentBar();
        setProgressText(I18n.t(currentLanguage(), "editPrompt"));
        input.requestFocusInWindow();
    }

    /**
     * Attaches workspace file snapshots. The snapshots were read once, when they
     * were attached; the panel never re-reads the files, so later edits cannot
     * silently change the context of an already attached conversation.
     */
    public void addFiles(List<CodeFileSnapshot> files) {
        if (files == null || files.isEmpty()) {
            return;
        }
        for (CodeFileSnapshot file : files) {
            if (file == null || file.isEmpty() || pendingFiles.size() >= CodeFilePolicy.MAX_FILES) {
                continue;
            }
            boolean known = pendingFiles.stream()
                    .anyMatch(existing -> existing.path().equals(file.path()));
            if (!known) {
                pendingFiles.add(file);
            }
        }
        refreshAttachmentBar();
        input.requestFocusInWindow();
    }

    private String currentLanguage() {
        String language = TaSettings.getInstance().getState().language;
        return I18n.EN.equals(language) ? I18n.EN : I18n.ZH;
    }

    private JPanel createTopPanel() {
        JPanel labels = new JPanel();
        labels.setLayout(new BoxLayout(labels, BoxLayout.Y_AXIS));
        labels.setOpaque(false);
        conversationTitleLabel.setFont(
                conversationTitleLabel.getFont().deriveFont(Font.BOLD, 12f));
        conversationTitleLabel.setText(I18n.t(currentLanguage(), "newConversation"));
        progressLabel.setForeground(JBColor.GRAY);
        progressLabel.setVisible(false);
        labels.add(conversationTitleLabel);
        labels.add(progressLabel);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        controls.setOpaque(false);
        languageBox.setSelectedIndex("zh".equals(
                TaSettings.getInstance().getState().language) ? 0 : 1);
        languageBox.setFont(languageBox.getFont().deriveFont(11f));
        languageBox.setPreferredSize(new Dimension(88, 26));
        languageBox.setMaximumRowCount(2);
        languageBox.addActionListener(event -> {
            if (syncingLanguageSelection) {
                return;
            }
            TaSettings.State state = TaSettings.getInstance().getState();
            state.language = languageBox.getSelectedIndex() == 0 ? "zh" : "en";
            updateLanguageUi();
        });
        configureButton(settingsButton, event -> showSettings());
        configureButton(historyButton, event -> toggleHistory());
        configureButton(newConversationButton, event -> startNewConversation());

        controls.add(historyButton);
        controls.add(newConversationButton);
        controls.add(languageBox);
        controls.add(settingsButton);

        JPanel top = new JPanel(new BorderLayout(0, 4));
        top.setOpaque(false);
        top.add(labels, BorderLayout.NORTH);
        top.add(controls, BorderLayout.SOUTH);
        return top;
    }

    private void configureButton(JButton button, java.awt.event.ActionListener listener) {
        button.setFont(button.getFont().deriveFont(11f));
        button.setMargin(new Insets(2, 6, 2, 6));
        button.addActionListener(listener);
    }

    private void configureWorkspaceToggle() {
        workspaceBox.setFont(workspaceBox.getFont().deriveFont(11f));
        workspaceBox.setOpaque(false);
        workspaceBox.setSelected(TaSettings.getInstance().getState().workspaceContext);
        updateWorkspaceTooltip();
        workspaceBox.addActionListener(event -> {
            TaSettings.State state = TaSettings.getInstance().getState();
            state.workspaceContext = workspaceBox.isSelected();
            if (workspaceBox.isSelected()) {
                WorkspaceIndexService indexService =
                        project.getService(WorkspaceIndexService.class);
                if (indexService != null) {
                    indexService.ensureBuiltAsync();
                }
            }
            input.requestFocusInWindow();
        });
    }

    private void configureModeMenu() {
        modeButton.setFont(modeButton.getFont().deriveFont(11f));
        modeButton.setIcon(AllIcons.General.ArrowDown);
        modeButton.setIconTextGap(4);
        modeButton.setBorder(JBUI.Borders.empty(4, 8));
        modeButton.addActionListener(event -> {
            updateModeSelector();
            modeMenu.show(modeButton, 0, -modeMenu.getPreferredSize().height);
        });
        mockModeItem.addActionListener(event -> selectMode(true));
        onlineModeItem.addActionListener(event -> selectMode(false));
        modeMenu.add(mockModeItem);
        modeMenu.add(onlineModeItem);
    }

    private JPanel createCenterPanel() {
        JPanel center = new JPanel(new BorderLayout());
        center.setOpaque(false);
        center.add(transcriptScroll, BorderLayout.CENTER);
        newReplyButton.setFont(newReplyButton.getFont().deriveFont(11f));
        newReplyButton.setVisible(false);
        newReplyButton.addActionListener(event -> scrollToBottom());
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 2));
        bottom.setOpaque(false);
        bottom.add(newReplyButton);
        center.add(bottom, BorderLayout.SOUTH);
        transcriptScroll.getVerticalScrollBar().addAdjustmentListener(event -> {
            if (isAtBottom()) {
                newReplyButton.setVisible(false);
            }
        });
        return center;
    }

    private JPanel createBottomPanel() {
        configureWorkspaceToggle();
        attachmentBar.setOpaque(false);
        attachmentBar.setVisible(false);
        attachmentLabel.setForeground(JBColor.GRAY);
        JPanel attachmentButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        attachmentButtons.setOpaque(false);
        configureButton(viewAttachmentButton, event -> viewAttachment());
        configureButton(cancelEditButton, event -> cancelEditPreset());
        configureButton(removeAttachmentButton, event -> removeAttachment());
        attachmentButtons.add(viewAttachmentButton);
        attachmentButtons.add(cancelEditButton);
        attachmentButtons.add(removeAttachmentButton);
        attachmentBar.add(attachmentLabel, BorderLayout.CENTER);
        attachmentBar.add(attachmentButtons, BorderLayout.EAST);

        input.setRows(3);
        input.setColumns(48);
        input.setLineWrap(true);
        input.setWrapStyleWord(true);
        Font labelFont = UIManager.getFont("Label.font");
        input.setFont(labelFont != null
                ? labelFont.deriveFont(13f)
                : new Font("SansSerif", Font.PLAIN, 13));
        input.setMargin(new Insets(4, 6, 4, 6));
        TextContextMenu.install(input);
        input.addInputMethodListener(new InputMethodListener() {
            @Override
            public void inputMethodTextChanged(InputMethodEvent event) {
                imeComposing = InputMethodComposition.hasUncommittedText(
                        event.getText(), event.getCommittedCharacterCount());
            }

            @Override
            public void caretPositionChanged(InputMethodEvent event) {
                // Composition state is updated by inputMethodTextChanged.
            }
        });
        input.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent event) {
                imeComposing = false;
            }
        });
        input.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent event) {
                if (event.isConsumed()) {
                    return;
                }
                if (event.getKeyCode() == KeyEvent.VK_ENTER
                        && !event.isShiftDown()
                        && !event.isControlDown()
                        && !event.isAltDown()
                        && !event.isMetaDown()
                        && !imeComposing) {
                    event.consume();
                    if (!busy) {
                        send();
                    }
                }
            }
        });

        sendButton.addActionListener(event -> {
            if (busy) {
                stopGeneration();
            } else {
                send();
            }
        });
        sendButton.setFocusPainted(false);
        sendButton.setContentAreaFilled(false);
        sendButton.setBorder(JBUI.Borders.empty(5));
        sendButton.setPreferredSize(JBUI.size(30));
        input.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                updateInputRows();
                updateSendButton();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                updateInputRows();
                updateSendButton();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                updateInputRows();
                updateSendButton();
            }
        });

        configureModeMenu();
        updateModeSelector();

        JPanel composerFooter = new JPanel(new ComposerFooterLayout(
                workspaceBox, modeButton, sendButton, JBUI.scale(6)));
        composerFooter.setOpaque(false);
        composerFooter.add(workspaceBox);
        composerFooter.add(modeButton);
        composerFooter.add(sendButton);

        ComposerPanel inputPanel = new ComposerPanel();
        JBScrollPane inputScroll = new JBScrollPane(input);
        inputScroll.setBorder(JBUI.Borders.empty());
        inputScroll.setOpaque(false);
        inputScroll.getViewport().setOpaque(false);
        inputScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        input.setOpaque(false);
        inputPanel.add(inputScroll, BorderLayout.CENTER);
        inputPanel.add(composerFooter, BorderLayout.SOUTH);

        JPanel bottom = new JPanel(new BorderLayout(0, 4));
        bottom.setOpaque(false);
        bottom.add(attachmentBar, BorderLayout.NORTH);
        bottom.add(inputPanel, BorderLayout.CENTER);
        updateSendButton();
        return bottom;
    }

    private void updateInputRows() {
        int rows = Math.max(3, Math.min(6, input.getLineCount()));
        if (input.getRows() != rows) {
            input.setRows(rows);
            input.revalidate();
        }
    }

    /** Plans and generates all file proposals, then opens one review window. */
    private void sendEdit(
            ConversationTurn turn,
            String instruction,
            String forcedTarget,
            String assistantAdvice,
            List<String> targetHints) {
        pendingEditTargetPath = null;
        pendingCodeEditRequested = false;
        busy = true;
        int generation = ++requestGeneration;
        updateSendButton();
        setProgressText(I18n.t(currentLanguage(), "editGenerating"));
        EditorTarget editor = selectedEditorTarget();
        String activeFile = editor == null ? "" : editor.path();
        CodeContext previousAnalysisContext = assistantAdvice == null
                || assistantAdvice.isBlank()
                ? CodeContext.empty() : lastAnalysisContext;
        EditBatchRequest request;
        try {
            request = service.prepareEditBatch(
                    instruction,
                    assistantAdvice == null ? "" : assistantAdvice,
                    forcedTarget == null ? "" : forcedTarget,
                    targetHints == null ? List.of() : targetHints,
                    activeFile,
                    pendingContextSnapshot(),
                    previousAnalysisContext,
                    currentLanguage());
        } catch (RuntimeException error) {
            busy = false;
            setProgressText("");
            updateSendButton();
            appendError(
                    friendlyError(error),
                    currentLanguage(),
                    () -> sendEdit(
                            turn,
                            instruction,
                            forcedTarget == null ? "" : forcedTarget,
                            assistantAdvice == null ? "" : assistantAdvice,
                            targetHints == null ? List.of() : targetHints));
            return;
        }
        currentEditRequest = request;
        request.future().whenComplete((result, error) ->
                ApplicationManager.getApplication().invokeLater(() -> {
                    if (generation != requestGeneration
                            || currentEditRequest != request
                            || request.isCancelled()) {
                        return;
                    }
                    currentEditRequest = null;
                    if (error != null) {
                        busy = false;
                        setProgressText("");
                        updateSendButton();
                        appendError(
                                friendlyError(error),
                                currentLanguage(),
                                () -> sendEdit(
                                        turn,
                                        instruction,
                                        forcedTarget == null ? "" : forcedTarget,
                                        assistantAdvice == null ? "" : assistantAdvice,
                                        targetHints == null ? List.of() : targetHints));
                        return;
                    }
                    if (result.hasProposals()) {
                        busy = false;
                        setProgressText("");
                        updateSendButton();
                        openEditBatchReview(result, turn, instruction, generation);
                        return;
                    }
                    busy = false;
                    setProgressText("");
                    updateSendButton();
                    String message = result.error().isBlank()
                            ? I18n.t(currentLanguage(), "editNoChange") : result.error();
                    completeLocalTurn(turn, message, true);
                }));
    }

    private void openEditBatchReview(
            EditBatchResult batch, ConversationTurn turn, String instruction, int generation) {
        EditBatchReviewDialog dialog = new EditBatchReviewDialog(project, batch);
        boolean accepted = dialog.showAndGet();
        if (disposed || generation != requestGeneration) {
            return;
        }
        if (accepted && dialog.applyResult() != null) {
            if (dialog.applyResult().status() == EditBatchApplyResult.Status.APPLIED) {
                String summary = I18n.t(currentLanguage(), "editApplied") + "\n"
                        + dialog.applyResult().appliedPaths().stream()
                        .map(path -> "- " + path).reduce("", (left, right) ->
                                left + (left.isBlank() ? "" : "\n") + right)
                        + "\n- " + instruction;
                completeLocalTurn(turn, summary, true);
            } else if (dialog.applyResult().status()
                    == EditBatchApplyResult.Status.NO_CHANGE) {
                completeLocalTurn(turn, I18n.t(currentLanguage(), "editNoChange"), true);
            }
        } else if (!accepted) {
            completeLocalTurn(turn, I18n.t(currentLanguage(), "editCancelled"), true);
        }
    }

    /** Active editor file path and display name, or {@code null}. */
    private EditorTarget selectedEditorTarget() {
        VirtualFile[] files = FileEditorManager.getInstance(project).getSelectedFiles();
        for (VirtualFile file : files) {
            if (file == null || file.isDirectory() || file.getFileType().isBinary()) {
                continue;
            }
            String path = VfsUtilCore.getRelativePath(
                    file, ProjectUtil.guessProjectDir(project), '/');
            if (path != null && !path.isBlank()) {
                return new EditorTarget(path);
            }
        }
        return null;
    }

    private static String fileName(String path) {
        int slash = path == null ? -1 : path.lastIndexOf('/');
        return path == null || path.isBlank()
                ? "" : slash < 0 ? path : path.substring(slash + 1);
    }

    private void send() {
        if (busy) {
            return;
        }
        String question = input.getText().trim();
        if (question.isEmpty()) {
            return;
        }
        historyLoadGeneration++;
        ConversationTurn turn = new ConversationTurn(
                question, attachmentSummary(), System.currentTimeMillis());
        appendMessage("user", question, false, true, turn.attachmentSummary());
        input.setText("");
        if (pendingCodeEditRequested) {
            EditorTarget editor = selectedEditorTarget();
            String forcedTarget = EditGuard.normalizeRelativePath(pendingEditTargetPath);
            if (forcedTarget.isBlank() && editor != null) {
                forcedTarget = EditGuard.normalizeRelativePath(editor.path());
            }
            if (!forcedTarget.isBlank()) {
                sendEdit(turn, question, forcedTarget, "", List.of());
                return;
            }
            pendingCodeEditRequested = false;
            pendingEditTargetPath = null;
            completeLocalTurn(turn, I18n.t(currentLanguage(), "editNoTarget"), false);
            return;
        }
        startIntentRouting(turn);
    }

    private void startIntentRouting(ConversationTurn turn) {
        String question = turn.question();
        busy = true;
        int generation = ++requestGeneration;
        updateSendButton();
        setProgressText(I18n.t(currentLanguage(), "routingRequest"));
        EditorTarget editor = selectedEditorTarget();
        String activePath = editor == null ? "" : editor.path();
        String activeSnippet = pendingCodeContext != null && pendingCodeContext.hasSelection()
                ? pendingCodeContext.selected() : "";
        WorkspaceIndexService indexService = project.getService(WorkspaceIndexService.class);
        WorkspaceIndex index = indexService == null ? null : indexService.getIndex();
        List<String> workspaceFiles = index == null
                ? List.of()
                : index.files().stream().map(file -> file.path()).limit(80).toList();
        IntentRequest request;
        try {
            request = service.routeIntent(
                    question,
                    routingHistory(),
                    lastEditStatus,
                    activePath,
                    activeSnippet,
                    workspaceFiles,
                    currentLanguage());
        } catch (RuntimeException error) {
            busy = false;
            setProgressText("");
            updateSendButton();
            appendError(
                    friendlyError(error),
                    currentLanguage(),
                    () -> startIntentRouting(turn));
            return;
        }
        currentIntentRequest = request;
        request.future().whenComplete((decision, error) ->
                ApplicationManager.getApplication().invokeLater(() -> {
                    if (generation != requestGeneration
                            || currentIntentRequest != request
                            || request.isCancelled()) {
                        return;
                    }
                    currentIntentRequest = null;
                    if (error != null) {
                        busy = false;
                        setProgressText("");
                        updateSendButton();
                        appendError(
                                friendlyError(error),
                                currentLanguage(),
                                () -> startIntentRouting(turn));
                        return;
                    }
                    handleIntent(turn, decision);
                }));
    }

    private List<ChatMessage> routingHistory() {
        List<ChatMessage> routeHistory = new ArrayList<>(history);
        if (!lastAssistantTextForIntent.isBlank()) {
            boolean represented = !routeHistory.isEmpty()
                    && "assistant".equals(routeHistory.get(routeHistory.size() - 1).role())
                    && lastAssistantTextForIntent.equals(
                            routeHistory.get(routeHistory.size() - 1).content());
            if (!represented) {
                routeHistory.add(new ChatMessage("assistant", lastAssistantTextForIntent));
            }
        }
        return routeHistory;
    }

    private void handleIntent(ConversationTurn turn, RouteDecision decision) {
        String question = turn.question();
        if (decision.intent() == ConversationIntent.EDIT) {
            String advice = decision.usePreviousAnalysis()
                    ? lastAssistantTextForIntent : "";
            String instruction = decision.instruction().isBlank()
                    ? question : decision.instruction();
            sendEdit(turn, instruction, "", advice, decision.targetHints());
            return;
        }
        if (decision.intent() == ConversationIntent.ANALYZE) {
            submitChat(turn, true, true);
            return;
        }
        if (decision.intent() == ConversationIntent.STATUS) {
            busy = false;
            setProgressText("");
            updateSendButton();
            String message = lastEditStatus.isBlank()
                    ? I18n.t(currentLanguage(), "editStatusNone")
                    : lastEditStatus;
            completeLocalTurn(turn, message, false);
            return;
        }
        if (decision.intent() == ConversationIntent.CLARIFY) {
            busy = false;
            setProgressText("");
            updateSendButton();
            String message = decision.clarification().isBlank()
                    ? I18n.t(currentLanguage(), "intentClarify")
                    : decision.clarification();
            completeLocalTurn(turn, message, false);
            return;
        }
        submitChat(turn, TaSettings.getInstance().getState().workspaceContext, false);
    }

    private void completeLocalTurn(ConversationTurn turn, String message, boolean editResult) {
        appendMessage("assistant", message, false, false, "");
        lastAssistantTextForIntent = message;
        if (editResult) {
            lastEditStatus = message;
        }
        long answeredAt = System.currentTimeMillis();
        recordCompletedTurn(turn, editResult
                ? ConversationMessage.editResult(message, answeredAt)
                : ConversationMessage.assistant(message, answeredAt));
    }

    private void submitChat(ConversationTurn turn, boolean includeWorkspace, boolean analyze) {
        String question = turn.question();
        CodeContext codeContext = pendingContextSnapshot();
        List<ChatMessage> historySnapshot = new ArrayList<>(history);
        EditorTarget currentEditor = selectedEditorTarget();
        String activeFilePath = currentEditor == null ? "" : currentEditor.path();

        busy = true;
        int generation = ++requestGeneration;
        updateSendButton();
        setProgressText(I18n.t(currentLanguage(), "thinking"));
        streamState = new StreamState(generation);
        streamCard = null;
        streamedText.setLength(0);
        streamTimer.restart();

        ChatRequest request;
        try {
            String promptQuestion = analyze
                    ? question + "\n\nAnalyze the relevant workspace code and identify concrete "
                    + "problems with file and method details. Do not claim that code was modified."
                    : question;
            request = service.ask(promptQuestion, codeContext, historySnapshot, stage ->
                    ApplicationManager.getApplication().invokeLater(() -> {
                        if (generation == requestGeneration && busy) {
                            setProgressText(stage);
                        }
                    }), createSink(generation), includeWorkspace, activeFilePath);
        } catch (RuntimeException error) {
            streamTimer.stop();
            streamState = null;
            busy = false;
            input.setText(question);
            setProgressText("");
            updateSendButton();
            appendError(
                    friendlyError(error),
                    currentLanguage(),
                    () -> retryChat(turn, includeWorkspace, analyze));
            return;
        }
        currentRequest = request;
        request.future().whenComplete((answer, error) ->
                ApplicationManager.getApplication().invokeLater(() -> {
                    if (generation != requestGeneration
                            || currentRequest != request
                            || request.isCancelled()) {
                        return;
                    }
                    currentRequest = null;
                    busy = false;
                    setProgressText("");
                    flushStream();
                    streamTimer.stop();
                    streamState = null;
                    if (error == null) {
                        finishStreamCard(answer);
                        lastAssistantTextForIntent = answer.text();
                        if (analyze) {
                            lastAnalysisContext = request.effectiveContext().getNow(
                                    CodeContext.empty());
                        }
                        if (answer.citationStatus() != CitationStatus.FAILED) {
                            recordCompletedTurn(
                                    turn, ConversationMessage.assistant(
                                            answer, System.currentTimeMillis()));
                        }
                    } else {
                        discardStreamCard();
                        input.setText(question);
                        lastAssistantTextForIntent = "";
                        appendError(
                                friendlyError(error),
                                currentLanguage(),
                                () -> retryChat(turn, includeWorkspace, analyze));
                    }
                    updateSendButton();
                    input.requestFocusInWindow();
                }));
    }

    private void stopGeneration() {
        requestGeneration++;
        if (currentRequest != null) {
            currentRequest.cancel();
            currentRequest = null;
        }
        cancelCurrentIntentRequest();
        cancelCurrentEditRequest();
        flushStream();
        discardStreamCard();
        streamTimer.stop();
        streamState = null;
        streamedText.setLength(0);
        busy = false;
        setProgressText(I18n.t(currentLanguage(), "stopped"));
        updateSendButton();
    }

    private void retryChat(
            ConversationTurn turn, boolean includeWorkspace, boolean analyze) {
        String question = turn.question();
        if (question.equals(input.getText().trim())) {
            input.setText("");
        }
        submitChat(turn, includeWorkspace, analyze);
    }

    private void updateSendButton() {
        String language = currentLanguage();
        String action = I18n.t(language, busy ? "stop" : "send");
        sendButton.setStopping(busy);
        sendButton.setText("");
        sendButton.setToolTipText(action);
        sendButton.getAccessibleContext().setAccessibleName(action);
        sendButton.setEnabled(busy || !input.getText().trim().isEmpty());
    }

    private void rememberTurn(String question, String answer) {
        history.add(ChatMessage.user(question));
        history.add(new ChatMessage("assistant", answer));
        while (history.size() > 8) {
            history.remove(0);
        }
    }

    private void appendAnswer(Answer answer) {
        AssistantBubble bubble = newAssistantBubble(answer.text());
        populateAnswerDetails(bubble.card, answer);
        addRow(bubble.card, false);
    }

    private AssistantBubble newAssistantBubble(String text) {
        JPanel card = new ContentSizedPanel(null);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setOpaque(false);
        card.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));

        BubblePanel bubble = new BubblePanel(ASSISTANT_BUBBLE);
        JBLabel role = new JBLabel(I18n.t(currentLanguage(), "courseTa"));
        role.setFont(role.getFont().deriveFont(Font.BOLD, 11f));
        role.setForeground(ASSISTANT_ROLE);
        bubble.add(role, BorderLayout.NORTH);
        MessageBody body = createMessageBody(text, false);
        bubble.add(body, BorderLayout.CENTER);
        card.add(bubble);
        return new AssistantBubble(card, body);
    }

    private void populateAnswerDetails(JPanel card, Answer answer) {
        int width = bodyWidth();
        boolean mixedEvidence = answer.hasMixedEvidence();
        JPanel meta = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        meta.setOpaque(false);
        meta.setAlignmentX(LEFT_ALIGNMENT);
        if (answer.citationStatus() != CitationStatus.NOT_APPLICABLE) {
            JBLabel status = new JBLabel(mixedEvidence
                    ? I18n.t(currentLanguage(), "citationsMixed")
                    : citationStatusText(answer.citationStatus()));
            status.setFont(status.getFont().deriveFont(11f));
            status.setForeground(answer.citationStatus() == CitationStatus.FAILED
                    ? JBColor.RED
                    : JBColor.GRAY);
            meta.add(status);
        }

        JPanel sourcesPanel = new JPanel();
        sourcesPanel.setLayout(new BoxLayout(sourcesPanel, BoxLayout.Y_AXIS));
        sourcesPanel.setOpaque(false);
        sourcesPanel.setAlignmentX(LEFT_ALIGNMENT);
        sourcesPanel.setVisible(false);
        for (SourceReference source : answer.sources()) {
            JButton item = new JButton(source.fileName() + " " + source.locator());
            item.setFont(item.getFont().deriveFont(11f));
            item.setHorizontalAlignment(JButton.LEFT);
            item.setAlignmentX(LEFT_ALIGNMENT);
            item.addActionListener(event -> showSource(source));
            sourcesPanel.add(item);
        }
        if (!answer.sources().isEmpty()) {
            JButton toggle = new JButton(I18n.t(currentLanguage(), "sourcesCount")
                    + " (" + answer.sources().size() + ")");
            toggle.setFont(toggle.getFont().deriveFont(11f));
            toggle.addActionListener(event -> {
                sourcesPanel.setVisible(!sourcesPanel.isVisible());
                card.revalidate();
                card.repaint();
            });
            meta.add(toggle);
        }

        if (meta.getComponentCount() > 0) {
            card.add(meta);
        }
        card.add(sourcesPanel);
        if (!answer.disclosure().isBlank() && !mixedEvidence) {
            card.add(createFootnote(answer.disclosure(), width));
        }
        if (!answer.contextNote().isBlank()) {
            card.add(createFootnote(answer.contextNote(), width));
        }
        if (!answer.warnings().isEmpty()) {
            JBLabel warning = new JBLabel(I18n.t(currentLanguage(), "warning")
                    + String.join("; ", answer.warnings()));
            warning.setFont(warning.getFont().deriveFont(11f));
            warning.setForeground(JBColor.RED);
            warning.setAlignmentX(LEFT_ALIGNMENT);
            card.add(warning);
        }
    }

    private StreamSink createSink(int generation) {
        return new StreamSink() {
            @Override
            public void append(String delta) {
                StreamState state = streamState;
                if (state == null || state.generation != generation
                        || delta == null || delta.isEmpty()) {
                    return;
                }
                synchronized (state.pending) {
                    state.pending.append(delta);
                }
            }

            @Override
            public void reset() {
                StreamState state = streamState;
                if (state == null || state.generation != generation) {
                    return;
                }
                synchronized (state.pending) {
                    state.pending.setLength(0);
                }
                state.resetRequested = true;
            }
        };
    }

    /** Drains buffered deltas on the EDT and repaints the streaming card. */
    private void flushStream() {
        StreamState state = streamState;
        if (state == null) {
            return;
        }
        String delta;
        boolean reset;
        synchronized (state.pending) {
            reset = state.resetRequested;
            state.resetRequested = false;
            delta = state.pending.toString();
            state.pending.setLength(0);
        }
        if (!reset && delta.isEmpty()) {
            return;
        }
        boolean pinned = isAtBottom();
        if (reset) {
            streamedText.setLength(0);
            if (streamCard != null) {
                streamCard.body.setPreviewText("");
            }
        }
        if (!delta.isEmpty()) {
            if (streamCard == null) {
                streamCard = newAssistantBubble("");
                addRow(streamCard.card, false);
            }
            streamedText.append(delta);
            streamCard.body.setPreviewText(streamedText.toString());
            streamCard.card.revalidate();
            streamCard.card.repaint();
            setProgressText("");
        }
        if (pinned) {
            scrollToBottom();
        }
    }

    /** Replaces the streamed preview with the authoritative final answer. */
    private void finishStreamCard(Answer answer) {
        AssistantBubble card = streamCard;
        if (card == null) {
            appendAnswer(answer);
            return;
        }
        streamCard = null;
        card.body.setText(answer.text());
        populateAnswerDetails(card.card, answer);
        card.card.revalidate();
        card.card.repaint();
        if (isAtBottom()) {
            scrollToBottom();
        }
    }

    /** Drops a partially streamed answer, for example after a failed request. */
    private void discardStreamCard() {
        AssistantBubble card = streamCard;
        if (card == null) {
            return;
        }
        streamCard = null;
        messageBodies.removeIf(message -> message.body() == card.body);
        transcriptPanel.remove(card.card);
        transcriptPanel.revalidate();
        transcriptPanel.repaint();
    }

    private String citationStatusText(CitationStatus status) {
        return switch (status) {
            case VALIDATED -> I18n.t(currentLanguage(), "citationsValidated");
            case FAILED -> I18n.t(currentLanguage(), "citationsFailed");
            case NOT_APPLICABLE -> I18n.t(currentLanguage(), "lowCoverage");
        };
    }

    private void showSource(SourceReference source) {
        CourseDocumentSource managed = service.getCourseLibraryService()
                .sourceForDocumentId(source.docId());
        new SourcePreviewDialog(project, source, managed).show();
    }

    private MessageView appendMessage(String role, String text, boolean warning, boolean userMessage) {
        return appendMessage(role, text, warning, userMessage, "");
    }

    private MessageView appendMessage(
            String role,
            String text,
            boolean warning,
            boolean userMessage,
            String attachmentSummary) {
        boolean user = "user".equals(role);
        boolean error = "error".equals(role);
        JPanel row = new ContentSizedPanel(new BorderLayout());
        row.setOpaque(false);
        row.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
        BubblePanel bubble = new BubblePanel(
                error ? ERROR_BUBBLE : user ? USER_BUBBLE : ASSISTANT_BUBBLE);
        JBLabel roleLabel = new JBLabel(
                user ? I18n.t(currentLanguage(), "you")
                        : error ? I18n.t(currentLanguage(), "error")
                        : I18n.t(currentLanguage(), "courseTa"));
        roleLabel.setFont(roleLabel.getFont().deriveFont(Font.BOLD, 11f));
        roleLabel.setForeground(user ? USER_ROLE : error ? JBColor.RED : ASSISTANT_ROLE);
        bubble.add(roleLabel, BorderLayout.NORTH);
        MessageBody body = createMessageBody(text, user);
        bubble.add(body, BorderLayout.CENTER);
        if (attachmentSummary != null && !attachmentSummary.isBlank()) {
            JBLabel attachment = new JBLabel(attachmentSummary);
            attachment.setFont(attachment.getFont().deriveFont(10f));
            attachment.setForeground(JBColor.GRAY);
            bubble.add(attachment, BorderLayout.SOUTH);
        }
        row.add(bubble, user ? BorderLayout.EAST : BorderLayout.WEST);
        addRow(row, userMessage);
        return new MessageView(roleLabel, body);
    }

    private void appendError(String message, String language, Runnable retryAction) {
        String normalized = message.toLowerCase(Locale.ROOT);
        boolean settings = normalized.contains("api key")
                || normalized.contains("base url")
                || normalized.contains("chat model")
                || message.contains("密钥")
                || message.contains("接口地址")
                || message.contains("模型未配置");
        boolean index = normalized.startsWith("index error:")
                || message.startsWith("索引错误：")
                || normalized.contains("course index")
                || message.contains("课程索引");
        String actionLabel = settings ? I18n.t(language, "openSettings")
                : index ? I18n.t(language, "importIndexAction")
                : I18n.t(language, "retry");
        Runnable action;
        if (settings) {
            action = this::showSettings;
        } else if (index) {
            action = this::showCourseLibrary;
        } else {
            action = retryAction;
        }

        JPanel row = new ContentSizedPanel(new BorderLayout());
        row.setOpaque(false);
        row.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
        BubblePanel bubble = new BubblePanel(ERROR_BUBBLE);
        JBLabel role = new JBLabel(I18n.t(language, "error"));
        role.setFont(role.getFont().deriveFont(Font.BOLD, 11f));
        role.setForeground(JBColor.RED);
        bubble.add(role, BorderLayout.NORTH);
        bubble.add(createMessageBody(message, false), BorderLayout.CENTER);
        JButton actionButton = new JButton(actionLabel);
        actionButton.setFont(actionButton.getFont().deriveFont(11f));
        actionButton.addActionListener(event -> action.run());
        bubble.add(actionButton, BorderLayout.SOUTH);
        row.add(bubble, BorderLayout.WEST);
        addRow(row, false);
    }

    private int bodyWidth() {
        return bodyWidth(false);
    }

    private int bodyWidth(boolean user) {
        int viewportWidth = transcriptScroll.getViewport().getWidth();
        if (viewportWidth <= 0) {
            viewportWidth = 708;
        }
        Insets insets = transcriptPanel.getInsets();
        int available = Math.max(1, viewportWidth - insets.left - insets.right);
        int bubbleWidth = user ? (int) Math.floor(available * USER_WIDTH_RATIO) : available;
        return Math.max(1, Math.min(MAX_BODY_WIDTH,
                bubbleWidth - 2 * BUBBLE_HORIZONTAL_PADDING));
    }

    private MessageBody createMessageBody(String text, boolean user) {
        int width = bodyWidth(user);
        MessageBody body = new MessageBody(text, project, width);
        if (user) {
            body.setContentWidth(body.preferredContentWidth(width));
        }
        messageBodies.add(new MessageLayout(body, user));
        return body;
    }

    private void resizeFootnote(JBTextArea footnote, int width) {
        // Clear the old explicit height before measuring the newly wrapped text.
        footnote.setPreferredSize(null);
        footnote.setSize(new Dimension(width, Short.MAX_VALUE));
        int height = footnote.getPreferredSize().height;
        footnote.setPreferredSize(new Dimension(width, height));
        footnote.setMaximumSize(new Dimension(width, height));
        footnote.setAlignmentX(LEFT_ALIGNMENT);
    }

    private void reflowMessages() {
        int width = bodyWidth();
        for (MessageLayout message : messageBodies) {
            int limit = bodyWidth(message.user());
            MessageBody body = message.body();
            body.setContentWidth(message.user() ? body.preferredContentWidth(limit) : limit);
        }
        for (JBTextArea footnote : footnotes) {
            resizeFootnote(footnote, width);
        }
        transcriptPanel.revalidate();
        transcriptPanel.repaint();
    }

    private void addRow(java.awt.Component row, boolean forceScroll) {
        boolean atBottom = isAtBottom();
        transcriptPanel.add(row);
        transcriptPanel.revalidate();
        transcriptPanel.repaint();
        if (forceScroll || atBottom) {
            scrollToBottom();
        } else {
            newReplyButton.setVisible(true);
        }
    }

    private boolean isAtBottom() {
        JScrollBar bar = transcriptScroll.getVerticalScrollBar();
        return bar.getValue() + bar.getVisibleAmount() >= bar.getMaximum() - 20;
    }

    private void scrollToBottom() {
        SwingUtilities.invokeLater(() -> {
            JScrollBar bar = transcriptScroll.getVerticalScrollBar();
            bar.setValue(bar.getMaximum());
            newReplyButton.setVisible(false);
        });
    }

    private void appendWelcome() {
        welcomeMessage = appendMessage(
                "assistant", I18n.t(currentLanguage(), "welcome"), false, true);
    }

    private JBTextArea createFootnote(String text, int width) {
        JBTextArea footnote = new JBTextArea(I18n.t(currentLanguage(), "footnotePrefix") + text);
        footnote.setEditable(false);
        footnote.setLineWrap(true);
        footnote.setWrapStyleWord(true);
        footnote.setOpaque(false);
        footnote.setFont(footnote.getFont().deriveFont(Font.ITALIC, 11f));
        footnote.setForeground(JBColor.GRAY);
        footnote.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        resizeFootnote(footnote, width);
        footnotes.add(footnote);
        return footnote;
    }

    private CodeContext pendingContextSnapshot() {
        List<CodeFileSnapshot> files = List.copyOf(pendingFiles);
        CodeContext selection = pendingCodeContext;
        return selection == null ? CodeContext.ofFiles(files) : selection.withFiles(files);
    }

    private String attachmentSummary() {
        String language = currentLanguage();
        StringBuilder label = new StringBuilder();
        if (pendingCodeEditRequested) {
            String target = fileName(pendingEditTargetPath);
            label.append(I18n.t(language, "editPending"));
            if (!target.isBlank()) {
                label.append(target);
            }
        }
        if (pendingCodeContext != null && pendingCodeContext.hasSelection()) {
            if (label.length() > 0) {
                label.append("  |  ");
            }
            label.append(pendingCodeContext.summary());
        }
        if (!pendingFiles.isEmpty()) {
            if (label.length() > 0) {
                label.append("  |  ");
            }
            label.append(pendingFiles.size()).append(' ')
                    .append(I18n.t(language, "attachedFiles")).append(": ")
                    .append(pendingFiles.get(0).fileName());
            if (pendingFiles.size() > 1) {
                label.append(", ...");
            }
        }
        return label.toString();
    }

    private void refreshAttachmentBar() {
        String summary = attachmentSummary();
        boolean visible = !summary.isEmpty();
        boolean hasAttachment = !pendingContextSnapshot().isEmpty();
        attachmentBar.setVisible(visible);
        if (visible) {
            attachmentLabel.setText(summary);
        }
        viewAttachmentButton.setVisible(hasAttachment);
        removeAttachmentButton.setVisible(hasAttachment);
        cancelEditButton.setVisible(pendingCodeEditRequested);
        attachmentBar.revalidate();
        attachmentBar.repaint();
    }

    private void viewAttachment() {
        CodeContext context = pendingContextSnapshot();
        if (context.isEmpty()) {
            return;
        }
        String text = new PromptBuilder().buildIdeContext(context)
                + "\n\n" + I18n.t(currentLanguage(), "contextSnapshot");
        Messages.showInfoMessage(project, text,
                I18n.t(currentLanguage(), "attachmentTitle"));
    }

    private void removeAttachment() {
        pendingCodeContext = null;
        pendingFiles.clear();
        pendingEditTargetPath = null;
        pendingCodeEditRequested = false;
        if (!busy) {
            setProgressText("");
        }
        refreshAttachmentBar();
        updateSendButton();
    }

    private void cancelEditPreset() {
        pendingEditTargetPath = null;
        pendingCodeEditRequested = false;
        if (!busy) {
            setProgressText("");
        }
        refreshAttachmentBar();
        updateSendButton();
        input.requestFocusInWindow();
    }

    private void startNewConversation() {
        if (!confirmConversationChange()) {
            return;
        }
        historyLoadGeneration++;
        cancelActiveRequest();
        resetConversationSurface();
        currentConversation = null;
        historyPanel.setActiveId("");
        updateConversationTitle();
    }

    private void resetConversationSurface() {
        requestGeneration++;
        busy = false;
        streamTimer.stop();
        streamState = null;
        streamCard = null;
        streamedText.setLength(0);
        history.clear();
        messageBodies.clear();
        footnotes.clear();
        pendingCodeContext = null;
        pendingEditTargetPath = null;
        pendingCodeEditRequested = false;
        lastAssistantTextForIntent = "";
        lastEditStatus = "";
        lastAnalysisContext = CodeContext.empty();
        pendingFiles.clear();
        attachmentBar.setVisible(false);
        input.setText("");
        setProgressText("");
        transcriptPanel.removeAll();
        welcomeMessage = null;
        historyPanel.setActiveId("");
        appendWelcome();
        updateSendButton();
        transcriptPanel.revalidate();
        transcriptPanel.repaint();
        input.requestFocusInWindow();
    }

    private boolean confirmConversationChange() {
        return !busy || Messages.showYesNoDialog(
                project,
                I18n.t(currentLanguage(), "stopBeforeConversationChange"),
                I18n.t(currentLanguage(), "conversationChangeTitle"),
                Messages.getQuestionIcon()) == Messages.YES;
    }

    private void cancelActiveRequest() {
        requestGeneration++;
        if (currentRequest != null) {
            currentRequest.cancel();
            currentRequest = null;
        }
        cancelCurrentIntentRequest();
        cancelCurrentEditRequest();
        discardStreamCard();
        streamTimer.stop();
        streamState = null;
        streamedText.setLength(0);
        busy = false;
        setProgressText("");
        updateSendButton();
    }

    private void cancelCurrentEditRequest() {
        if (currentEditRequest != null) {
            currentEditRequest.cancel();
            currentEditRequest = null;
        }
    }

    private void cancelCurrentIntentRequest() {
        if (currentIntentRequest != null) {
            currentIntentRequest.cancel();
            currentIntentRequest = null;
        }
    }

    private void recordCompletedTurn(
            ConversationTurn turn, ConversationMessage assistant) {
        rememberTurn(turn.question(), assistant.content());
        currentConversation = turn.appendTo(currentConversation, assistant);
        updateConversationTitle();
        persistConversationAsync(currentConversation);
    }

    private void updateConversationTitle() {
        conversationTitleLabel.setText(currentConversation == null
                ? I18n.t(currentLanguage(), "newConversation")
                : currentConversation.title());
    }

    private void loadInitialConversationAsync(boolean restoreActiveConversation) {
        int generation = ++historyLoadGeneration;
        conversationExecutor.execute(() -> {
            List<ConversationSummary> summaries = List.of();
            Conversation active = null;
            try {
                summaries = conversationStore.summaries();
            } catch (Exception error) {
                LOG.warn("Cannot load Jentor conversation summaries", error);
            }
            if (restoreActiveConversation) {
                try {
                    active = conversationStore.loadActive();
                } catch (Exception error) {
                    LOG.warn("Cannot restore the last Jentor conversation", error);
                }
            }
            List<ConversationSummary> loadedSummaries = summaries;
            Conversation loadedActive = active;
            ApplicationManager.getApplication().invokeLater(() -> {
                if (disposed || generation != historyLoadGeneration) {
                    return;
                }
                historyPanel.setSummaries(loadedSummaries);
                if (loadedActive != null) {
                    applyConversation(loadedActive);
                } else {
                    historyPanel.setActiveId("");
                }
            });
        });
    }

    private void refreshHistoryAsync() {
        conversationExecutor.execute(() -> {
            try {
                List<ConversationSummary> summaries = conversationStore.summaries();
                ApplicationManager.getApplication().invokeLater(() -> {
                    if (disposed) {
                        return;
                    }
                    historyPanel.setSummaries(summaries);
                    historyPanel.setActiveId(
                            currentConversation == null ? "" : currentConversation.id());
                });
            } catch (Exception error) {
                LOG.warn("Cannot refresh Jentor conversation history", error);
            }
        });
    }

    private void persistConversationAsync(Conversation conversation) {
        conversationExecutor.execute(() -> {
            try {
                conversationStore.save(conversation);
                refreshHistoryAsync();
            } catch (Exception error) {
                LOG.warn("Cannot save Jentor conversation", error);
                ApplicationManager.getApplication().invokeLater(() -> {
                    if (!disposed) {
                        setProgressText(I18n.t(currentLanguage(), "conversationSaveFailed"));
                    }
                });
            }
        });
    }

    private void toggleHistory() {
        boolean visible = !historyPanel.isVisible();
        historyPanel.setVisible(visible);
        revalidate();
        repaint();
        if (visible) {
            refreshHistoryAsync();
        }
    }

    private void openConversation(ConversationSummary summary) {
        if (summary == null || (currentConversation != null
                && summary.id().equals(currentConversation.id()))) {
            return;
        }
        if (!confirmConversationChange()) {
            historyPanel.setActiveId(
                    currentConversation == null ? "" : currentConversation.id());
            return;
        }
        cancelActiveRequest();
        int generation = ++historyLoadGeneration;
        setProgressText(I18n.t(currentLanguage(), "loadingConversation"));
        conversationExecutor.execute(() -> {
            try {
                Conversation conversation = conversationStore.load(summary.id());
                conversationStore.setLastActive(conversation.id());
                ApplicationManager.getApplication().invokeLater(() -> {
                    if (disposed || generation != historyLoadGeneration) {
                        return;
                    }
                    applyConversation(conversation);
                });
            } catch (Exception error) {
                LOG.warn("Cannot load Jentor conversation " + summary.id(), error);
                ApplicationManager.getApplication().invokeLater(() -> {
                    if (!disposed && generation == historyLoadGeneration) {
                        setProgressText(I18n.t(currentLanguage(), "conversationLoadFailed"));
                        historyPanel.setActiveId(
                                currentConversation == null ? "" : currentConversation.id());
                    }
                });
            }
        });
    }

    private void applyConversation(Conversation conversation) {
        currentConversation = conversation;
        requestGeneration++;
        busy = false;
        streamTimer.stop();
        streamState = null;
        streamCard = null;
        streamedText.setLength(0);
        history.clear();
        messageBodies.clear();
        footnotes.clear();
        pendingCodeContext = null;
        pendingEditTargetPath = null;
        pendingCodeEditRequested = false;
        lastEditStatus = "";
        lastAnalysisContext = CodeContext.empty();
        pendingFiles.clear();
        attachmentBar.setVisible(false);
        transcriptPanel.removeAll();
        welcomeMessage = null;
        input.setText("");
        for (ConversationMessage message : conversation.messages()) {
            if ("user".equals(message.role())) {
                appendMessage("user", message.content(), false, false,
                        message.attachmentSummary());
            } else if ("assistant".equals(message.role())) {
                appendAnswer(message.toAnswer());
                if (message.editResult()) {
                    lastEditStatus = message.content();
                }
            }
            history.add(message.toChatMessage());
        }
        while (history.size() > 8) {
            history.remove(0);
        }
        lastAssistantTextForIntent = "";
        for (int index = conversation.messages().size() - 1; index >= 0; index--) {
            ConversationMessage message = conversation.messages().get(index);
            if ("assistant".equals(message.role()) && !message.content().isBlank()) {
                lastAssistantTextForIntent = message.content();
                break;
            }
        }
        if (transcriptPanel.getComponentCount() == 0) {
            appendWelcome();
        }
        updateConversationTitle();
        historyPanel.setActiveId(conversation.id());
        setProgressText("");
        updateSendButton();
        transcriptPanel.revalidate();
        transcriptPanel.repaint();
        SwingUtilities.invokeLater(this::scrollToBottom);
    }

    private void renameConversation(ConversationSummary summary) {
        if (summary == null) {
            return;
        }
        String title = Messages.showInputDialog(
                project,
                I18n.t(currentLanguage(), "renameConversationPrompt"),
                I18n.t(currentLanguage(), "renameConversation"),
                Messages.getQuestionIcon(),
                summary.title(),
                null);
        if (title == null || title.isBlank()) {
            return;
        }
        conversationExecutor.execute(() -> {
            try {
                Conversation conversation = conversationStore.rename(summary.id(), title);
                ApplicationManager.getApplication().invokeLater(() -> {
                    if (disposed) {
                        return;
                    }
                    if (currentConversation != null
                            && currentConversation.id().equals(conversation.id())) {
                        currentConversation = conversation;
                        updateConversationTitle();
                    }
                    refreshHistoryAsync();
                });
            } catch (Exception error) {
                LOG.warn("Cannot rename Jentor conversation " + summary.id(), error);
            }
        });
    }

    private void deleteConversation(ConversationSummary summary) {
        if (summary == null) {
            return;
        }
        if (Messages.showYesNoDialog(
                project,
                I18n.t(currentLanguage(), "deleteConversationPrompt") + "\n\n"
                        + summary.title(),
                I18n.t(currentLanguage(), "deleteConversation"),
                Messages.getQuestionIcon()) != Messages.YES) {
            return;
        }
        boolean deletingActive = currentConversation != null
                && summary.id().equals(currentConversation.id());
        if (deletingActive) {
            cancelActiveRequest();
        }
        conversationExecutor.execute(() -> {
            try {
                conversationStore.delete(summary.id());
                ApplicationManager.getApplication().invokeLater(() -> {
                    if (disposed) {
                        return;
                    }
                    if (deletingActive) {
                        resetConversationSurface();
                        currentConversation = null;
                        updateConversationTitle();
                    }
                    refreshHistoryAsync();
                });
            } catch (Exception error) {
                LOG.warn("Cannot delete Jentor conversation " + summary.id(), error);
            }
        });
    }

    private void updateModeSelector() {
        String language = currentLanguage();
        TaSettings.State state = TaSettings.getInstance().getState();
        String onlineModel = state.chatModel == null || state.chatModel.isBlank()
                ? I18n.t(language, "onlineMode") : state.chatModel.trim();
        mockModeItem.setText(I18n.t(language, "mockMode"));
        onlineModeItem.setText(onlineModel);
        modeButton.setText(state.useMockModel ? mockModeItem.getText() : onlineModel);
        updateModeTooltip();
    }

    private void showSettings() {
        ShowSettingsUtil.getInstance().showSettingsDialog(project, TaConfigurable.class);
        syncSettingsUi();
    }

    private void syncSettingsUi() {
        if (disposed) {
            return;
        }
        if (!currentLanguage().equals(displayedLanguage)) {
            updateLanguageUi();
        } else {
            updateModeSelector();
        }
    }

    private void updateWorkspaceTooltip() {
        workspaceBox.setToolTipText(I18n.t(currentLanguage(), "askInWorkspaceHint"));
    }

    private void updateModeTooltip() {
        String language = currentLanguage();
        boolean mock = TaSettings.getInstance().getState().useMockModel;
        String hint = I18n.t(language, mock ? "mockModeHint" : "onlineModeHint");
        modeButton.setToolTipText(modeButton.getText() + " - " + hint);
        modeButton.getAccessibleContext().setAccessibleName(
                modeButton.getText() + " - " + hint);
    }

    private void selectMode(boolean mock) {
        TaSettings.State state = TaSettings.getInstance().getState();
        if (state.useMockModel == mock) {
            return;
        }
        state.useMockModel = mock;
        updateModeSelector();
        input.requestFocusInWindow();
    }

    private void setProgressText(String text) {
        progressLabel.setText(text == null ? "" : text);
        progressLabel.setVisible(!progressLabel.getText().isBlank());
        if (progressLabel.getParent() != null) {
            progressLabel.getParent().revalidate();
        }
    }

    private void updateLanguageUi() {
        String language = currentLanguage();
        syncingLanguageSelection = true;
        try {
            languageBox.setSelectedIndex(I18n.EN.equals(language) ? 1 : 0);
        } finally {
            syncingLanguageSelection = false;
        }
        settingsButton.setText(I18n.t(language, "settings"));
        workspaceBox.setText(I18n.t(language, "askInWorkspace"));
        updateWorkspaceTooltip();
        viewAttachmentButton.setText(I18n.t(language, "view"));
        cancelEditButton.setText(I18n.t(language, "cancelEdit"));
        removeAttachmentButton.setText(I18n.t(language, "remove"));
        newReplyButton.setText(I18n.t(language, "newReply"));
        historyButton.setText(I18n.t(language, "history"));
        newConversationButton.setText(I18n.t(language, "newConversation"));
        historyPanel.applyLanguage(language);
        updateConversationTitle();
        updateModeSelector();
        updateSendButton();
        refreshAttachmentBar();
        if (!busy) {
            setProgressText("");
        }
        if (welcomeMessage != null) {
            welcomeMessage.roleLabel().setText(I18n.t(language, "courseTa"));
            welcomeMessage.body().setText(I18n.t(language, "welcome"));
        }
        displayedLanguage = language;
        transcriptPanel.revalidate();
        transcriptPanel.repaint();
    }

    private void showCourseLibrary() {
        JentorToolWindowPanel root =
                (JentorToolWindowPanel) SwingUtilities.getAncestorOfClass(
                        JentorToolWindowPanel.class, this);
        if (root != null) {
            root.showCourseLibrary();
        }
    }

    private String friendlyError(Throwable error) {
        Throwable current = error;
        while ((current instanceof CompletionException
                || current instanceof ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank()
                ? current.getClass().getSimpleName()
                : message;
    }

    @Override
    public void dispose() {
        disposed = true;
        requestGeneration++;
        if (currentRequest != null) {
            currentRequest.cancel();
            currentRequest = null;
        }
        cancelCurrentIntentRequest();
        cancelCurrentEditRequest();
        settingsTimer.stop();
        reflowTimer.stop();
        streamTimer.stop();
        conversationExecutor.shutdownNow();
    }

    private static final class AssistantBubble {
        private final JPanel card;
        private final MessageBody body;

        private AssistantBubble(JPanel card, MessageBody body) {
            this.card = card;
            this.body = body;
        }
    }

    private static final class StreamState {
        private final int generation;
        private final StringBuilder pending = new StringBuilder();
        private volatile boolean resetRequested;

        private StreamState(int generation) {
            this.generation = generation;
        }
    }

    private record MessageLayout(MessageBody body, boolean user) {
    }

    private record EditorTarget(String path) {
    }

    private record MessageView(JBLabel roleLabel, MessageBody body) {
    }

    /** Transcript rows fill the width, but never absorb spare vertical space. */
    static final class ContentSizedPanel extends JPanel {
        ContentSizedPanel(LayoutManager layout) {
            super(layout);
            setAlignmentX(LEFT_ALIGNMENT);
        }

        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
        }

        @Override
        public Dimension getMinimumSize() {
            return new Dimension(0, getPreferredSize().height);
        }
    }

    static final class BubblePanel extends JPanel {
        private final Color fill;

        private BubblePanel(Color fill) {
            this.fill = fill;
            setOpaque(false);
            setAlignmentX(LEFT_ALIGNMENT);
            setLayout(new BorderLayout(0, 4));
            setBorder(BorderFactory.createEmptyBorder(
                    8, BUBBLE_HORIZONTAL_PADDING, 8, BUBBLE_HORIZONTAL_PADDING));
        }

        @Override
        public Dimension getMaximumSize() {
            return getPreferredSize();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g2 = (Graphics2D) graphics.create();
            g2.setRenderingHint(
                    RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(fill);
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
            g2.dispose();
            super.paintComponent(graphics);
        }
    }

    private static final class ComposerPanel extends JPanel {
        private ComposerPanel() {
            super(new BorderLayout(0, 2));
            setOpaque(false);
            setBorder(JBUI.Borders.empty(4, 6));
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g2 = (Graphics2D) graphics.create();
            g2.setRenderingHint(
                    RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            Color fill = UIManager.getColor("TextField.background");
            if (fill == null) {
                fill = UIManager.getColor("Panel.background");
            }
            if (fill != null) {
                g2.setColor(fill);
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 14, 14);
            }
            g2.setColor(JBColor.border());
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 14, 14);
            g2.dispose();
            super.paintComponent(graphics);
        }
    }

    private static final class ComposerModeButton extends JButton {
        private ComposerModeButton() {
            setOpaque(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setRolloverEnabled(true);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            if (getModel().isRollover() || getModel().isPressed()) {
                Graphics2D g2 = (Graphics2D) graphics.create();
                g2.setRenderingHint(
                        RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                Color fill = UIManager.getColor("ActionButton.hoverBackground");
                if (fill == null) {
                    fill = UIManager.getColor("Button.hoverBackground");
                }
                if (fill == null) {
                    fill = new JBColor(new Color(0xECEEF1), new Color(0x3C3F41));
                }
                if (getModel().isPressed()) {
                    fill = fill.darker();
                }
                int arc = Math.max(8, getHeight() - 4);
                g2.setColor(fill);
                g2.fillRoundRect(1, 2, getWidth() - 3, getHeight() - 5, arc, arc);
                g2.dispose();
            }
            super.paintComponent(graphics);
        }
    }

    private static final class ComposerSendButton extends JButton {
        private boolean stopping;

        private ComposerSendButton() {
            setOpaque(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setRolloverEnabled(true);
        }

        private void setStopping(boolean stopping) {
            if (this.stopping != stopping) {
                this.stopping = stopping;
                repaint();
            }
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g2 = (Graphics2D) graphics.create();
            g2.setRenderingHint(
                    RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            Color fill = UIManager.getColor(
                    isEnabled() ? "Button.default.background" : "Button.background");
            if (fill == null) {
                fill = UIManager.getColor("Panel.background");
            }
            if (fill != null) {
                if (isEnabled() && getModel().isRollover()) {
                    fill = fill.darker();
                }
                g2.setColor(fill);
                g2.fillOval(0, 0, getWidth() - 1, getHeight() - 1);
            }
            g2.setColor(JBColor.border());
            g2.drawOval(0, 0, getWidth() - 1, getHeight() - 1);

            Color glyph = isEnabled()
                    ? UIManager.getColor("Label.foreground")
                    : UIManager.getColor("Label.disabledForeground");
            if (glyph == null) {
                glyph = JBColor.GRAY;
            }
            g2.setColor(glyph);
            int centerX = getWidth() / 2;
            int centerY = getHeight() / 2;
            if (stopping) {
                g2.fillRoundRect(centerX - 4, centerY - 4, 8, 8, 2, 2);
            } else {
                g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND,
                        BasicStroke.JOIN_ROUND));
                int top = centerY - 5;
                int bottom = centerY + 4;
                g2.drawLine(centerX, bottom, centerX, top);
                g2.drawLine(centerX, top, centerX - 3, top + 3);
                g2.drawLine(centerX, top, centerX + 3, top + 3);
            }
            g2.dispose();
        }
    }
}
