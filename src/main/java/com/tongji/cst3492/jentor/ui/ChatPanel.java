package com.tongji.cst3492.jentor.ui;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.util.ui.JBUI;
import com.tongji.cst3492.jentor.llm.Answer;
import com.tongji.cst3492.jentor.llm.ChatMessage;
import com.tongji.cst3492.jentor.llm.CitationStatus;
import com.tongji.cst3492.jentor.llm.PromptBuilder;
import com.tongji.cst3492.jentor.llm.StreamSink;
import com.tongji.cst3492.jentor.context.CodeFilePolicy;
import com.tongji.cst3492.jentor.model.CodeContext;
import com.tongji.cst3492.jentor.model.CodeFileSnapshot;
import com.tongji.cst3492.jentor.model.CourseDocumentSource;
import com.tongji.cst3492.jentor.model.SourceReference;
import com.tongji.cst3492.jentor.service.ChatRequest;
import com.tongji.cst3492.jentor.service.CourseLibraryService;
import com.tongji.cst3492.jentor.service.CourseTaService;
import com.tongji.cst3492.jentor.service.WorkspaceIndexService;
import com.tongji.cst3492.jentor.settings.TaConfigurable;
import com.tongji.cst3492.jentor.settings.TaSettings;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
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
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.BorderLayout;
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
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.nio.file.Path;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

public final class ChatPanel extends JBPanel<ChatPanel> implements Disposable {
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
    private final JPanel transcriptPanel = new JPanel();
    private final JBScrollPane transcriptScroll = new JBScrollPane(transcriptPanel);
    private final JBTextArea input = new JBTextArea();
    private final JButton sendButton = new JButton();
    private final JButton settingsButton = new JButton();
    private final JButton moreButton = new JButton("⋯");
    private final JButton newReplyButton = new JButton();
    private final JBLabel statusLabel = new JBLabel();
    private final JBLabel progressLabel = new JBLabel();
    private final JBLabel mockBadge = new JBLabel();
    private final JCheckBox workspaceBox = new JCheckBox();
    private final JComboBox<String> languageBox =
            new JComboBox<>(new String[]{"中文", "English"});
    private final JPanel attachmentBar = new JPanel(new BorderLayout(6, 0));
    private final JBLabel attachmentLabel = new JBLabel();
    private final JButton viewAttachmentButton = new JButton();
    private final JButton removeAttachmentButton = new JButton();
    private final JPopupMenu moreMenu = new JPopupMenu();
    private final JMenuItem courseMaterialsItem = new JMenuItem();
    private final JMenuItem manageCourseMaterialsItem = new JMenuItem();
    private final JMenuItem refreshCourseMaterialsItem = new JMenuItem();
    private final JMenuItem importIndexItem = new JMenuItem();
    private final JMenuItem clearConversationItem = new JMenuItem();
    private final Timer mockTimer;
    private final Timer reflowTimer;
    private final Timer streamTimer;

    private final List<ChatMessage> history = new ArrayList<>();
    private final StringBuilder streamedText = new StringBuilder();
    private final List<MessageLayout> messageBodies = new ArrayList<>();
    private final List<JBTextArea> footnotes = new ArrayList<>();
    private CodeContext pendingCodeContext;
    private final List<CodeFileSnapshot> pendingFiles = new ArrayList<>();
    private ChatRequest currentRequest;
    private volatile StreamState streamState;
    private AssistantBubble streamCard;
    private boolean busy;
    private int requestGeneration;

    public ChatPanel(Project project) {
        super(new BorderLayout(0, 8));
        this.project = project;
        CourseTaService resolvedService = project.getService(CourseTaService.class);
        if (resolvedService == null) {
            resolvedService = new CourseTaService(project);
        }
        this.service = resolvedService;
        setBorder(JBUI.Borders.empty(8));

        transcriptPanel.setLayout(new BoxLayout(transcriptPanel, BoxLayout.Y_AXIS));
        transcriptPanel.setOpaque(false);
        transcriptPanel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        transcriptScroll.setBorder(JBUI.Borders.empty());
        transcriptScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        transcriptScroll.getVerticalScrollBar().setUnitIncrement(16);

        add(createTopPanel(), BorderLayout.NORTH);
        add(createCenterPanel(), BorderLayout.CENTER);
        add(createBottomPanel(), BorderLayout.SOUTH);

        appendWelcome();
        updateLanguageUi();
        updateStatus();
        updateMockBadge();
        mockTimer = new Timer(2000, event -> {
            updateMockBadge();
            updateStatus();
        });
        mockTimer.start();
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
    }

    public void setPendingContext(CodeContext context) {
        if (context == null || !context.hasSelection()) {
            return;
        }
        pendingCodeContext = context;
        refreshAttachmentBar();
        updateLanguageUi();
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
        statusLabel.setForeground(JBColor.GRAY);
        progressLabel.setForeground(JBColor.GRAY);
        mockBadge.setForeground(JBColor.GRAY);
        mockBadge.setFont(mockBadge.getFont().deriveFont(Font.BOLD, 11f));
        mockBadge.setVisible(false);
        labels.add(statusLabel);
        labels.add(mockBadge);
        labels.add(progressLabel);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        controls.setOpaque(false);
        workspaceBox.setFont(workspaceBox.getFont().deriveFont(11f));
        workspaceBox.setOpaque(false);
        workspaceBox.setSelected(TaSettings.getInstance().getState().workspaceContext);
        workspaceBox.setToolTipText("Include the workspace code outline in the question");
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
        });
        languageBox.setSelectedIndex("zh".equals(
                TaSettings.getInstance().getState().language) ? 0 : 1);
        languageBox.setFont(languageBox.getFont().deriveFont(11f));
        languageBox.setPreferredSize(new Dimension(88, 26));
        languageBox.setMaximumRowCount(2);
        languageBox.addActionListener(event -> {
            TaSettings.State state = TaSettings.getInstance().getState();
            state.language = languageBox.getSelectedIndex() == 0 ? "zh" : "en";
            updateLanguageUi();
        });
        configureButton(settingsButton, event ->
                ShowSettingsUtil.getInstance().showSettingsDialog(project, TaConfigurable.class));
        configureButton(moreButton, event ->
                moreMenu.show(moreButton, 0, moreButton.getHeight()));

        courseMaterialsItem.addActionListener(event -> showIndexInfo());
        manageCourseMaterialsItem.addActionListener(event -> showCourseLibrary());
        refreshCourseMaterialsItem.addActionListener(event -> refreshCourseMaterials());
        importIndexItem.addActionListener(event -> loadIndex());
        clearConversationItem.addActionListener(event -> clearChat());
        moreMenu.add(courseMaterialsItem);
        moreMenu.add(manageCourseMaterialsItem);
        moreMenu.add(refreshCourseMaterialsItem);
        moreMenu.add(importIndexItem);
        moreMenu.addSeparator();
        moreMenu.add(clearConversationItem);

        controls.add(workspaceBox);
        controls.add(languageBox);
        controls.add(settingsButton);
        controls.add(moreButton);

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
        attachmentBar.setOpaque(false);
        attachmentBar.setVisible(false);
        attachmentLabel.setForeground(JBColor.GRAY);
        JPanel attachmentButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        attachmentButtons.setOpaque(false);
        configureButton(viewAttachmentButton, event -> viewAttachment());
        configureButton(removeAttachmentButton, event -> removeAttachment());
        attachmentButtons.add(viewAttachmentButton);
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
                        && !event.isMetaDown()) {
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

        JPanel inputPanel = new JPanel(new BorderLayout(6, 0));
        inputPanel.setOpaque(false);
        inputPanel.add(new JBScrollPane(input), BorderLayout.CENTER);
        inputPanel.add(sendButton, BorderLayout.EAST);

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

    private void send() {
        if (busy) {
            return;
        }
        String question = input.getText().trim();
        if (question.isEmpty()) {
            return;
        }
        CodeContext codeContext = pendingContextSnapshot();
        boolean includeWorkspace = TaSettings.getInstance().getState().workspaceContext;
        List<ChatMessage> historySnapshot = new ArrayList<>(history);

        appendMessage("user", question, false, true);
        input.setText("");
        busy = true;
        int generation = ++requestGeneration;
        updateSendButton();
        progressLabel.setText(I18n.t(currentLanguage(), "thinking"));
        streamState = new StreamState(generation);
        streamCard = null;
        streamedText.setLength(0);
        streamTimer.restart();

        ChatRequest request;
        try {
            request = service.ask(question, codeContext, historySnapshot, stage ->
                    ApplicationManager.getApplication().invokeLater(() -> {
                        if (generation == requestGeneration && busy) {
                            progressLabel.setText(stage);
                        }
                    }), createSink(generation), includeWorkspace);
        } catch (RuntimeException error) {
            streamTimer.stop();
            streamState = null;
            busy = false;
            input.setText(question);
            progressLabel.setText("");
            updateSendButton();
            appendError(friendlyError(error), currentLanguage(), question);
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
                    progressLabel.setText("");
                    flushStream();
                    streamTimer.stop();
                    streamState = null;
                    if (error == null) {
                        finishStreamCard(answer);
                        if (answer.citationStatus() != CitationStatus.FAILED) {
                            rememberTurn(question, answer.text());
                        }
                    } else {
                        discardStreamCard();
                        input.setText(question);
                        appendError(friendlyError(error), currentLanguage(), question);
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
        flushStream();
        discardStreamCard();
        streamTimer.stop();
        streamState = null;
        streamedText.setLength(0);
        busy = false;
        progressLabel.setText(I18n.t(currentLanguage(), "stopped"));
        updateSendButton();
    }

    private void updateSendButton() {
        String language = currentLanguage();
        sendButton.setText(I18n.t(language, busy ? "stop" : "send"));
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
        JPanel meta = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        meta.setOpaque(false);
        meta.setAlignmentX(LEFT_ALIGNMENT);
        if (answer.citationStatus() != CitationStatus.NOT_APPLICABLE) {
            JBLabel status = new JBLabel(citationStatusText(answer.citationStatus()));
            status.setFont(status.getFont().deriveFont(11f));
            status.setForeground(answer.citationStatus() == CitationStatus.FAILED
                    ? JBColor.RED
                    : JBColor.GRAY);
            meta.add(status);
        }

        JButton copy = new JButton(I18n.t(currentLanguage(), "copyAnswer"));
        copy.setFont(copy.getFont().deriveFont(11f));
        copy.addActionListener(event -> Toolkit.getDefaultToolkit()
                .getSystemClipboard()
                .setContents(new StringSelection(MarkdownText.toPlainText(answer.text())), null));
        meta.add(copy);

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

        JPanel debugPanel = new JPanel();
        debugPanel.setLayout(new BoxLayout(debugPanel, BoxLayout.Y_AXIS));
        debugPanel.setOpaque(false);
        debugPanel.setAlignmentX(LEFT_ALIGNMENT);
        debugPanel.setVisible(false);
        for (var candidate : answer.debugCandidates()) {
            JBLabel label = new JBLabel(candidate.getChunk().getFileName()
                    + " " + candidate.getChunk().locator()
                    + "  score=" + String.format(Locale.ROOT, "%.3f", candidate.getFusedScore()));
            label.setFont(label.getFont().deriveFont(10f));
            label.setForeground(JBColor.GRAY);
            debugPanel.add(label);
        }
        if (!answer.debugCandidates().isEmpty()) {
            JButton debugToggle = new JButton(I18n.t(currentLanguage(), "debugCandidates"));
            debugToggle.setFont(debugToggle.getFont().deriveFont(11f));
            debugToggle.addActionListener(event -> {
                debugPanel.setVisible(!debugPanel.isVisible());
                card.revalidate();
                card.repaint();
            });
            meta.add(debugToggle);
        }

        card.add(meta);
        card.add(sourcesPanel);
        card.add(debugPanel);
        if (!answer.disclosure().isBlank()) {
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
            progressLabel.setText("");
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

    private void appendMessage(String role, String text, boolean warning, boolean userMessage) {
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
        row.add(bubble, user ? BorderLayout.EAST : BorderLayout.WEST);
        addRow(row, userMessage);
    }

    private void appendError(String message, String language, String question) {
        boolean settings = message.toLowerCase(Locale.ROOT).contains("api key")
                || message.contains("密钥");
        boolean index = message.toLowerCase(Locale.ROOT).contains("index")
                || message.contains("索引");
        String actionLabel = settings ? I18n.t(language, "openSettings")
                : index ? I18n.t(language, "importIndexAction")
                : I18n.t(language, "retry");
        Runnable action;
        if (settings) {
            action = () -> ShowSettingsUtil.getInstance()
                    .showSettingsDialog(project, TaConfigurable.class);
        } else if (index) {
            action = this::loadIndex;
        } else {
            action = () -> {
                input.setText(question);
                send();
            };
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
        appendMessage("assistant", I18n.t(currentLanguage(), "welcome"), false, true);
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
        if (pendingCodeContext != null && pendingCodeContext.hasSelection()) {
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
        attachmentBar.setVisible(visible);
        if (visible) {
            attachmentLabel.setText(summary);
        }
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
        attachmentBar.setVisible(false);
    }

    private void clearChat() {
        requestGeneration++;
        if (currentRequest != null) {
            currentRequest.cancel();
            currentRequest = null;
        }
        busy = false;
        streamTimer.stop();
        streamState = null;
        streamCard = null;
        streamedText.setLength(0);
        history.clear();
        messageBodies.clear();
        footnotes.clear();
        pendingCodeContext = null;
        pendingFiles.clear();
        attachmentBar.setVisible(false);
        progressLabel.setText("");
        transcriptPanel.removeAll();
        appendWelcome();
        updateSendButton();
        transcriptPanel.revalidate();
        transcriptPanel.repaint();
    }

    private void updateStatus() {
        try {
            int count = service.getDocuments().size();
            CourseLibraryService library = service.getCourseLibraryService();
            String language = currentLanguage();
            if (!busy && I18n.t(language, "courseMaterialsIndexing")
                    .equals(progressLabel.getText())) {
                progressLabel.setText("");
            }
            if (!library.getSources().isEmpty()) {
                if (!library.getLastError().isBlank()) {
                    statusLabel.setText(I18n.t(language, "indexError")
                            + library.getLastError());
                } else if (library.isBusy()) {
                    statusLabel.setText(I18n.t(language, "courseMaterialsIndexing"));
                } else if (library.getIndex() != null) {
                    statusLabel.setText(I18n.t(language, "courseMaterialsReady")
                            + count + I18n.t(language, "documents"));
                } else {
                    statusLabel.setText(I18n.t(language, "courseMaterialsWaiting"));
                }
            } else {
                statusLabel.setText(I18n.t(language, "courseMaterialsReady")
                        + count + I18n.t(language, "documents"));
            }
        } catch (Throwable error) {
            statusLabel.setText(I18n.t(currentLanguage(), "indexError")
                    + friendlyError(error));
        }
    }

    private void updateMockBadge() {
        boolean mock = TaSettings.getInstance().getState().useMockModel;
        mockBadge.setVisible(mock);
        mockBadge.setText(I18n.t(currentLanguage(), "mockBadge"));
    }

    private void updateLanguageUi() {
        String language = currentLanguage();
        settingsButton.setText(I18n.t(language, "settings"));
        sendButton.setText(I18n.t(language, busy ? "stop" : "send"));
        workspaceBox.setText(I18n.t(language, "askInWorkspace"));
        viewAttachmentButton.setText(I18n.t(language, "view"));
        removeAttachmentButton.setText(I18n.t(language, "remove"));
        newReplyButton.setText(I18n.t(language, "newReply"));
        courseMaterialsItem.setText(I18n.t(language, "courseMaterials"));
        manageCourseMaterialsItem.setText(I18n.t(language, "manageCourseMaterials"));
        refreshCourseMaterialsItem.setText(I18n.t(language, "refreshCourseMaterials"));
        importIndexItem.setText(I18n.t(language, "importIndex"));
        clearConversationItem.setText(I18n.t(language, "clearConversation"));
        refreshAttachmentBar();
        if (!busy) {
            progressLabel.setText("");
        }
        updateStatus();
        updateMockBadge();
        if (transcriptPanel.getComponentCount() <= 1) {
            transcriptPanel.removeAll();
            messageBodies.clear();
            footnotes.clear();
            appendWelcome();
        }
    }

    private void showIndexInfo() {
        String language = currentLanguage();
        StringBuilder message = new StringBuilder();
        message.append(service.getIndexSummary()).append("\n\n");
        CourseLibraryService library = service.getCourseLibraryService();
        if (!library.getSources().isEmpty()) {
            for (var source : library.getSources()) {
                message.append("- ")
                        .append(source.displayName())
                        .append(" [")
                        .append(source.status())
                        .append("]\n  ")
                        .append(source.path())
                        .append('\n');
            }
            message.append('\n');
        }
        for (var document : service.getDocuments()) {
            message.append("- ")
                    .append(document.getFileName())
                    .append(" (")
                    .append(document.getPageCount())
                    .append(I18n.t(language, "pages"))
                    .append(")\n");
        }
        Messages.showInfoMessage(project, message.toString(),
                I18n.t(language, "courseMaterials"));
    }

    private void refreshCourseMaterials() {
        service.refreshCourseLibrary();
        progressLabel.setText(I18n.t(currentLanguage(), "courseMaterialsIndexing"));
        updateStatus();
    }

    private void showCourseLibrary() {
        JentorToolWindowPanel root =
                (JentorToolWindowPanel) SwingUtilities.getAncestorOfClass(
                        JentorToolWindowPanel.class, this);
        if (root != null) {
            root.showCourseLibrary();
        }
    }

    private void loadIndex() {
        String language = currentLanguage();
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(I18n.t(language, "importIndex"));
        chooser.setFileFilter(new FileNameExtensionFilter("Course index (JSON)", "json"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        try {
            service.loadIndex(chooser.getSelectedFile().toPath());
            updateStatus();
            appendMessage("assistant",
                    I18n.t(language, "loadedIndex") + service.getIndexSummary(),
                    false, false);
        } catch (Exception error) {
            Messages.showErrorDialog(project, friendlyError(error),
                    I18n.t(language, "cannotLoadIndex"));
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
        requestGeneration++;
        if (currentRequest != null) {
            currentRequest.cancel();
            currentRequest = null;
        }
        mockTimer.stop();
        reflowTimer.stop();
        streamTimer.stop();
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
}
