package com.tongji.cst3492.jentor.context;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.tongji.cst3492.jentor.model.CourseIndex;
import com.tongji.cst3492.jentor.model.CourseLibraryCatalog;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Persistent cache for automatically managed course PDF libraries. */
public final class CourseLibraryStore {
    private static final Logger LOG = Logger.getInstance(CourseLibraryStore.class);
    private static final String DIRECTORY = "jentor-course-library";

    private CourseLibraryStore() {
    }

    public static CourseLibraryCatalog loadCatalog() {
        try {
            Path path = directory().resolve("catalog.json");
            if (!Files.isRegularFile(path)) {
                return CourseLibraryCatalog.empty();
            }
            return CourseLibraryCatalogCodec.fromJson(Files.readAllBytes(path));
        } catch (IOException | RuntimeException error) {
            LOG.warn("Cannot load the Jentor course catalog", error);
            return CourseLibraryCatalog.empty();
        }
    }

    public static void saveCatalog(CourseLibraryCatalog catalog) throws IOException {
        Path directory = directory();
        Files.createDirectories(directory);
        writeAtomically(directory.resolve("catalog.json"),
                CourseLibraryCatalogCodec.toJsonBytes(catalog));
    }

    public static CourseIndex loadIndex() {
        Path path = directory().resolve("course-index.json");
        if (!Files.isRegularFile(path)) {
            return null;
        }
        try (InputStream input = Files.newInputStream(path)) {
            return CourseIndex.load(input);
        } catch (IOException | RuntimeException error) {
            LOG.warn("Cannot load the managed Jentor course index", error);
            return null;
        }
    }

    public static void saveIndex(CourseIndex index) throws IOException {
        Path directory = directory();
        Files.createDirectories(directory);
        writeAtomically(directory.resolve("course-index.json"),
                CourseIndexJsonCodec.toJsonBytes(index));
    }

    public static void clearIndex() throws IOException {
        Files.deleteIfExists(directory().resolve("course-index.json"));
    }

    private static Path directory() {
        return PathManager.getSystemDir().resolve(DIRECTORY);
    }

    private static void writeAtomically(Path target, byte[] content) throws IOException {
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
        Files.write(temporary, content);
        try {
            Files.move(temporary, target,
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
