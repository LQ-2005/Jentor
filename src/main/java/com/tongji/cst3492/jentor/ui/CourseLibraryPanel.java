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
import com.tongji.cst3492.jentor.service.CourseTaService;
import com.tongji.cst3492.jentor.settings.TaSettings;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
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
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Dynamic manager for independently imported course PDFs. */
public final class CourseLibraryPanel extends JPanel implements Disposable {
    private final Project project;
    private final CourseLibraryService service;
    private final CourseTaService taService;
    private final LibraryTableModel model = new LibraryTableModel();
    private final JBTable table = new JBTable(model);
    private final JBLabel status = new JBLabel();
    private final JButton addFiles = new JButton();
    private final JButton addFolder = new JButton();
    private final JButton refresh = new JButton();
    private final JButton openPdf = new JButton();
    private final JButton more = new JButton("⋯");
    private final JPopupMenu moreMenu = new JPopupMenu();
    private final JMenuItem rebuild = new JMenuItem();
    private final JMenuItem remove = new JMenuItem();
    private final JMenuItem openFolder = new JMenuItem();
    private final JMenuItem locate = new JMenuItem();
    private final JMenuItem copyPath = new JMenuItem();
    private final JMenuItem importIndex = new JMenuItem();
    private final Timer refreshTimer;
    private String language;

    public CourseLibraryPanel(Project project) {
        super(new BorderLayout(0, 6));
        this.project = project;
        this.service = CourseLibraryService.getInstance();
        CourseTaService resolvedTaService = project.getService(CourseTaService.class);
        this.taService = resolvedTaService == null
                ? new CourseTaService(project) : resolvedTaService;
        this.language = TaSettings.getInstance().getState().language;
        service.ensureStarted();
        setBorder(JBUI.Borders.empty(8));

        add(createToolbar(), BorderLayout.NORTH);
        add(createCenter(), BorderLayout.CENTER);
        add(status, BorderLayout.SOUTH);

        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoCreateRowSorter(true);
        table.setComponentPopupMenu(moreMenu);
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(event)
                        && table.getSelectedRow() >= 0) {
                    openSelected(false);
                }
            }
        });
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
        configure(openPdf, () -> openSelected(false));
        configure(rebuild, this::rebuildSelected);
        configure(remove, this::removeSelected);
        configure(openFolder, () -> openSelected(true));
        configure(locate, this::locateSelected);
        configure(copyPath, this::copySelectedPath);
        configure(importIndex, this::importCourseIndex);
        configure(more, () -> moreMenu.show(more, 0, more.getHeight()));
        moreMenu.add(rebuild);
        moreMenu.add(remove);
        moreMenu.addSeparator();
        moreMenu.add(openFolder);
        moreMenu.add(locate);
        moreMenu.add(copyPath);
        moreMenu.addSeparator();
        moreMenu.add(importIndex);
        toolbar.add(addFiles);
        toolbar.add(addFolder);
        toolbar.add(refresh);
        toolbar.add(openPdf);
        toolbar.add(more);
        return toolbar;
    }

    private JBScrollPane createCenter() {
        return new JBScrollPane(table);
    }

    private void chooseFiles() {
        JFileChooser chooser = new JFileChooser();
        chooser.setMultiSelectionEnabled(true);
        chooser.setFileFilter(new FileNameExtensionFilter(
                "Course materials",
                "pdf", "docx", "pptx", "xlsx", "doc", "ppt", "xls",
                "txt", "md", "markdown", "html", "htm", "csv",
                "png", "jpg", "jpeg", "bmp", "tif", "tiff"));
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
        chooser.setFileFilter(new FileNameExtensionFilter(
                "Course materials",
                "pdf", "docx", "pptx", "xlsx", "doc", "ppt", "xls",
                "txt", "md", "markdown", "html", "htm", "csv",
                "png", "jpg", "jpeg", "bmp", "tif", "tiff"));
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

    private void importCourseIndex() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(text("导入课程索引", "Import course index"));
        chooser.setFileFilter(new FileNameExtensionFilter("Course index (JSON)", "json"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        try {
            taService.loadIndex(chooser.getSelectedFile().toPath());
            status.setForeground(JBColor.GRAY);
            status.setText(text("已加载课程索引：", "Loaded course index: ")
                    + taService.getIndexSummary());
        } catch (Exception error) {
            Messages.showErrorDialog(project, error.getMessage(),
                    text("无法加载课程索引", "Cannot load course index"));
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
        addFiles.setText(text("添加资料", "Add files"));
        addFolder.setText(text("添加文件夹", "Add folder"));
        refresh.setText(text("刷新", "Refresh"));
        rebuild.setText(text("重新索引", "Rebuild"));
        remove.setText(text("移除资料", "Remove"));
        openPdf.setText(text("打开文件", "Open file"));
        more.setToolTipText(text("更多操作", "More actions"));
        openFolder.setText(text("打开所在文件夹", "Show folder"));
        locate.setText(text("定位缺失文件", "Locate"));
        copyPath.setText(text("复制路径", "Copy path"));
        importIndex.setText(text("导入课程索引", "Import course index"));
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

    private static void configure(JMenuItem item, Runnable action) {
        item.addActionListener(event -> action.run());
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
                case 2 -> text("类型", "Type");
                case 3 -> text("原始位置", "Original location");
                case 4 -> text("定位单元", "Locations");
                case 5 -> "Chunks";
                default -> "";
            };
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            CourseDocumentSource source = rows.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> statusText(source.status());
                case 1 -> source.displayName();
                case 2 -> displayType(source);
                case 3 -> source.path();
                case 4 -> source.pageCount() == 0 ? "" : source.pageCount();
                case 5 -> source.chunkCount() == 0 ? "" : source.chunkCount();
                default -> "";
            };
        }
    }

    private static String displayType(CourseDocumentSource source) {
        if (!source.fileType().isBlank()) {
            return source.fileType();
        }
        String path = source.path();
        int dot = path.lastIndexOf('.');
        return dot < 0 ? "" : path.substring(dot + 1)
                .toUpperCase(java.util.Locale.ROOT);
    }
}
