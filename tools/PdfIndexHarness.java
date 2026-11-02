package com.tongji.cst3492.jentor.tools;

import com.tongji.cst3492.jentor.context.CourseIndexJsonCodec;
import com.tongji.cst3492.jentor.context.PdfCourseIndexBuilder;
import com.tongji.cst3492.jentor.model.CourseIndex;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/** CLI check for the in-plugin PDFBox indexer. */
public final class PdfIndexHarness {
    private PdfIndexHarness() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1 || args.length > 2) {
            throw new IllegalArgumentException(
                    "Usage: PdfIndexHarness <materials-dir> [output-json]");
        }
        Path materials = Path.of(args[0]).toAbsolutePath().normalize();
        CourseIndex index = PdfCourseIndexBuilder.build(materials);
        if (args.length == 2) {
            Path output = Path.of(args[1]).toAbsolutePath().normalize();
            Files.createDirectories(output.getParent());
            try (OutputStream stream = Files.newOutputStream(output)) {
                CourseIndexJsonCodec.write(index, stream);
            }
        }
        int pages = index.getDocuments().stream().mapToInt(document ->
                document.getPageCount()).sum();
        System.out.println("Documents: " + index.getDocuments().size());
        System.out.println("Pages: " + pages);
        System.out.println("Chunks: " + index.getChunks().size());
        System.out.println("Vocabulary: " + index.getIdf().size());
    }
}
