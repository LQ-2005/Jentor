package com.tongji.cst3492.jentor.service;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.util.Alarm;
import com.tongji.cst3492.jentor.context.WorkspaceChangeClassifier;
import com.tongji.cst3492.jentor.context.WorkspaceExcerpt;
import com.tongji.cst3492.jentor.context.WorkspaceFileReader;
import com.tongji.cst3492.jentor.context.WorkspaceIndexBuilder;
import com.tongji.cst3492.jentor.context.WorkspaceIndexStore;
import com.tongji.cst3492.jentor.context.WorkspaceOutlineSelector;
import com.tongji.cst3492.jentor.model.WorkspaceContext;
import com.tongji.cst3492.jentor.model.WorkspaceIndex;
import com.tongji.cst3492.jentor.model.WorkspaceOutline;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Owns the workspace index and keeps it current.
 *
 * <p>A background monitor reacts to VFS changes (debounced) and re-parses only
 * the files that changed, so the index stays fresh without asking the user to
 * reload. A slow periodic sweep catches changes the event listener missed.</p>
 */
@Service(Service.Level.PROJECT)
public final class WorkspaceIndexService implements Disposable {
    private static final Logger LOG = Logger.getInstance(WorkspaceIndexService.class);
    /** More changed code files than this in one batch looks like a VCS operation. */
    public static final int BULK_CHANGE_THRESHOLD = WorkspaceChangeClassifier.BULK_CHANGE_THRESHOLD;
    /** Files whose full text is injected alongside the outline. */
    public static final int EXCERPT_FILES = 2;
    public static final int EXCERPT_CHARS = 1_800;

    private static final int REFRESH_DELAY_MS = 800;
    private static final int SWEEP_INTERVAL_MS = 120_000;

    private final Project project;
    private volatile WorkspaceIndex index;
    private volatile boolean stale;
    private final AtomicBoolean loading = new AtomicBoolean();
    private final Alarm refreshAlarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, this);
    private final Alarm sweepAlarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, this);
    private final List<String> pendingChanges = new CopyOnWriteArrayList<>();
    private final AtomicBoolean refreshScheduled = new AtomicBoolean();

    public WorkspaceIndexService(Project project) {
        this.project = project;
        project.getMessageBus().connect(this).subscribe(VirtualFileManager.VFS_CHANGES,
                new BulkFileListener() {
                    @Override
                    public void after(@NotNull List<? extends VFileEvent> events) {
                        onChanges(events);
                    }
                });
        scheduleSweep(SWEEP_INTERVAL_MS);
    }

    /** Builds the index in the background if it is not available yet. */
    public void ensureBuiltAsync() {
        if (index != null || !loading.compareAndSet(false, true)) {
            return;
        }
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                WorkspaceIndex loaded = WorkspaceIndexStore.load(project);
                WorkspaceIndex cached = loaded;
                boolean useCached = cached != null && cached.size() > 0
                        && ReadAction.computeBlocking(
                                () -> WorkspaceIndexBuilder.isFresh(project, cached));
                if (!useCached) {
                    loaded = ReadAction.computeBlocking(
                            () -> WorkspaceIndexBuilder.build(project));
                    WorkspaceIndexStore.save(project, loaded);
                }
                WorkspaceIndex result = loaded;
                ApplicationManager.getApplication().invokeLater(() -> {
                    index = result;
                    stale = false;
                    loading.set(false);
                });
            } catch (Throwable error) {
                LOG.warn("Cannot build the Jentor workspace index", error);
                ApplicationManager.getApplication().invokeLater(() -> loading.set(false));
            }
        });
    }

    /** Drops the index and rebuilds it; kept for an explicit refresh. */
    public void reload() {
        index = null;
        stale = false;
        ensureBuiltAsync();
    }

    /**
     * Renders the outline and a bounded excerpt of the most relevant files. Builds
     * the index synchronously when it is missing, so ticking the option always
     * takes effect even on the first question. Must be called off the EDT.
     */
    public WorkspaceContext contextForBlocking(String question) {
        WorkspaceIndex local = index;
        if (local == null) {
            synchronized (this) {
                local = index;
                if (local == null) {
                    local = ReadAction.computeBlocking(() -> WorkspaceIndexBuilder.build(project));
                    WorkspaceIndexStore.save(project, local);
                    index = local;
                    stale = false;
                }
            }
        }
        WorkspaceIndex resolved = local;
        StringBuilder text = new StringBuilder(WorkspaceOutlineSelector.select(resolved, question));
        String excerpts = ReadAction.computeBlocking(() -> excerpts(resolved, question));
        if (!excerpts.isEmpty()) {
            text.append('\n').append(excerpts);
        }
        return new WorkspaceContext(text.toString(), stale, local.size());
    }

    /** Non-blocking variant: empty until the index is ready. */
    public WorkspaceContext contextFor(String question) {
        WorkspaceIndex local = index;
        if (local == null) {
            ensureBuiltAsync();
            return WorkspaceContext.empty();
        }
        return new WorkspaceContext(WorkspaceOutlineSelector.select(local, question), stale,
                local.size());
    }

    public boolean isStale() {
        return stale;
    }

    public WorkspaceIndex getIndex() {
        return index;
    }

    private String excerpts(WorkspaceIndex local, String question) {
        WorkspaceOutlineSelector.Ranking ranking =
                WorkspaceOutlineSelector.rank(local, question, EXCERPT_FILES);
        StringBuilder result = new StringBuilder();
        for (WorkspaceOutline file : ranking.files()) {
            VirtualFile virtualFile = resolve(file.path());
            if (virtualFile == null) {
                continue;
            }
            String excerpt = WorkspaceExcerpt.render(file.path(),
                    WorkspaceFileReader.textOf(virtualFile), EXCERPT_CHARS);
            if (!excerpt.isEmpty()) {
                result.append(excerpt);
            }
        }
        if (result.length() == 0) {
            return "";
        }
        return "RELATED FILE CONTENT (reference only, lowest priority):\n" + result;
    }

    private VirtualFile resolve(String path) {
        String base = project.getBasePath();
        if (base == null || path == null || path.isBlank()) {
            return null;
        }
        return LocalFileSystem.getInstance().findFileByPath(base + "/" + path);
    }

    private void onChanges(List<? extends VFileEvent> events) {
        if (index == null) {
            return;
        }
        boolean relevant = false;
        for (VFileEvent event : events) {
            String path = event.getPath();
            if (WorkspaceChangeClassifier.isRelevant(path)) {
                pendingChanges.add(path);
                relevant = true;
            }
        }
        if (!relevant) {
            return;
        }
        stale = true;
        scheduleRefresh();
    }

    private void scheduleRefresh() {
        if (!refreshScheduled.compareAndSet(false, true)) {
            return;
        }
        refreshAlarm.cancelAllRequests();
        refreshAlarm.addRequest(() -> {
            refreshScheduled.set(false);
            refreshNow();
        }, REFRESH_DELAY_MS);
    }

    private void refreshNow() {
        WorkspaceIndex local = index;
        if (local == null) {
            ensureBuiltAsync();
            return;
        }
        List<String> changed = new ArrayList<>(pendingChanges);
        pendingChanges.clear();
        if (changed.isEmpty()) {
            stale = false;
            return;
        }
        WorkspaceIndex updated = ReadAction.computeBlocking(
                () -> WorkspaceIndexBuilder.refresh(project, local, changed));
        index = updated;
        stale = false;
        WorkspaceIndexStore.save(project, updated);
    }

    private void scheduleSweep(int delayMs) {
        sweepAlarm.addRequest(() -> {
            try {
                sweep();
            } finally {
                scheduleSweep(SWEEP_INTERVAL_MS);
            }
        }, delayMs);
    }

    /** Catches file changes the VFS listener missed (external tools, VCS). */
    private void sweep() {
        WorkspaceIndex local = index;
        if (local == null) {
            ensureBuiltAsync();
            return;
        }
        boolean fresh = ReadAction.computeBlocking(
                () -> WorkspaceIndexBuilder.isFresh(project, local));
        if (!fresh) {
            WorkspaceIndex rebuilt = ReadAction.computeBlocking(
                    () -> WorkspaceIndexBuilder.build(project));
            index = rebuilt;
            stale = false;
            WorkspaceIndexStore.save(project, rebuilt);
        }
    }

    @Override
    public void dispose() {
        refreshAlarm.cancelAllRequests();
        sweepAlarm.cancelAllRequests();
        index = null;
    }
}
