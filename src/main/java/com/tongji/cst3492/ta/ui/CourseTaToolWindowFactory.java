package com.tongji.cst3492.ta.ui;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import java.awt.BorderLayout;
import java.io.PrintWriter;
import java.io.StringWriter;

public final class CourseTaToolWindowFactory implements ToolWindowFactory {
    private static final Logger LOG = Logger.getInstance(CourseTaToolWindowFactory.class);

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        try {
            ChatPanel panel = new ChatPanel(project);
            Content content = ContentFactory.getInstance().createContent(panel, "", false);
            content.setDisposer(panel);
            toolWindow.getContentManager().addContent(content);
        } catch (Throwable error) {
            LOG.error("CST3492 Course TA failed to create its tool window", error);
            Content content = ContentFactory.getInstance().createContent(
                    createErrorPanel(error), "", false);
            toolWindow.getContentManager().addContent(content);
        }
    }

    private JPanel createErrorPanel(Throwable error) {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(JBUI.Borders.empty(8));
        JLabel label = new JLabel(
                "CST3492 Course TA failed to start. See the stack trace below.");
        label.setForeground(JBColor.RED);
        panel.add(label, BorderLayout.NORTH);

        StringWriter writer = new StringWriter();
        error.printStackTrace(new PrintWriter(writer));
        JTextArea text = new JTextArea(writer.toString());
        text.setEditable(false);
        text.setLineWrap(false);
        panel.add(new JBScrollPane(text), BorderLayout.CENTER);
        return panel;
    }
}
