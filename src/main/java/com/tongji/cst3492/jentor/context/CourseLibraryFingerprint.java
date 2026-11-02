package com.tongji.cst3492.jentor.context;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Cheap source fingerprint based on PDF names, sizes and modification times. */
public final class CourseLibraryFingerprint {
    private CourseLibraryFingerprint() {
    }

    public static String of(Path materialsDirectory) throws IOException {
        MessageDigest digest = sha256();
        for (Path pdf : PdfCourseIndexBuilder.listPdfFiles(materialsDirectory)) {
            digest.update(pdf.getFileName().toString().getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(Long.toString(Files.size(pdf)).getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(Long.toString(Files.getLastModifiedTime(pdf).toMillis())
                    .getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is required", error);
        }
    }
}
