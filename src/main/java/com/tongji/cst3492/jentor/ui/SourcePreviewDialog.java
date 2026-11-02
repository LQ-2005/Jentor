package com.tongji.cst3492.jentor.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import com.tongji.cst3492.jentor.model.CourseDocumentSource;
import com.tongji.cst3492.jentor.model.SourceReference;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.Locale;

/** Shows a cited page/image, source path and excerpt without requiring an external reader. */
final class SourcePreviewDialog extends DialogWrapper {
    private final Project project;
    private final SourceReference source;
    private final @Nullable CourseDocumentSource managedSource;

    SourcePreviewDialog(
            Project project,
            SourceReference source,
            @Nullable CourseDocumentSource managedSource) {
        super(true);
        this.project = project;
        this.source = source;
        this.managedSource = managedSource;
        setTitle("Jentor Source");
        init();
        setOKButtonText("Close");
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setBorder(JBUI.Borders.empty(6));
        Path path = managedSource == null ? null : Path.of(managedSource.path());
        BufferedImage preview = renderPreview(path);
        if (preview != null) {
            JLabel image = new JLabel(new ImageIcon(preview));
            JBScrollPane imageScroll = new JBScrollPane(image);
            imageScroll.setPreferredSize(new Dimension(920, 620));
            panel.add(imageScroll, BorderLayout.CENTER);
        }

        String details =
                "File: " + source.fileName() + "\n"
                        + "Location: " + source.locator() + "\n"
                        + (path == null ? "" : "Original: " + path + "\n")
                        + "\nExcerpt:\n" + source.excerpt();
        JTextArea text = new JTextArea(details);
        text.setEditable(false);
        text.setLineWrap(true);
        text.setWrapStyleWord(true);
        JBScrollPane textScroll = new JBScrollPane(text);
        textScroll.setPreferredSize(new Dimension(920, preview == null ? 360 : 145));
        panel.add(textScroll, BorderLayout.SOUTH);

        if (path != null) {
            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
            JButton open = new JButton("Open file");
            open.addActionListener(event -> openPath(path));
            JButton folder = new JButton("Show folder");
            folder.addActionListener(event -> openPath(path.getParent()));
            JButton copy = new JButton("Copy path");
            copy.addActionListener(event -> java.awt.Toolkit.getDefaultToolkit()
                    .getSystemClipboard().setContents(
                            new java.awt.datatransfer.StringSelection(path.toString()), null));
            buttons.add(open);
            buttons.add(folder);
            buttons.add(copy);
            panel.add(buttons, BorderLayout.NORTH);
        }
        return panel;
    }

    private @Nullable BufferedImage renderPreview(@Nullable Path path) {
        if (path == null || !java.nio.file.Files.isRegularFile(path)) {
            return null;
        }
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        try {
            BufferedImage image;
            if (name.endsWith(".pdf") && source.pageStart() > 0) {
                try (PDDocument document = Loader.loadPDF(path.toFile())) {
                    int pageIndex = Math.min(source.pageStart() - 1,
                            document.getNumberOfPages() - 1);
                    image = new PDFRenderer(document)
                            .renderImageWithDPI(pageIndex, 120, ImageType.RGB);
                }
            } else if (name.matches(".*\\.(png|jpe?g|bmp|gif)$")) {
                image = ImageIO.read(path.toFile());
            } else {
                return null;
            }
            if (image == null) {
                return null;
            }
            int maxWidth = 880;
            int maxHeight = 560;
            double scale = Math.min(1.0, Math.min(
                    maxWidth / (double) image.getWidth(),
                    maxHeight / (double) image.getHeight()));
            if (scale >= 1.0) {
                return image;
            }
            int width = Math.max(1, (int) Math.round(image.getWidth() * scale));
            int height = Math.max(1, (int) Math.round(image.getHeight() * scale));
            Image scaled = image.getScaledInstance(width, height, Image.SCALE_SMOOTH);
            BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            var graphics = result.createGraphics();
            try {
                graphics.drawImage(scaled, 0, 0, null);
            } finally {
                graphics.dispose();
            }
            return result;
        } catch (Throwable error) {
            return null;
        }
    }

    private void openPath(@Nullable Path path) {
        if (path == null || !Desktop.isDesktopSupported()) {
            return;
        }
        try {
            Desktop.getDesktop().open(path.toFile());
        } catch (Exception error) {
            Messages.showErrorDialog(project, error.getMessage(), "Jentor Source");
        }
    }
}
