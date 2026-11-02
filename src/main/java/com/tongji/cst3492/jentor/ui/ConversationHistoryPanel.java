package com.tongji.cst3492.jentor.ui;

import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.JBUI;
import com.tongji.cst3492.jentor.conversation.ConversationSummary;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Compact project-scoped conversation list shown beside the transcript. */
final class ConversationHistoryPanel extends JPanel {
    interface Listener {
        void open(ConversationSummary summary);

        void rename(ConversationSummary summary);

        void delete(ConversationSummary summary);
    }

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("MM-dd");

    private final Listener listener;
    private final DefaultListModel<ConversationSummary> model = new DefaultListModel<>();
    private final JBList<ConversationSummary> list = new JBList<>(model);
    private final JBLabel headerTitle = new JBLabel();
    private final JBTextField search = new JBTextField();
    private final JPopupMenu contextMenu = new JPopupMenu();
    private final JMenuItem renameItem = new JMenuItem();
    private final JMenuItem deleteItem = new JMenuItem();
    private List<ConversationSummary> allSummaries = List.of();
    private String activeId = "";
    private String language = "zh";

    ConversationHistoryPanel(Listener listener) {
        super(new BorderLayout(0, 6));
        this.listener = listener;
        setOpaque(false);
        setPreferredSize(new Dimension(210, 0));
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 0, 1, JBColor.border()),
                JBUI.Borders.empty(0, 0, 0, 8)));

        JPanel header = new JPanel(new BorderLayout(0, 4));
        header.setOpaque(false);
        headerTitle.setFont(headerTitle.getFont().deriveFont(Font.BOLD, 12f));
        header.add(headerTitle, BorderLayout.NORTH);
        header.add(search, BorderLayout.SOUTH);
        add(header, BorderLayout.NORTH);

        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setFixedCellHeight(54);
        list.setCellRenderer(new HistoryCellRenderer());
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                int index = list.locationToIndex(event.getPoint());
                if (index >= 0) {
                    list.setSelectedIndex(index);
                }
                if (event.isPopupTrigger() && index >= 0) {
                    showContextMenu(event);
                }
            }

            @Override
            public void mouseReleased(MouseEvent event) {
                if (event.isPopupTrigger()) {
                    showContextMenu(event);
                }
            }

            @Override
            public void mouseClicked(MouseEvent event) {
                if (!event.isPopupTrigger() && event.getButton() == MouseEvent.BUTTON1
                        && list.getSelectedValue() != null) {
                    listener.open(list.getSelectedValue());
                }
            }
        });
        add(new JBScrollPane(list), BorderLayout.CENTER);

        renameItem.addActionListener(event -> {
            ConversationSummary selected = list.getSelectedValue();
            if (selected != null) {
                listener.rename(selected);
            }
        });
        deleteItem.addActionListener(event -> {
            ConversationSummary selected = list.getSelectedValue();
            if (selected != null) {
                listener.delete(selected);
            }
        });
        contextMenu.add(renameItem);
        contextMenu.add(deleteItem);

        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                filter();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                filter();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                filter();
            }
        });
        applyLanguage("zh");
    }

    void setSummaries(List<ConversationSummary> summaries) {
        allSummaries = summaries == null ? List.of() : List.copyOf(summaries);
        filter();
    }

    void setActiveId(String id) {
        activeId = id == null ? "" : id;
        selectActive();
    }

    void applyLanguage(String language) {
        this.language = "en".equals(language) ? "en" : "zh";
        headerTitle.setText("en".equals(this.language) ? "History" : "历史");
        renameItem.setText("en".equals(this.language) ? "Rename" : "重命名");
        deleteItem.setText("en".equals(this.language) ? "Delete" : "删除");
        search.setToolTipText("en".equals(this.language)
                ? "Search conversations" : "搜索对话");
        list.setEmptyText("en".equals(this.language)
                ? "No conversations yet" : "暂无历史对话");
        list.repaint();
    }

    private void showContextMenu(MouseEvent event) {
        if (list.getSelectedIndex() < 0) {
            return;
        }
        contextMenu.show(list, event.getX(), event.getY());
    }

    private void filter() {
        String query = search.getText() == null
                ? "" : search.getText().trim().toLowerCase(Locale.ROOT);
        List<ConversationSummary> filtered = new ArrayList<>();
        for (ConversationSummary summary : allSummaries) {
            if (query.isEmpty()
                    || summary.title().toLowerCase(Locale.ROOT).contains(query)
                    || summary.preview().toLowerCase(Locale.ROOT).contains(query)) {
                filtered.add(summary);
            }
        }
        model.clear();
        filtered.forEach(model::addElement);
        selectActive();
    }

    private void selectActive() {
        if (activeId.isBlank()) {
            list.clearSelection();
            return;
        }
        for (int index = 0; index < model.size(); index++) {
            if (activeId.equals(model.get(index).id())) {
                list.setSelectedIndex(index);
                list.ensureIndexIsVisible(index);
                return;
            }
        }
        list.clearSelection();
    }

    private static String updatedLabel(long timestamp) {
        Instant instant = Instant.ofEpochMilli(timestamp);
        long age = System.currentTimeMillis() - timestamp;
        DateTimeFormatter formatter = age < 24L * 60L * 60L * 1000L
                ? TIME_FORMAT : DATE_FORMAT;
        return formatter.format(instant.atZone(ZoneId.systemDefault()));
    }

    private final class HistoryCellRenderer extends JPanel
            implements ListCellRenderer<ConversationSummary> {
        private final JBLabel title = new JBLabel();
        private final JBLabel preview = new JBLabel();
        private final JBLabel updated = new JBLabel();

        private HistoryCellRenderer() {
            super(new BorderLayout(4, 1));
            setBorder(JBUI.Borders.empty(5, 6));
            title.setFont(title.getFont().deriveFont(Font.BOLD, 11f));
            preview.setForeground(JBColor.GRAY);
            preview.setFont(preview.getFont().deriveFont(10f));
            updated.setForeground(JBColor.GRAY);
            updated.setFont(updated.getFont().deriveFont(9f));
            JPanel text = new JPanel(new BorderLayout(0, 1));
            text.setOpaque(false);
            text.add(title, BorderLayout.NORTH);
            text.add(preview, BorderLayout.SOUTH);
            add(text, BorderLayout.CENTER);
            add(updated, BorderLayout.EAST);
        }

        @Override
        public Component getListCellRendererComponent(
                JList<? extends ConversationSummary> source,
                ConversationSummary value,
                int index,
                boolean selected,
                boolean hasFocus) {
            title.setText(value.title());
            preview.setText(value.preview().isBlank()
                    ? (value.messageCount() + ("en".equals(language)
                    ? " messages" : " 条消息"))
                    : value.preview());
            updated.setText(ConversationHistoryPanel.updatedLabel(value.updatedAt()));
            Color background = selected
                    ? source.getSelectionBackground()
                    : source.getBackground();
            setBackground(background);
            setOpaque(true);
            title.setForeground(selected
                    ? source.getSelectionForeground() : source.getForeground());
            preview.setForeground(selected
                    ? source.getSelectionForeground() : JBColor.GRAY);
            updated.setForeground(selected
                    ? source.getSelectionForeground() : JBColor.GRAY);
            return this;
        }
    }
}
