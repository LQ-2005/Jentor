package com.tongji.cst3492.jentor.context;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.tongji.cst3492.jentor.model.CourseIndex;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HexFormat;

/** Persistent cache for automatically managed course PDF libraries. */
public final class CourseLibraryStore {
    private static final Logger LOG = Logger.getInstance(CourseLibraryStore.class);
    private static final String DIRECTORY = "jentor-course-library";

    private CourseLibraryStore() {
    }

    public static CourseIndex load(Path materialsDirectory) {
        try {
            String fingerprint = CourseLibraryFingerprint.of(materialsDirectory);
            return load(materialsDirectory, fingerprint);
        } catch (IOException | RuntimeException error) {
            LOG.warn("Cannot load cached Jentor course library", error);
            return null;
        }
    }

    public static CourseIndex load(Path materialsDirectory, String fingerprint) {
        try {
            Path directory = directoryFor(materialsDirectory);
            Path fingerprintFile = directory.resolve("fingerprint.txt");
            Path indexFile = directory.resolve("course-index.json");
            if (!Files.isRegularFile(fingerprintFile) || !Files.isRegularFile(indexFile)) {
                return null;
            }
            String cached = Files.readString(fingerprintFile, StandardCharsets.UTF_8).strip();
            if (!fingerprint.equals(cached)) {
                return null;
            }
            try (InputStream input = Files.newInputStream(indexFile)) {
                return CourseIndex.load(input);
            }
        } catch (IOException | RuntimeException error) {
            LOG.warn("Cannot load cached Jentor course library", error);
            return null;
        }
    }

    public static void save(Path materialsDirectory, CourseIndex index) throws IOException {
        save(materialsDirectory, index, CourseLibraryFingerprint.of(materialsDirectory));
    }

    public static void save(
            Path materialsDirectory,
            CourseIndex index,
            String fingerprint) throws IOException {
        Path directory = directoryFor(materialsDirectory);
        Files.createDirectories(directory);
        writeAtomically(directory.resolve("course-index.json"),
                CourseIndexJsonCodec.toJsonBytes(index));
        writeAtomically(directory.resolve("fingerprint.txt"),
                fingerprint.getBytes(StandardCharsets.UTF_8));
    }

    private static Path directoryFor(Path materialsDirectory) {
        String normalized = materialsDirectory.toAbsolutePath().normalize().toString();
        String key = HexFormat.of().formatHex(
                sha256(normalized.getBytes(StandardCharsets.UTF_8)));
        return PathManager.getSystemDir().resolve(DIRECTORY).resolve(key);
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

    private static byte[] sha256(byte[] value) {
        try {
            return java.security.MessageDigest.getInstance("SHA-256").digest(value);
        } catch (java.security.NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is required", error);
        }
    }
}
