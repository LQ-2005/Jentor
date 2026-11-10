package com.tongji.cst3492.jentor.context;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.tongji.cst3492.jentor.model.WorkspaceIndex;
import com.tongji.cst3492.jentor.util.AtomicFileWriter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Persists the workspace index under the IDE system directory, keyed by project
 * path, so opening a project does not re-parse it every time.
 */
public final class WorkspaceIndexStore {
    private static final Logger LOG = Logger.getInstance(WorkspaceIndexStore.class);
    private static final String DIRECTORY = "jentor-workspace";

    private WorkspaceIndexStore() {
    }

    public static Path pathFor(Project project) {
        String base = project.getBasePath();
        String key = Integer.toHexString((base == null ? project.getName() : base).hashCode());
        return PathManager.getSystemDir().resolve(DIRECTORY).resolve(key + ".json");
    }

    public static WorkspaceIndex load(Project project) {
        Path path = pathFor(project);
        if (!Files.isRegularFile(path)) {
            return null;
        }
        try {
            return WorkspaceIndexCodec.fromJson(Files.readString(path, StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException error) {
            LOG.warn("Cannot read the workspace index cache: " + path, error);
            return null;
        }
    }

    public static void save(Project project, WorkspaceIndex index) {
        Path path = pathFor(project);
        try {
            AtomicFileWriter.write(
                    path, WorkspaceIndexCodec.toJson(index).getBytes(StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException error) {
            LOG.warn("Cannot write the workspace index cache: " + path, error);
        }
    }
}
