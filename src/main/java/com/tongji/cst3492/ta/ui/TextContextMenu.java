package com.tongji.cst3492.ta.ui;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.text.JTextComponent;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;

public final class TextContextMenu {
    private TextContextMenu() {
    }

    public static void install(JTextComponent component) {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem cut = new JMenuItem("剪切 / Cut");
        JMenuItem copy = new JMenuItem("复制 / Copy");
        JMenuItem paste = new JMenuItem("粘贴 / Paste");
        JMenuItem selectAll = new JMenuItem("全选 / Select all");
        JMenuItem copyAll = new JMenuItem("复制全部 / Copy all");

        cut.addActionListener(event -> component.cut());
        copy.addActionListener(event -> component.copy());
        paste.addActionListener(event -> component.paste());
        selectAll.addActionListener(event -> component.selectAll());
        copyAll.addActionListener(event -> Toolkit.getDefaultToolkit()
                .getSystemClipboard()
                .setContents(new StringSelection(component.getText()), null));

        menu.addPopupMenuListener(new PopupMenuListener() {
            @Override
            public void popupMenuWillBecomeVisible(PopupMenuEvent event) {
                boolean selection = component.getSelectionStart() != component.getSelectionEnd();
                cut.setEnabled(component.isEditable() && selection);
                copy.setEnabled(selection);
                paste.setEnabled(component.isEditable() && hasClipboardText());
                selectAll.setEnabled(component.getDocument().getLength() > 0);
            }

            @Override
            public void popupMenuWillBecomeInvisible(PopupMenuEvent event) {
            }

            @Override
            public void popupMenuCanceled(PopupMenuEvent event) {
            }
        });

        menu.add(cut);
        menu.add(copy);
        menu.add(copyAll);
        menu.add(paste);
        menu.addSeparator();
        menu.add(selectAll);
        component.setComponentPopupMenu(menu);
    }

    private static boolean hasClipboardText() {
        try {
            return Toolkit.getDefaultToolkit()
                    .getSystemClipboard()
                    .isDataFlavorAvailable(DataFlavor.stringFlavor);
        } catch (RuntimeException error) {
            return false;
        }
    }
}
