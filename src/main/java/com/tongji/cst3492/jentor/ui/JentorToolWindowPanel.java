package com.tongji.cst3492.jentor.ui;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBTabbedPane;
import com.tongji.cst3492.jentor.settings.TaSettings;

import javax.swing.JPanel;
import javax.swing.Timer;
import java.awt.BorderLayout;

/** Root tool-window content containing the chat and course-library tabs. */
public final class JentorToolWindowPanel extends JPanel implements Disposable {
    private final ChatPanel chatPanel;
    private final CourseLibraryPanel courseLibraryPanel;
    private final JBTabbedPane tabs = new JBTabbedPane();
    private final Timer languageTimer;

    public JentorToolWindowPanel(Project project) {
        super(new BorderLayout());
        chatPanel = new ChatPanel(project);
        courseLibraryPanel = new CourseLibraryPanel(project);
        tabs.addTab("Jentor Chat", chatPanel);
        tabs.addTab(courseMaterialsTitle(), courseLibraryPanel);
        add(tabs, BorderLayout.CENTER);
        languageTimer = new Timer(1000, event -> updateLanguage());
        languageTimer.start();
    }

    public ChatPanel chatPanel() {
        return chatPanel;
    }

    public void showCourseLibrary() {
        tabs.setSelectedIndex(1);
    }

    private void updateLanguage() {
        String title = courseMaterialsTitle();
        if (!title.equals(tabs.getTitleAt(1))) {
            tabs.setTitleAt(1, title);
        }
    }

    private String courseMaterialsTitle() {
        return "en".equals(TaSettings.getInstance().getState().language)
                ? "Course materials" : "课程资料";
    }

    @Override
    public void dispose() {
        languageTimer.stop();
        chatPanel.dispose();
        courseLibraryPanel.dispose();
    }
}
