package com.tongji.cst3492.jentor.ui;

import com.intellij.openapi.project.Project;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.UIManager;
import javax.swing.text.DefaultEditorKit;
import javax.swing.text.JTextComponent;
import javax.swing.text.Element;
import javax.swing.text.View;
import javax.swing.text.ViewFactory;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.InlineView;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public final class MessageBody extends JPanel {
    private final Project project;
    private String sourceText;
    private boolean preview;
    private int contentWidth;
    private int naturalContentWidth;
    private final Font uiFont;
    private final String fontFamily;

    public MessageBody(String text, Project project, int contentWidth) {
        super();
        this.project = project;
        this.sourceText = text == null ? "" : text;
        this.contentWidth = Math.max(1, contentWidth);
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
        int newWidth = Math.max(1, width);
        if (newWidth == contentWidth) {
            return;
        }
        contentWidth = newWidth;
        removeAll();
        render(sourceText);
        revalidate();
        repaint();
    }

    /** Natural rendered width, capped to the space available for the message. */
    /** The message as plain text, used by the copy action. */
    String plainText() {
        return MarkdownText.toPlainText(sourceText);
    }

    /**
     * Makes rendered text selectable and copyable. The component takes focus on a
     * click and binds the platform copy shortcut itself, because a read-only
     * JEditorPane does not always receive it from the IDE keymap.
     */
    private void makeCopyable(JTextComponent component) {
        component.setFocusable(true);
        component.setRequestFocusEnabled(true);
        component.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                component.requestFocusInWindow();
            }
        });
        int mask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        component.getInputMap(JComponent.WHEN_FOCUSED)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_C, mask), DefaultEditorKit.copyAction);
        component.getInputMap(JComponent.WHEN_FOCUSED)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_A, mask),
                        DefaultEditorKit.selectAllAction);
    }

    public int preferredContentWidth(int maxWidth) {
        return Math.min(Math.max(1, maxWidth), Math.max(1, naturalContentWidth));
    }

    @Override
    public Dimension getMaximumSize() {
        return new Dimension(contentWidth, getPreferredSize().height);
    }

    private void render(String text) {
        naturalContentWidth = 0;
        for (MarkdownContent.Part part : MarkdownContent.parse(text)) {
            if (part.code() != null) {
                addCode(part.language(), part.code());
            } else {
                addProse(part.html());
            }
        }
    }

    private void addProse(String html) {
        String trimmed = html.trim();
        if (trimmed.isEmpty()) {
            return;
        }
        JEditorPane pane = new JEditorPane();
        pane.setEditorKit(new WrappingHtmlEditorKit());
        pane.setEditable(false);
        pane.setOpaque(false);
        pane.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));
        pane.setFont(uiFont);
        pane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true);
        TextContextMenu.install(pane);
        makeCopyable(pane);
        try {
            pane.setText("<html><head><style>body { margin: 0; } "
                    + "p { margin-top: 0; margin-bottom: 6px; } "
                    + "h1, h2, h3, h4, h5, h6 { margin-top: 8px; margin-bottom: 6px; } "
                    + "h1 { font-size: 145%; } h2 { font-size: 130%; } "
                    + "h3 { font-size: 115%; } h4, h5, h6 { font-size: 105%; } "
                    + "ul, ol { margin-top: 2px; margin-bottom: 6px; margin-left: 18px; } "
                    + "li { margin-bottom: 3px; } "
                    + "blockquote { margin-left: 12px; margin-right: 4px; } "
                    + "code { font-family: Monospaced; } "
                    + "table { margin-top: 6px; margin-bottom: 6px; } "
                    + "th, td { border: 1px solid #808080; padding: 4px; }"
                    + "</style></head>"
                    + "<body><font face=\"" + escapeAttribute(fontFamily) + "\">"
                    + trimmed + "</font></body></html>");
            Insets insets = pane.getInsets();
            View root = pane.getUI().getRootView(pane);
            naturalContentWidth = Math.max(naturalContentWidth,
                    (int) Math.ceil(root.getPreferredSpan(View.X_AXIS))
                            + insets.left + insets.right + 2);
            root.setSize(Math.max(1, contentWidth - insets.left - insets.right),
                    Float.MAX_VALUE);
            int height = Math.max(pane.getFontMetrics(uiFont).getHeight(),
                    (int) Math.ceil(root.getPreferredSpan(View.Y_AXIS)))
                    + insets.top + insets.bottom + 2;
            setBlockSize(pane, height);
            add(pane);
        } catch (Throwable error) {
            JTextArea fallback = new JTextArea(sourceText);
            fallback.setEditable(false);
            fallback.setLineWrap(true);
            fallback.setWrapStyleWord(true);
            fallback.setOpaque(false);
            fallback.setBorder(null);
            fallback.setFont(uiFont);
            TextContextMenu.install(fallback);
            makeCopyable(fallback);
            fallback.setSize(new Dimension(contentWidth, Short.MAX_VALUE));
            FontMetrics metrics = fallback.getFontMetrics(fallback.getFont());
            int textWidth = 0;
            for (String line : sourceText.split("\\R", -1)) {
                textWidth = Math.max(textWidth, metrics.stringWidth(line));
            }
            naturalContentWidth = Math.max(naturalContentWidth, textWidth + 2);
            setBlockSize(fallback, Math.max(metrics.getHeight(),
                    fallback.getPreferredSize().height));
            add(fallback);
        }
    }

    private void addCode(String language, String code) {
        CodeBlockPanel card = new CodeBlockPanel(language, code, project, contentWidth, preview);
        naturalContentWidth = Math.max(naturalContentWidth, card.preferredContentWidth());
        JPanel spacing = new JPanel();
        spacing.setLayout(new BoxLayout(spacing, BoxLayout.Y_AXIS));
        spacing.setOpaque(false);
        spacing.setBorder(BorderFactory.createEmptyBorder(4, 0, 8, 0));
        spacing.add(card);
        setBlockSize(spacing, spacing.getPreferredSize().height);
        add(spacing);
    }

    private void setBlockSize(JComponent component, int height) {
        int blockHeight = Math.max(1, height);
        component.setAlignmentX(LEFT_ALIGNMENT);
        component.setPreferredSize(new Dimension(contentWidth, blockHeight));
        component.setMinimumSize(new Dimension(1, blockHeight));
        component.setMaximumSize(new Dimension(contentWidth, blockHeight));
    }

    /** Lets long URLs and identifiers wrap inside a capped message bubble. */
    private static final class WrappingHtmlEditorKit extends HTMLEditorKit {
        private final ViewFactory factory = new HTMLFactory() {
            @Override
            public View create(Element element) {
                View view = super.create(element);
                if (view instanceof InlineView) {
                    return new InlineView(element) {
                        @Override
                        public float getMinimumSpan(int axis) {
                            return axis == View.X_AXIS ? 0 : super.getMinimumSpan(axis);
                        }

                        @Override
                        public int getBreakWeight(int axis, float position, float length) {
                            int weight = super.getBreakWeight(axis, position, length);
                            return axis == View.X_AXIS && weight == View.BadBreakWeight
                                    ? View.GoodBreakWeight : weight;
                        }
                    };
                }
                return view;
            }
        };

        @Override
        public ViewFactory getViewFactory() {
            return factory;
        }
    }

    private String escapeAttribute(String text) {
        return text.replace("&", "")
                .replace("\"", "")
                .replace("<", "")
                .replace(">", "");
    }
}
