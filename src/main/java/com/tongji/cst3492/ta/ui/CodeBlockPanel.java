package com.tongji.cst3492.ta.ui;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.JBColor;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.UIManager;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.util.Locale;

/** A bounded, independently styled code card inside a chat message. */
final class CodeBlockPanel extends JPanel {
    private static final JBColor BACKGROUND =
            new JBColor(new Color(0xF6F8FA), new Color(0x1E1F22));
    private static final JBColor OUTLINE =
            new JBColor(new Color(0xD8DEE4), new Color(0x41454B));
    private static final JBColor MUTED =
            new JBColor(new Color(0x57606A), new Color(0xA6ADB5));
    private static final int PADDING_X = 10;
    private static final int PADDING_Y = 8;
    private static final int MAX_CODE_HEIGHT = 360;
    private static final int ARC = 12;

    private final int naturalWidth;

    CodeBlockPanel(String language, String code, Project project, int contentWidth, boolean preview) {
        super(new BorderLayout());
        int width = Math.max(1, contentWidth);
        String label = language == null || language.isBlank() ? "CODE" : language.toUpperCase(Locale.ROOT);
        String source = code == null ? "" : code;
        // Fenced blocks normally contain a newline before the closing fence.
        String displayedCode = source.replaceFirst("\\R$", "");
        Font labelFont = UIManager.getFont("Label.font");
        Font headerFont = labelFont == null
                ? new Font("SansSerif", Font.PLAIN, 11)
                : labelFont.deriveFont(11f);
        Font codeFont = new Font(Font.MONOSPACED, Font.PLAIN, 12);
        setOpaque(false);
        setAlignmentX(LEFT_ALIGNMENT);
        setBorder(BorderFactory.createEmptyBorder(1, 1, 1, 1));

        JPanel header = new JPanel(new BorderLayout(8, 0));
        header.setOpaque(false);
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, OUTLINE),
                BorderFactory.createEmptyBorder(5, PADDING_X, 5, PADDING_X)));
        JLabel languageLabel = new JLabel(label);
        languageLabel.setFont(headerFont.deriveFont(Font.BOLD));
        languageLabel.setForeground(MUTED);
        header.add(languageLabel, BorderLayout.WEST);
        JButton copy = new JButton("Copy / 复制");
        copy.setFont(headerFont);
        copy.setForeground(MUTED);
        copy.setMargin(new Insets(1, 6, 1, 6));
        copy.setContentAreaFilled(false);
        copy.setFocusable(false);
        copy.setToolTipText("Copy code / 复制代码");
        copy.addActionListener(event -> Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(source), null));
        header.add(copy, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        JTextArea fallback = new JTextArea(displayedCode);
        fallback.setEditable(false);
        fallback.setFont(codeFont);
        fallback.setBackground(BACKGROUND);
        Color foreground = UIManager.getColor("Label.foreground");
        fallback.setForeground(foreground == null ? Color.BLACK : foreground);
        fallback.setBorder(BorderFactory.createEmptyBorder());
        fallback.setCaretPosition(0);
        TextContextMenu.install(fallback);
        FontMetrics metrics = fallback.getFontMetrics(codeFont);
        int textWidth = 0;
        for (String line : displayedCode.split("\\R", -1)) {
            textWidth = Math.max(textWidth, metrics.stringWidth(line));
        }
        int lines = Math.max(1, displayedCode.split("\\R", -1).length);
        int horizontalBar = textWidth > Math.max(1, width - 2 * PADDING_X - 2)
                ? Math.max(14, UIManager.getInt("ScrollBar.width")) : 0;
        int textHeight = Math.min(MAX_CODE_HEIGHT,
                lines * Math.max(18, metrics.getHeight()) + horizontalBar + 2);

        JComponent codeView = createCodeView(language, displayedCode, project, preview, fallback);
        JPanel codeArea = new JPanel(new BorderLayout());
        codeArea.setOpaque(false);
        codeArea.setBorder(BorderFactory.createEmptyBorder(
                PADDING_Y, PADDING_X, PADDING_Y, PADDING_X));
        codeArea.add(codeView, BorderLayout.CENTER);
        codeArea.setPreferredSize(new Dimension(width, textHeight + 2 * PADDING_Y));
        add(codeArea, BorderLayout.CENTER);

        naturalWidth = Math.max(header.getPreferredSize().width,
                textWidth + 2 * PADDING_X + 2);
        int height = header.getPreferredSize().height + codeArea.getPreferredSize().height + 2;
        setPreferredSize(new Dimension(width, height));
        setMinimumSize(new Dimension(1, height));
        setMaximumSize(new Dimension(width, height));
    }

    int preferredContentWidth() {
        return naturalWidth;
    }

    private JComponent createCodeView(
            String language, String code, Project project, boolean preview, JTextArea fallback) {
        if (!preview && project != null) {
            try {
                FileType fileType = FileTypeManager.getInstance()
                        .getFileTypeByExtension(extension(language));
                EditorTextField editor = new EditorTextField(code, project, fileType);
                editor.setViewer(true);
                editor.setOneLineMode(false);
                editor.setBorder(BorderFactory.createEmptyBorder());
                editor.setBackground(BACKGROUND);
                editor.addSettingsProvider(instance -> {
                    instance.setBackgroundColor(BACKGROUND);
                    instance.getSettings().setLineNumbersShown(false);
                    instance.getSettings().setLineMarkerAreaShown(false);
                    instance.getSettings().setFoldingOutlineShown(false);
                    instance.getSettings().setRightMarginShown(false);
                    instance.getSettings().setCaretRowShown(false);
                    instance.getSettings().setAdditionalLinesCount(0);
                    instance.getSettings().setAdditionalColumnsCount(0);
                    instance.setHorizontalScrollbarVisible(true);
                    instance.setVerticalScrollbarVisible(true);
                });
                return editor;
            } catch (RuntimeException | LinkageError ignored) {
                // The same card remains usable when an editor cannot be created.
            }
        }
        JScrollPane scroll = new JScrollPane(fallback,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setOpaque(false);
        scroll.getViewport().setBackground(BACKGROUND);
        return scroll;
    }

    private static String extension(String language) {
        if (language == null || language.isBlank()) {
            return "txt";
        }
        return switch (language.toLowerCase(Locale.ROOT)) {
            case "javascript" -> "js";
            case "typescript" -> "ts";
            case "python" -> "py";
            case "kotlin" -> "kt";
            case "shell", "bash" -> "sh";
            default -> language;
        };
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D copy = (Graphics2D) graphics.create();
        copy.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        copy.setColor(BACKGROUND);
        copy.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, ARC, ARC);
        copy.dispose();
        super.paintComponent(graphics);
    }

    @Override
    protected void paintBorder(Graphics graphics) {
        Graphics2D copy = (Graphics2D) graphics.create();
        copy.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        copy.setColor(OUTLINE);
        copy.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, ARC, ARC);
        copy.dispose();
    }
}
