package com.tongji.cst3492.jentor.util;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

/** Writes a file through a unique sibling temporary file and an atomic replacement. */
public final class AtomicFileWriter {
    private AtomicFileWriter() {
    }

    public static void write(Path target, byte[] content) throws IOException {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(content, "content");

        Path absoluteTarget = target.toAbsolutePath().normalize();
        Path parent = absoluteTarget.getParent();
        if (parent == null) {
            throw new IOException("Target file has no parent directory: " + target);
        }
        Files.createDirectories(parent);

        Path temporary = Files.createTempFile(
                parent, absoluteTarget.getFileName().toString() + ".", ".tmp");
        try {
            Files.write(temporary, content);
            try {
                Files.move(temporary, absoluteTarget,
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, absoluteTarget, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
