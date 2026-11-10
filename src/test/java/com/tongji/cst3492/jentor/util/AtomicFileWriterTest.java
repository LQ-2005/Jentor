package com.tongji.cst3492.jentor.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class AtomicFileWriterTest {
    @TempDir
    Path directory;

    @Test
    void createsParentDirectoriesAndWritesContent() throws Exception {
        Path target = directory.resolve("nested").resolve("data.json");
        byte[] content = "first".getBytes(StandardCharsets.UTF_8);

        AtomicFileWriter.write(target, content);

        assertArrayEquals(content, Files.readAllBytes(target));
    }

    @Test
    void replacesExistingContentWithoutLeavingTemporaryFiles() throws Exception {
        Path target = directory.resolve("data.json");
        Files.writeString(target, "old", StandardCharsets.UTF_8);
        byte[] content = "new".getBytes(StandardCharsets.UTF_8);

        AtomicFileWriter.write(target, content);

        assertArrayEquals(content, Files.readAllBytes(target));
        try (var files = Files.list(directory)) {
            List<String> names = files.map(path -> path.getFileName().toString()).toList();
            assertFalse(names.stream().anyMatch(name -> name.endsWith(".tmp")));
        }
    }
}
