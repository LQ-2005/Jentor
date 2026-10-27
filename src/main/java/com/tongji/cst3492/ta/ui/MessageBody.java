package com.tongji.cst3492.ta.ui;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.JBColor;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.UIManager;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MessageBody extends JPanel {
    private static final Pattern CODE_FENCE = Pattern.compile(
            "(?s)```([A-Za-z0-9_+-]*)\\R(.*?)```");

    private final Project project;
    private String sourceText;
    private boolean preview;
    private int contentWidth;
    private final Font uiFont;
    private final String fontFamily;

    public MessageBody(String text, Project project, int contentWidth) {
        super();
        this.project = project;
        this.sourceText = text == null ? "" : text;
        this.contentWidth = Math.max(200, contentWidth);
        Font labelFont = UIManager.getFont("Label.font");
        this.uiFont = labelFont != null
                ? labelFont.deriveFont(13f)
                : new Font("SansSerif", Font.PLAIN, 13);
        this.fontFamily = uiFont.getFamily();
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setOpaque(false);
        render(sourceText);
    }

    /** Replaces the rendered text with the authoritative final content. */
    public void setText(String text) {
        update(text, false);
    }

    /**
     * Replaces the text of a message that is still being streamed. Code blocks are
     * painted as read-only text areas instead of editor components, because this
     * method is called several times per second and a real editor per frame would
     * be needlessly expensive.
     */
    public void setPreviewText(String text) {
        update(text, true);
    }

    private void update(String text, boolean livePreview) {
        String next = text == null ? "" : text;
        if (next.equals(sourceText) && livePreview == preview) {
            return;
        }
        sourceText = next;
        preview = livePreview;
        removeAll();
        render(sourceText);
        revalidate();
        repaint();
    }

    public void setContentWidth(int width) {
        int newWidth = Math.max(200, width);
        if (newWidth == contentWidth) {
            return;
        }
        contentWidth = newWidth;
        removeAll();
        render(sourceText);
        revalidate();
        repaint();
    }

    private void render(String text) {
        Matcher matcher = CODE_FENCE.matcher(text);
        int last = 0;
        while (matcher.find()) {
            addProse(text.substring(last, matcher.start()));
            addCode(matcher.group(1).trim(), matcher.group(2));
            last = matcher.end();
        }
        addProse(text.substring(last));
    }

    private void addProse(String text) {
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return;
        }
        JEditorPane pane = new JEditorPane();
        pane.setContentType("text/html");
        pane.setEditable(false);
        pane.setOpaque(false);
        pane.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));
        pane.setFont(uiFont);
        TextContextMenu.install(pane);
        try {
            pane.setText("<html><body><font face=\"" + escapeAttribute(fontFamily) + "\">"
                    + proseHtml(trimmed) + "</font></body></html>");
            pane.setSize(new Dimension(contentWidth, Short.MAX_VALUE));
            Dimension preferred = pane.getPreferredSize();
            int height = Math.max(18, (int) Math.ceil(preferred.height * 1.15) + 8);
            pane.setPreferredSize(new Dimension(contentWidth, height));
            add(pane);
        } catch (Throwable error) {
            JTextArea fallback = new JTextArea(trimmed);
            fallback.setEditable(false);
            fallback.setLineWrap(true);
            fallback.setWrapStyleWord(true);
            fallback.setOpaque(false);
            fallback.setBorder(null);
            fallback.setFont(uiFont);
            TextContextMenu.install(fallback);
            fallback.setSize(new Dimension(contentWidth, Short.MAX_VALUE));
            FontMetrics metrics = fallback.getFontMetrics(fallback.getFont());
            int width = contentWidth;
            int textWidth = 0;
            for (String line : trimmed.split("\\R", -1)) {
                textWidth = Math.max(textWidth, metrics.stringWidth(line));
            }
            width = Math.min(contentWidth, Math.max(160, textWidth + 12));
            fallback.setSize(width, Short.MAX_VALUE);
            fallback.setPreferredSize(new Dimension(width, fallback.getPreferredSize().height));
            add(fallback);
        }
    }

    private void addCode(String language, String code) {
        JPanel card = new JPanel(new BorderLayout(4, 4));
        card.setOpaque(false);
        card.setBorder(BorderFactory.createEmptyBorder(2, 0, 6, 0));

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        JLabel languageLabel = new JLabel(language.isBlank() ? "code" : language);
        languageLabel.setFont(languageLabel.getFont().deriveFont(Font.PLAIN, 11f));
        languageLabel.setForeground(JBColor.GRAY);
        header.add(languageLabel, BorderLayout.WEST);

        JButton copy = new JButton("Copy / 复制");
        copy.setFont(copy.getFont().deriveFont(11f));
        copy.addActionListener(event -> Toolkit.getDefaultToolkit()
                .getSystemClipboard()
                .setContents(new StringSelection(code), null));
        header.add(copy, BorderLayout.EAST);
        card.add(header, BorderLayout.NORTH);

        int lines = Math.max(1, code.split("\\R", -1).length);
        int height = Math.min(360, Math.max(80, lines * 18 + 18));
        JTextArea fallback = new JTextArea(code);
        fallback.setEditable(false);
        fallback.setFont(new Font("Monospaced", Font.PLAIN, 12));
        fallback.setBackground(new JBColor(
                new java.awt.Color(0xF4F6F8),
                new java.awt.Color(0x1E1F22)));
        Color foreground = UIManager.getColor("Label.foreground");
        fallback.setForeground(foreground != null ? foreground : Color.BLACK);
        fallback.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        fallback.setPreferredSize(new Dimension(contentWidth, height));

        FileType fileType = FileTypeManager.getInstance()
                .getFileTypeByExtension(language.isBlank() ? "txt" : language);
        if (!preview && fileType != null) {
            try {
                EditorTextField editor = new EditorTextField(code, project, fileType);
                editor.setViewer(true);
                editor.setOneLineMode(false);
                editor.setPreferredSize(new Dimension(contentWidth, height));
                card.add(editor, BorderLayout.CENTER);
            } catch (Throwable error) {
                card.add(fallback, BorderLayout.CENTER);
            }
        } else {
            card.add(fallback, BorderLayout.CENTER);
        }
        card.setMaximumSize(new Dimension(contentWidth, height + 40));
        add(card);
    }

    private String proseHtml(String text) {
        String escaped = escape(text);
        escaped = escaped.replaceAll("\\*\\*(.+?)\\*\\*", "<b>$1</b>");
        escaped = escaped.replaceAll("`([^`]+)`", "<code>$1</code>");
        String[] lines = escaped.split("\\R", -1);
        StringBuilder html = new StringBuilder();
        boolean inList = false;
        for (String line : lines) {
            String trimmed = line.trim();
            boolean bullet = trimmed.startsWith("- ") || trimmed.startsWith("* ");
            if (bullet) {
                if (!inList) {
                    html.append("<ul>");
                    inList = true;
                }
                html.append("<li>").append(trimmed.substring(2)).append("</li>");
            } else {
                if (inList) {
                    html.append("</ul>");
                    inList = false;
                }
                if (trimmed.isEmpty()) {
                    html.append("<br/>");
                } else {
                    html.append("<p>").append(trimmed).append("</p>");
                }
            }
        }
        if (inList) {
            html.append("</ul>");
        }
        return html.toString();
    }

    private String escape(String text) {
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private String escapeAttribute(String text) {
        return text.replace("&", "")
                .replace("\"", "")
                .replace("<", "")
                .replace(">", "");
    }
}
