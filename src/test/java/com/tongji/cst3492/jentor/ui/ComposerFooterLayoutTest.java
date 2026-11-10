package com.tongji.cst3492.jentor.ui;

import org.junit.jupiter.api.Test;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JPanel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComposerFooterLayoutTest {
    @Test
    void keepsAllControlsSeparatedAtNormalWidth() {
        JCheckBox workspace = new JCheckBox("Include relevant workspace code");
        JButton model = new JButton("deepseek-chat");
        JButton send = sendButton();
        JPanel panel = panel(workspace, model, send, 600);

        panel.doLayout();

        assertEquals(workspace.getPreferredSize().width, workspace.getWidth());
        assertEquals(model.getPreferredSize().width, model.getWidth());
        assertDoesNotOverlap(workspace, model);
        assertDoesNotOverlap(model, send);
    }

    @Test
    void shortensTextControlsBeforeTheyCanOverlapInANarrowPanel() {
        JCheckBox workspace = new JCheckBox("Include relevant workspace code");
        JButton model = new JButton("deepseek-chat");
        JButton send = sendButton();
        JPanel panel = panel(workspace, model, send, 180);

        panel.doLayout();

        assertTrue(workspace.getWidth() < workspace.getPreferredSize().width);
        assertTrue(workspace.getWidth() > 0);
        assertEquals(send.getPreferredSize().width, send.getWidth());
        assertDoesNotOverlap(workspace, model);
        assertDoesNotOverlap(model, send);
        assertInsidePanel(panel, workspace, model, send);
    }

    @Test
    void preservesTheSendControlAtExtremelyNarrowWidths() {
        JCheckBox workspace = new JCheckBox("Include relevant workspace code");
        JButton model = new JButton("deepseek-chat");
        JButton send = sendButton();
        JPanel panel = panel(workspace, model, send, 48);

        panel.doLayout();

        assertEquals(send.getPreferredSize().width, send.getWidth());
        assertTrue(workspace.getWidth() >= 0);
        assertTrue(model.getWidth() >= 0);
        assertDoesNotOverlap(workspace, model);
        assertDoesNotOverlap(model, send);
        assertInsidePanel(panel, workspace, model, send);
    }

    private static JPanel panel(
            JCheckBox workspace, JButton model, JButton send, int width) {
        JPanel panel = new JPanel(new ComposerFooterLayout(workspace, model, send, 6));
        panel.add(workspace);
        panel.add(model);
        panel.add(send);
        panel.setSize(width, 40);
        return panel;
    }

    private static JButton sendButton() {
        JButton send = new JButton("send");
        send.setPreferredSize(new java.awt.Dimension(24, 24));
        return send;
    }

    private static void assertDoesNotOverlap(
            java.awt.Component left, java.awt.Component right) {
        assertFalse(left.getX() + left.getWidth() > right.getX(),
                () -> "Controls overlap: " + bounds(left) + " and " + bounds(right));
    }

    private static void assertInsidePanel(
            JPanel panel, JCheckBox workspace, JButton model, JButton send) {
        for (var component : new java.awt.Component[]{workspace, model, send}) {
            assertTrue(component.getX() >= 0, () -> "Negative x: " + bounds(component));
            assertTrue(component.getX() + component.getWidth() <= panel.getWidth(),
                    () -> "Control exceeds panel: " + bounds(component));
        }
    }

    private static String bounds(java.awt.Component component) {
        return component.getClass().getSimpleName() + "[x=" + component.getX()
                + ", w=" + component.getWidth() + "]";
    }
}
