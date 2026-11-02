package com.tongji.cst3492.jentor.ui;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.JBUI;
import com.tongji.cst3492.jentor.model.CourseDocumentSource;
import com.tongji.cst3492.jentor.service.CourseLibraryService;
import com.tongji.cst3492.jentor.settings.TaSettings;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.TransferHandler;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.AbstractTableModel;
import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.FlowLayout;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Dynamic manager for independently imported course PDFs. */
public final class CourseLibraryPanel extends JPanel implements Disposable {
    private final Project project;
    private final CourseLibraryService service;
    private final LibraryTableModel model = new LibraryTableModel();
    private final JBTable table = new JBTable(model);
    private final JTextArea details = new JTextArea();
    private final JBLabel status = new JBLabel();
    private final JButton addFiles = new JButton();
    private final JButton addFolder = new JButton();
    private final JButton refresh = new JButton();
    private final JButton rebuild = new JButton();
    private final JButton remove = new JButton();
    private final JButton openPdf = new JButton();
    private final JButton openFolder = new JButton();
    private final JButton locate = new JButton();
    private final JButton copyPath = new JButton();
    private final Timer refreshTimer;
    private String language;

    public CourseLibraryPanel(Project project) {
        super(new BorderLayout(0, 6));
        this.project = project;
        this.service = CourseLibraryService.getInstance();
        this.language = TaSettings.getInstance().getState().language;
        service.ensureStarted();
        setBorder(JBUI.Borders.empty(8));

        add(createToolbar(), BorderLayout.NORTH);
        add(createCenter(), BorderLayout.CENTER);
        add(status, BorderLayout.SOUTH);

        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoCreateRowSorter(true);
        table.getSelectionModel().addListSelectionListener(event -> updateDetails());
        installDropHandler();
        refreshTimer = new Timer(1000, event -> refreshFromService());
        refreshTimer.start();
        updateLabels();
        refreshFromService();
    }

    private JPanel createToolbar() {
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        configure(addFiles, this::chooseFiles);
        configure(addFolder, this::chooseFolder);
        configure(refresh, () -> service.refreshAll());
        configure(rebuild, this::rebuildSelected);
        configure(remove, this::removeSelected);
        configure(openPdf, () -> openSelected(false));
        configure(openFolder, () -> openSelected(true));
        configure(locate, this::locateSelected);
        configure(copyPath, this::copySelectedPath);
        toolbar.add(addFiles);
        toolbar.add(addFolder);
        toolbar.add(refresh);
        toolbar.add(rebuild);
        toolbar.add(remove);
        toolbar.add(openPdf);
        toolbar.add(openFolder);
        toolbar.add(locate);
        toolbar.add(copyPath);
        return toolbar;
    }

    private JSplitPane createCenter() {
        details.setEditable(false);
        details.setLineWrap(true);
        details.setWrapStyleWord(true);
        details.setOpaque(false);
        details.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        JBScrollPane tableScroll = new JBScrollPane(table);
        JBScrollPane detailsScroll = new JBScrollPane(details);
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, tableScroll, detailsScroll);
        split.setResizeWeight(0.72);
        split.setDividerLocation(280);
        return split;
    }

    private void chooseFiles() {
        JFileChooser chooser = new JFileChooser();
        chooser.setMultiSelectionEnabled(true);
        chooser.setFileFilter(new FileNameExtensionFilter("PDF course material", "pdf"));
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            service.addFiles(List.of(chooser.getSelectedFiles()).stream()
                    .map(File::toPath).toList());
        }
    }

    private void chooseFolder() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            service.addFolder(chooser.getSelectedFile().toPath());
        }
    }

    private void rebuildSelected() {
        CourseDocumentSource selected = selectedSource();
        if (selected != null) {
            service.rebuild(selected.id());
        }
    }

    private void removeSelected() {
        CourseDocumentSource selected = selectedSource();
        if (selected == null) {
            return;
        }
        int result = Messages.showYesNoDialog(project,
                text("从 Jentor 资料库移除该课程资料？原始 PDF 不会被删除。\n\n",
                        "Remove this course material from Jentor? The source PDF will not be deleted.\n\n")
                        + selected.displayName(),
                text("移除课程资料", "Remove course material"),
                Messages.getQuestionIcon());
        if (result == Messages.YES) {
            service.remove(selected.id());
        }
    }

    private void locateSelected() {
        CourseDocumentSource selected = selectedSource();
        if (selected == null || !CourseDocumentSource.MISSING.equals(selected.status())) {
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("PDF course material", "pdf"));
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            service.relocate(selected.id(), chooser.getSelectedFile().toPath());
        }
    }

    private void openSelected(boolean folder) {
        CourseDocumentSource selected = selectedSource();
        if (selected == null) {
            return;
        }
        Path path = Path.of(selected.path());
        Path target = folder ? path.getParent() : path;
        if (target == null || !Desktop.isDesktopSupported()) {
            return;
        }
        try {
            Desktop.getDesktop().open(target.toFile());
        } catch (Exception error) {
            Messages.showErrorDialog(project, error.getMessage(),
                    text("无法打开课程资料", "Cannot open course material"));
        }
    }

    private void copySelectedPath() {
        CourseDocumentSource selected = selectedSource();
        if (selected != null) {
            java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new java.awt.datatransfer.StringSelection(selected.path()), null);
        }
    }

    private CourseDocumentSource selectedSource() {
        int row = table.getSelectedRow();
        return row < 0 ? null : model.sourceAt(table.convertRowIndexToModel(row));
    }

    private void refreshFromService() {
        String currentLanguage = TaSettings.getInstance().getState().language;
        if (!Objects.equals(language, currentLanguage)) {
            language = currentLanguage;
            updateLabels();
            model.fireTableStructureChanged();
        }
        String selectedId = selectedSource() == null ? "" : selectedSource().id();
        model.setSources(service.getSources());
        if (!selectedId.isBlank()) {
            for (int row = 0; row < model.getRowCount(); row++) {
                if (model.sourceAt(row).id().equals(selectedId)) {
                    int viewRow = table.convertRowIndexToView(row);
                    if (viewRow >= 0) {
                        table.setRowSelectionInterval(viewRow, viewRow);
                    }
                    break;
                }
            }
        }
        if (service.isBusy()) {
            status.setText(text("正在处理课程资料…", "Updating course materials..."));
            status.setForeground(JBColor.GRAY);
        } else if (!service.getLastError().isBlank()) {
            status.setText(service.getLastError());
            status.setForeground(JBColor.RED);
        } else {
            status.setText(service.getLastMessage());
            status.setForeground(JBColor.GRAY);
        }
        updateDetails();
    }

    private void updateDetails() {
        CourseDocumentSource source = selectedSource();
        if (source == null) {
            details.setText(text("选择一份课程资料以查看详细信息。",
                    "Select a course material to inspect its details."));
            return;
        }
        details.setText(
                source.displayName() + "\n"
                        + text("原始位置：", "Original location: ") + source.path() + "\n"
                        + text("状态：", "Status: ") + statusText(source.status()) + "\n"
                        + text("页数：", "Pages: ") + source.pageCount()
                        + "    Chunks: " + source.chunkCount() + "\n"
                        + (source.error().isBlank() ? ""
                        : text("错误：", "Error: ") + source.error() + "\n")
                        + "\n" + text(
                        "移除资料不会删除原始 PDF。缺失文件可以先用“定位”重新关联。",
                        "Removing a material never deletes its source PDF. "
                                + "Use Locate to rebind a missing file."));
        details.setCaretPosition(0);
    }

    private void installDropHandler() {
        table.setTransferHandler(new TransferHandler() {
            @Override
            public boolean canImport(TransferSupport support) {
                return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
            }

            @Override
            public boolean importData(TransferSupport support) {
                try {
                    Transferable transferable = support.getTransferable();
                    @SuppressWarnings("unchecked")
                    List<File> files = (List<File>) transferable.getTransferData(
                            DataFlavor.javaFileListFlavor);
                    service.addFiles(files.stream().map(File::toPath).toList());
                    return true;
                } catch (Exception error) {
                    return false;
                }
            }
        });
    }

    private void updateLabels() {
        addFiles.setText(text("添加 PDF", "Add PDFs"));
        addFolder.setText(text("添加文件夹", "Add folder"));
        refresh.setText(text("刷新", "Refresh"));
        rebuild.setText(text("重新索引", "Rebuild"));
        remove.setText(text("移除资料", "Remove"));
        openPdf.setText(text("打开 PDF", "Open PDF"));
        openFolder.setText(text("打开所在文件夹", "Show folder"));
        locate.setText(text("定位缺失文件", "Locate"));
        copyPath.setText(text("复制路径", "Copy path"));
    }

    private String statusText(String value) {
        return switch (value) {
            case CourseDocumentSource.QUEUED -> text("排队中", "Queued");
            case CourseDocumentSource.INDEXING -> text("索引中", "Indexing");
            case CourseDocumentSource.READY -> text("已就绪", "Ready");
            case CourseDocumentSource.MISSING -> text("文件缺失", "Missing");
            default -> text("失败", "Error");
        };
    }

    private String text(String chinese, String english) {
        String language = TaSettings.getInstance().getState().language;
        return "en".equals(language) ? english : chinese;
    }

    private static void configure(JButton button, Runnable action) {
        button.setMargin(JBUI.insets(2, 6));
        button.addActionListener(event -> action.run());
    }

    @Override
    public void dispose() {
        refreshTimer.stop();
    }

    private final class LibraryTableModel extends AbstractTableModel {
        private List<CourseDocumentSource> rows = List.of();

        private void setSources(List<CourseDocumentSource> sources) {
            rows = List.copyOf(sources);
            fireTableDataChanged();
        }

        private CourseDocumentSource sourceAt(int row) {
            return rows.get(row);
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return 6;
        }

        @Override
        public String getColumnName(int column) {
            return switch (column) {
                case 0 -> text("状态", "Status");
                case 1 -> text("课程资料", "Material");
                case 2 -> text("原始位置", "Original location");
                case 3 -> text("页数", "Pages");
                case 4 -> "Chunks";
                default -> text("错误", "Error");
            };
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            CourseDocumentSource source = rows.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> statusText(source.status());
                case 1 -> source.displayName();
                case 2 -> source.path();
                case 3 -> source.pageCount() == 0 ? "" : source.pageCount();
                case 4 -> source.chunkCount() == 0 ? "" : source.chunkCount();
                default -> source.error();
            };
        }
    }
}
