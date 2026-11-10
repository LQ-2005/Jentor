package com.tongji.cst3492.jentor.service;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.util.Alarm;
import com.tongji.cst3492.jentor.context.CourseLibraryStore;
import com.tongji.cst3492.jentor.context.CourseDocumentParser;
import com.tongji.cst3492.jentor.context.OcrEngine;
import com.tongji.cst3492.jentor.context.PdfCourseIndexBuilder;
import com.tongji.cst3492.jentor.model.CourseDocumentSource;
import com.tongji.cst3492.jentor.model.CourseIndex;
import com.tongji.cst3492.jentor.model.CourseLibraryCatalog;
import com.tongji.cst3492.jentor.settings.TaSettings;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Application-wide manager for independently imported course PDFs. Files may
 * live in unrelated folders; only their source paths are recorded and the
 * originals are never modified or deleted.
 */
@Service(Service.Level.APP)
public final class CourseLibraryService implements Disposable {
    private static final Logger LOG = Logger.getInstance(CourseLibraryService.class);
    private static final int SWEEP_INTERVAL_MS = 30_000;
    private static final int CHANGE_DEBOUNCE_MS = 800;

    private final Object lock = new Object();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(
            task -> {
                Thread thread = new Thread(task, "Jentor course library");
                thread.setDaemon(true);
                return thread;
            });
    private final AtomicInteger queuedOperations = new AtomicInteger();
    private final Alarm sweepAlarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, this);
    private final Alarm changeAlarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, this);
    private final List<CourseDocumentSource> sources = new ArrayList<>();
    private final Map<String, PdfCourseIndexBuilder.ParsedDocument> parsedDocuments =
            new LinkedHashMap<>();

    private volatile CourseIndex index;
    private volatile String lastError = "";
    private volatile String lastMessage = "";
    private volatile long revision;
    private volatile boolean started;

    public CourseLibraryService() {
        OcrEngine.configureStorageRoot(PathManager.getSystemDir().resolve("jentor-ocr"));
        ApplicationManager.getApplication().getMessageBus().connect(this)
                .subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
                    @Override
                    public void after(@NotNull List<? extends VFileEvent> events) {
                        onChanges(events);
                    }
                });
        scheduleSweep();
    }

    public static CourseLibraryService getInstance() {
        return ApplicationManager.getApplication().getService(CourseLibraryService.class);
    }

    public void ensureStarted() {
        if (started) {
            return;
        }
        List<Path> migrated = new ArrayList<>();
        synchronized (lock) {
            if (started) {
                return;
            }
            started = true;
            CourseLibraryCatalog catalog = CourseLibraryStore.loadCatalog();
            sources.addAll(catalog.documents());
            CourseIndex cachedIndex = CourseLibraryStore.loadIndex();
            index = cachedIndex;
            if (cachedIndex != null) {
                for (PdfCourseIndexBuilder.ParsedDocument parsed :
                        PdfCourseIndexBuilder.fromIndex(cachedIndex)) {
                    parsedDocuments.put(parsed.document().getId(), parsed);
                }
            }
            for (int i = 0; i < sources.size(); i++) {
                CourseDocumentSource source = sources.get(i);
                if (!Files.isRegularFile(Path.of(source.path()))) {
                    sources.set(i, source.withStatus(CourseDocumentSource.MISSING,
                            "Source PDF is missing"));
                } else if (!CourseDocumentSource.READY.equals(source.status())
                        || !parsedDocuments.containsKey(source.id())) {
                    sources.set(i, source.withStatus(CourseDocumentSource.QUEUED, ""));
                }
            }

            TaSettings.State settings = TaSettings.getInstance().getState();
            String legacyFolder = settings.courseMaterialsPath;
            if (legacyFolder != null && !legacyFolder.isBlank() && Files.isDirectory(
                    Path.of(legacyFolder))) {
                try {
                    migrated.addAll(CourseDocumentParser.listSupportedFiles(Path.of(legacyFolder)));
                } catch (IOException error) {
                    lastError = error.getMessage();
                }
                settings.courseMaterialsPath = "";
            }
        }
        if (!migrated.isEmpty()) {
            addFiles(migrated);
        } else {
            enqueue(this::reconcileAll);
        }
    }

    public void addFiles(List<Path> files) {
        ensureStarted();
        if (files == null || files.isEmpty()) {
            return;
        }
        List<Path> requested = files.stream()
                .filter(path -> path != null && Files.isRegularFile(path)
                        && CourseDocumentParser.isSupported(path))
                .map(path -> path.toAbsolutePath().normalize())
                .distinct()
                .toList();
        if (requested.isEmpty()) {
            lastMessage = "No valid PDF files were selected";
            return;
        }
        enqueue(() -> addFilesInternal(requested));
    }

    public void addFolder(Path folder) {
        if (folder == null || !Files.isDirectory(folder)) {
            lastMessage = "The selected course folder does not exist";
            return;
        }
        try {
            addFiles(CourseDocumentParser.listSupportedFiles(folder));
        } catch (IOException error) {
            lastMessage = "Cannot read course folder: " + error.getMessage();
        }
    }

    public void refreshAll() {
        ensureStarted();
        enqueue(this::reconcileAll);
    }

    public void rebuild(String sourceId) {
        ensureStarted();
        enqueue(() -> reconcileSource(sourceId));
    }

    public void remove(String sourceId) {
        ensureStarted();
        enqueue(() -> {
            synchronized (lock) {
                sources.removeIf(source -> source.id().equals(sourceId));
                parsedDocuments.remove(sourceId);
            }
            persistCatalog();
            rebuildAggregate();
            lastMessage = "Removed course material from Jentor; the source PDF was not deleted";
        });
    }

    public void relocate(String sourceId, Path newPath) {
        ensureStarted();
        if (newPath == null || !Files.isRegularFile(newPath)) {
            lastMessage = "The selected replacement file does not exist";
            return;
        }
        Path normalized = newPath.toAbsolutePath().normalize();
        enqueue(() -> {
            synchronized (lock) {
                int index = sourceIndex(sourceId);
                if (index < 0) {
                    return;
                }
                CourseDocumentSource source = sources.get(index);
                sources.set(index, new CourseDocumentSource(
                        source.id(), normalized.toString(), normalized.getFileName().toString(),
                        CourseDocumentParser.fileType(normalized),
                        "", 0L, 0L, source.pageCount(), source.chunkCount(),
                        source.tableCount(), source.imageCount(), source.ocrUsed(),
                        source.parser(), CourseDocumentSource.QUEUED, "", source.indexedAt()));
            }
            persistCatalog();
            reconcileSource(sourceId);
            lastMessage = "Relocated course material: " + normalized;
        });
    }

    public List<CourseDocumentSource> getSources() {
        synchronized (lock) {
            return List.copyOf(sources);
        }
    }

    public CourseDocumentSource getSource(String sourceId) {
        synchronized (lock) {
            int index = sourceIndex(sourceId);
            return index < 0 ? null : sources.get(index);
        }
    }

    public CourseDocumentSource sourceForDocumentId(String documentId) {
        return getSource(documentId);
    }

    public CourseIndex getIndex() {
        return index;
    }

    public long getRevision() {
        return revision;
    }

    public boolean isBusy() {
        return queuedOperations.get() > 0;
    }

    public String getLastError() {
        return lastError;
    }

    public String getLastMessage() {
        return lastMessage;
    }

    private void addFilesInternal(List<Path> paths) {
        boolean changed = false;
        for (Path path : paths) {
            try {
                String hash = sha256(path);
                CourseDocumentSource samePath = findByPath(path);
                if (samePath != null) {
                    synchronized (lock) {
                        int index = sourceIndex(samePath.id());
                        if (index >= 0) {
                            sources.set(index, new CourseDocumentSource(
                                    samePath.id(), path.toString(),
                                    path.getFileName().toString(),
                                    CourseDocumentParser.fileType(path), hash,
                                    Files.size(path),
                                    Files.getLastModifiedTime(path).toMillis(),
                                    samePath.pageCount(), samePath.chunkCount(),
                                    samePath.tableCount(), samePath.imageCount(),
                                    samePath.ocrUsed(), samePath.parser(),
                                    CourseDocumentSource.QUEUED, "", samePath.indexedAt()));
                        }
                    }
                    changed = true;
                    persistCatalog();
                    reconcileSource(samePath.id());
                    lastMessage = "Updated course material: " + path.getFileName();
                    continue;
                }
                CourseDocumentSource existing = findDuplicate(hash);
                if (existing != null) {
                    lastMessage = "Already in Jentor: " + path.getFileName()
                            + " (" + existing.displayName() + ")";
                    continue;
                }
                String id = uniqueSourceId(hash, path);
                CourseDocumentSource source = new CourseDocumentSource(
                        id,
                        path.toString(),
                        path.getFileName().toString(),
                        CourseDocumentParser.fileType(path),
                        hash,
                        Files.size(path),
                        Files.getLastModifiedTime(path).toMillis(),
                        0, 0, 0, 0, false, "",
                        CourseDocumentSource.QUEUED, "", 0L);
                synchronized (lock) {
                    sources.add(source);
                }
                changed = true;
                persistCatalog();
                reconcileSource(id);
                lastMessage = "Added course material: " + source.displayName();
            } catch (IOException | RuntimeException error) {
                lastError = "Cannot add " + path.getFileName() + ": " + error.getMessage();
            }
        }
        if (changed) {
            persistCatalog();
            rebuildAggregate();
        }
    }

    private void reconcileAll() {
        List<String> ids;
        synchronized (lock) {
            ids = sources.stream().map(CourseDocumentSource::id).toList();
        }
        for (String id : ids) {
            reconcileSource(id);
        }
    }

    private void reconcileSource(String sourceId) {
        CourseDocumentSource source;
        synchronized (lock) {
            int index = sourceIndex(sourceId);
            if (index < 0) {
                return;
            }
            source = sources.get(index);
        }
        Path path = Path.of(source.path());
        if (!Files.isRegularFile(path)) {
            updateSource(source.withStatus(CourseDocumentSource.MISSING,
                    "Source PDF is missing"));
            persistCatalog();
            return;
        }
        try {
            long size = Files.size(path);
            long modified = Files.getLastModifiedTime(path).toMillis();
            boolean oldParsed = false;
            synchronized (lock) {
                oldParsed = parsedDocuments.containsKey(sourceId);
            }
            if (oldParsed && size == source.size() && modified == source.modifiedAt()
                    && CourseDocumentSource.READY.equals(source.status())) {
                return;
            }
            updateSource(source.withStatus(CourseDocumentSource.INDEXING, ""));
            PdfCourseIndexBuilder.ParsedDocument parsed =
                    CourseDocumentParser.parse(path, source.id());
            String hash = sha256(path);
            CourseDocumentSource ready = source.withParsed(
                    hash,
                    size,
                    modified,
                    parsed.document().getPageCount(),
                    parsed.chunks().size(),
                    parsed.tableCount(),
                    parsed.imageCount(),
                    parsed.ocrUsed(),
                    parsed.parser(),
                    System.currentTimeMillis());
            synchronized (lock) {
                int index = sourceIndex(sourceId);
                if (index >= 0) {
                    sources.set(index, ready);
                    parsedDocuments.put(sourceId, parsed);
                }
            }
            persistCatalog();
            rebuildAggregate();
        } catch (Throwable error) {
            LOG.warn("Cannot index Jentor course PDF " + path, error);
            updateSource(source.withStatus(CourseDocumentSource.ERROR,
                    error.getMessage() == null ? error.getClass().getSimpleName()
                            : error.getMessage()));
            persistCatalog();
            lastError = "Cannot index " + source.displayName() + ": " + error.getMessage();
        }
    }

    private void rebuildAggregate() {
        List<PdfCourseIndexBuilder.ParsedDocument> documents;
        synchronized (lock) {
            documents = sources.stream()
                    .map(source -> parsedDocuments.get(source.id()))
                    .filter(parsed -> parsed != null)
                    .toList();
        }
        try {
            if (documents.isEmpty()) {
                index = null;
                CourseLibraryStore.clearIndex();
            } else {
                index = PdfCourseIndexBuilder.aggregate(documents);
                CourseLibraryStore.saveIndex(index);
            }
            revision++;
            lastError = "";
        } catch (Throwable error) {
            LOG.warn("Cannot rebuild aggregate Jentor course index", error);
            lastError = "Cannot rebuild course index: " + error.getMessage();
        }
    }

    private void updateSource(CourseDocumentSource updated) {
        synchronized (lock) {
            int index = sourceIndex(updated.id());
            if (index >= 0) {
                sources.set(index, updated);
            }
        }
    }

    private void persistCatalog() {
        try {
            CourseLibraryStore.saveCatalog(new CourseLibraryCatalog(1, getSources()));
        } catch (IOException error) {
            LOG.warn("Cannot persist Jentor course catalog", error);
            lastError = "Cannot save course catalog: " + error.getMessage();
        }
    }

    private CourseDocumentSource findDuplicate(String hash) {
        synchronized (lock) {
            for (CourseDocumentSource source : sources) {
                if (hash.equals(source.contentHash())) {
                    return source;
                }
            }
            return null;
        }
    }

    private CourseDocumentSource findByPath(Path path) {
        String normalized = path.toAbsolutePath().normalize().toString();
        synchronized (lock) {
            for (CourseDocumentSource source : sources) {
                if (Path.of(source.path()).toAbsolutePath().normalize()
                        .toString().equals(normalized)) {
                    return source;
                }
            }
            return null;
        }
    }

    private String uniqueSourceId(String hash, Path path) {
        String base = "doc-" + hash.substring(0, Math.min(16, hash.length()));
        String candidate = base;
        int suffix = 1;
        synchronized (lock) {
            Set<String> ids = new LinkedHashSet<>();
            sources.forEach(source -> ids.add(source.id()));
            while (ids.contains(candidate)) {
                candidate = base + "-" + suffix++;
            }
        }
        return candidate;
    }

    private int sourceIndex(String sourceId) {
        for (int i = 0; i < sources.size(); i++) {
            if (sources.get(i).id().equals(sourceId)) {
                return i;
            }
        }
        return -1;
    }

    private void enqueue(Runnable operation) {
        queuedOperations.incrementAndGet();
        try {
            worker.execute(() -> {
                try {
                    operation.run();
                } catch (Throwable error) {
                    LOG.warn("Jentor course library operation failed", error);
                    lastError = error.getMessage() == null
                            ? error.getClass().getSimpleName()
                            : error.getMessage();
                } finally {
                    queuedOperations.decrementAndGet();
                }
            });
        } catch (RejectedExecutionException error) {
            queuedOperations.decrementAndGet();
            lastError = "The course library service is not available.";
            LOG.warn(lastError, error);
        }
    }

    private void onChanges(List<? extends VFileEvent> events) {
        Map<String, String> paths = knownPaths();
        if (paths.isEmpty()) {
            return;
        }
        for (VFileEvent event : events) {
            if (event.getPath() == null) {
                continue;
            }
            Path eventPath = Path.of(event.getPath());
            if (!CourseDocumentParser.hasSupportedExtension(eventPath)) {
                continue;
            }
            String normalized = eventPath.toAbsolutePath().normalize().toString();
            if (paths.containsKey(normalized)) {
                changeAlarm.cancelAllRequests();
                changeAlarm.addRequest(this::refreshAll, CHANGE_DEBOUNCE_MS);
                return;
            }
        }
    }

    private Map<String, String> knownPaths() {
        Map<String, String> result = new HashMap<>();
        synchronized (lock) {
            for (CourseDocumentSource source : sources) {
                result.put(Path.of(source.path()).toAbsolutePath().normalize().toString(),
                        source.id());
            }
        }
        return result;
    }

    private void scheduleSweep() {
        sweepAlarm.addRequest(() -> {
            try {
                if (!isBusy() && !sources.isEmpty()) {
                    enqueue(this::reconcileAll);
                }
            } finally {
                scheduleSweep();
            }
        }, SWEEP_INTERVAL_MS);
    }

    private static String sha256(Path path) throws IOException {
        MessageDigest digest = digest();
        try (InputStream input = Files.newInputStream(path)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read > 0) {
                    digest.update(buffer, 0, read);
                }
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is required", error);
        }
    }

    @Override
    public void dispose() {
        sweepAlarm.cancelAllRequests();
        changeAlarm.cancelAllRequests();
        worker.shutdownNow();
        synchronized (lock) {
            parsedDocuments.clear();
            sources.clear();
            index = null;
        }
    }
}
