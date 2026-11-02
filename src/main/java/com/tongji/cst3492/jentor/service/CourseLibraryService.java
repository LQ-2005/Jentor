package com.tongji.cst3492.jentor.service;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.util.Alarm;
import com.tongji.cst3492.jentor.context.CourseLibraryStore;
import com.tongji.cst3492.jentor.context.CourseLibraryFingerprint;
import com.tongji.cst3492.jentor.context.PdfCourseIndexBuilder;
import com.tongji.cst3492.jentor.model.CourseIndex;
import com.tongji.cst3492.jentor.settings.TaSettings;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Application-wide managed course PDF library. It watches one configured folder,
 * rebuilds a full page-aware index off the EDT, keeps the previous index on
 * failure, and stores a validated cache under the IDE system directory.
 */
@Service(Service.Level.APP)
public final class CourseLibraryService implements Disposable {
    private static final Logger LOG = Logger.getInstance(CourseLibraryService.class);
    private static final int CHANGE_DEBOUNCE_MS = 800;
    private static final int SWEEP_INTERVAL_MS = 30_000;

    private final Object lock = new Object();
    private final AtomicBoolean building = new AtomicBoolean();
    private final AtomicLong generation = new AtomicLong();
    private final AtomicLong revision = new AtomicLong();
    private final Alarm refreshAlarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, this);
    private final Alarm sweepAlarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, this);

    private volatile String directory = "";
    private volatile CourseIndex index;
    private volatile String indexedFingerprint = "";
    private volatile String error = "";
    private volatile boolean started;

    public CourseLibraryService() {
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
        synchronized (lock) {
            if (started) {
                return;
            }
            started = true;
            String configured = TaSettings.getInstance().getState().courseMaterialsPath;
            directory = normalize(configured);
            if (directory.isBlank()) {
                return;
            }
            scheduleRebuild(0);
        }
    }

    public void configure(String configuredDirectory) {
        ensureStarted();
        String normalized = normalize(configuredDirectory);
        String previous;
        synchronized (lock) {
            previous = directory;
            directory = normalized;
            TaSettings.getInstance().getState().courseMaterialsPath = normalized;
            if (!previous.equals(normalized)) {
                index = null;
                indexedFingerprint = "";
                error = "";
                revision.incrementAndGet();
            }
        }
        if (normalized.isBlank()) {
            refreshAlarm.cancelAllRequests();
            return;
        }
        scheduleRebuild(0);
    }

    public void refreshAsync() {
        ensureStarted();
        if (!directory.isBlank()) {
            scheduleRebuild(0);
        }
    }

    public CourseIndex getIndex() {
        return index;
    }

    public String getDirectory() {
        return directory;
    }

    public String getError() {
        return error;
    }

    public boolean isConfigured() {
        return !directory.isBlank();
    }

    public boolean isIndexing() {
        return building.get();
    }

    public long getRevision() {
        return revision.get();
    }

    private void scheduleRebuild(long delayMs) {
        long request = generation.incrementAndGet();
        refreshAlarm.cancelAllRequests();
        refreshAlarm.addRequest(() -> rebuild(request), delayMs);
    }

    private void rebuild(long request) {
        if (!building.compareAndSet(false, true)) {
            scheduleRebuild(CHANGE_DEBOUNCE_MS);
            return;
        }
        String requestedDirectory = directory;
        try {
            if (requestedDirectory.isBlank()) {
                return;
            }
            Path path = Path.of(requestedDirectory);
            if (!Files.isDirectory(path)) {
                throw new IllegalStateException("Course materials directory does not exist: "
                        + requestedDirectory);
            }
            String before = CourseLibraryFingerprint.of(path);
            CourseIndex result = CourseLibraryStore.load(path, before);
            if (result == null) {
                result = PdfCourseIndexBuilder.build(path);
                String after = CourseLibraryFingerprint.of(path);
                if (!before.equals(after)) {
                    scheduleRebuild(CHANGE_DEBOUNCE_MS);
                    return;
                }
                CourseLibraryStore.save(path, result, before);
            } else {
                String after = CourseLibraryFingerprint.of(path);
                if (!before.equals(after)) {
                    scheduleRebuild(CHANGE_DEBOUNCE_MS);
                    return;
                }
            }
            if (requestedDirectory.equals(directory)) {
                index = result;
                indexedFingerprint = before;
                error = "";
                revision.incrementAndGet();
            }
        } catch (Throwable failure) {
            LOG.warn("Cannot rebuild the Jentor course library", failure);
            error = failure.getMessage() == null
                    ? failure.getClass().getSimpleName()
                    : failure.getMessage();
        } finally {
            building.set(false);
            if (request != generation.get() && !directory.isBlank()) {
                scheduleRebuild(CHANGE_DEBOUNCE_MS);
            }
        }
    }

    private void onChanges(List<? extends VFileEvent> events) {
        String configured = directory;
        if (configured.isBlank()) {
            return;
        }
        String normalizedDirectory = configured.replace('\\', '/');
        for (VFileEvent event : events) {
            String path = event.getPath() == null ? "" : event.getPath().replace('\\', '/');
            if (path.startsWith(normalizedDirectory + "/")
                    && path.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
                refreshAsync();
                return;
            }
        }
    }

    private void scheduleSweep() {
        sweepAlarm.addRequest(() -> {
            try {
                sweep();
            } finally {
                scheduleSweep();
            }
        }, SWEEP_INTERVAL_MS);
    }

    private void sweep() {
        String configured = directory;
        if (configured.isBlank() || building.get()) {
            return;
        }
        try {
            String current = CourseLibraryFingerprint.of(Path.of(configured));
            if (!current.equals(indexedFingerprint)) {
                scheduleRebuild(0);
            }
        } catch (IOException | RuntimeException error) {
            LOG.warn("Cannot check the Jentor course library fingerprint", error);
        }
    }

    private static String normalize(String path) {
        if (path == null || path.isBlank()) {
            return "";
        }
        try {
            return Path.of(path.strip()).toAbsolutePath().normalize().toString();
        } catch (InvalidPathException invalid) {
            return path.strip();
        }
    }

    @Override
    public void dispose() {
        refreshAlarm.cancelAllRequests();
        sweepAlarm.cancelAllRequests();
        index = null;
    }
}
