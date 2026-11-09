package com.tongji.cst3492.jentor.ui;

import javax.swing.AbstractButton;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.LayoutManager;

/** Keeps composer actions visible while letting Swing ellipsize their text. */
final class ComposerFooterLayout implements LayoutManager {
    private final AbstractButton workspace;
    private final AbstractButton model;
    private final Component send;
    private final int gap;

    ComposerFooterLayout(AbstractButton workspace, AbstractButton model, Component send, int gap) {
        this.workspace = workspace;
        this.model = model;
        this.send = send;
        this.gap = gap;
    }

    @Override
    public void addLayoutComponent(String name, Component component) {
    }

    @Override
    public void removeLayoutComponent(Component component) {
    }

    @Override
    public Dimension preferredLayoutSize(Container parent) {
        Insets insets = parent.getInsets();
        Dimension left = workspace.getPreferredSize();
        Dimension middle = model.getPreferredSize();
        Dimension right = send.getPreferredSize();
        return new Dimension(insets.left + insets.right + left.width + middle.width
                + right.width + 2 * gap, insets.top + insets.bottom
                + Math.max(left.height, Math.max(middle.height, right.height)));
    }

    @Override
    public Dimension minimumLayoutSize(Container parent) {
        Insets insets = parent.getInsets();
        return new Dimension(insets.left + insets.right + workspaceIconWidth()
                + send.getPreferredSize().width + 2 * gap, preferredLayoutSize(parent).height);
    }

    @Override
    public void layoutContainer(Container parent) {
        Insets insets = parent.getInsets();
        int width = Math.max(0, parent.getWidth() - insets.left - insets.right);
        int height = Math.max(0, parent.getHeight() - insets.top - insets.bottom);
        int sendWidth = Math.min(width, send.getPreferredSize().width);
        int iconWidth = workspaceIconWidth();
        int actualGap = Math.min(gap, Math.max(0, (width - sendWidth - iconWidth) / 2));
        int flexibleWidth = Math.max(0, width - sendWidth - 2 * actualGap);
        // Shorten the workspace label first, then the model name if necessary.
        int modelWidth = Math.min(model.getPreferredSize().width,
                Math.max(0, flexibleWidth - Math.min(iconWidth, flexibleWidth)));
        int workspaceWidth = Math.min(workspace.getPreferredSize().width,
                flexibleWidth - modelWidth);
        int sendX = insets.left + width - sendWidth;
        place(workspace, insets.left, workspaceWidth, insets.top, height);
        place(model, sendX - actualGap - modelWidth, modelWidth, insets.top, height);
        place(send, sendX, sendWidth, insets.top, height);
    }

    private int workspaceIconWidth() {
        String text = workspace.getText();
        int textWidth = text == null ? 0
                : workspace.getFontMetrics(workspace.getFont()).stringWidth(text);
        return Math.max(0, workspace.getPreferredSize().width - textWidth);
    }

    private static void place(Component component, int x, int width, int top, int height) {
        int componentHeight = Math.min(height, component.getPreferredSize().height);
        component.setBounds(x, top + (height - componentHeight) / 2, width, componentHeight);
    }
}
