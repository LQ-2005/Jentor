package com.tongji.cst3492.jentor.ui;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBTabbedPane;

import javax.swing.JPanel;
import java.awt.BorderLayout;

/** Root tool-window content containing the chat and course-library tabs. */
public final class JentorToolWindowPanel extends JPanel implements Disposable {
    private final ChatPanel chatPanel;
    private final CourseLibraryPanel courseLibraryPanel;
    private final JBTabbedPane tabs = new JBTabbedPane();

    public JentorToolWindowPanel(Project project) {
        super(new BorderLayout());
        chatPanel = new ChatPanel(project);
        courseLibraryPanel = new CourseLibraryPanel(project);
        tabs.addTab("Jentor Chat", chatPanel);
        tabs.addTab("课程资料 / Course materials", courseLibraryPanel);
        add(tabs, BorderLayout.CENTER);
    }

    public ChatPanel chatPanel() {
        return chatPanel;
    }

    public void showCourseLibrary() {
        tabs.setSelectedIndex(1);
    }

    @Override
    public void dispose() {
        chatPanel.dispose();
        courseLibraryPanel.dispose();
    }
}
