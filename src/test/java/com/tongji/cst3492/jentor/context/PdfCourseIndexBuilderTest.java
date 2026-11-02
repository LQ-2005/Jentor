package com.tongji.cst3492.jentor.context;

import com.tongji.cst3492.jentor.model.Chunk;
import com.tongji.cst3492.jentor.model.CourseIndex;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PdfCourseIndexBuilderTest {
    @TempDir
    Path temporary;

    @Test
    void buildsAndRoundTripsARealPdfIndex() throws Exception {
        Path pdf = temporary.resolve("Lec-Test-PDFBox.pdf");
        createPdf(pdf, List.of(
                "Arrays\nAn array is a container object.",
                "Interfaces\nAn interface defines a contract."));

        CourseIndex index = PdfCourseIndexBuilder.build(temporary);

        assertEquals(1, index.getDocuments().size());
        assertEquals(2, index.getDocuments().getFirst().getPageCount());
        assertFalse(index.getChunks().isEmpty());
        assertEquals("local-sha256-tfidf", index.getEmbeddingModel());
        assertEquals(512, index.getDimensions());
        for (Chunk chunk : index.getChunks()) {
            assertEquals(512, chunk.getVector().length);
            assertEquals("PAGE", chunk.getLocatorType());
            assertTrue(chunk.getPageStart() >= 1);
            assertEquals(chunk.getPageStart(), chunk.getPageEnd());
            assertTrue(chunk.getId().startsWith("lec-test-pdfbox-p"));
            assertFalse(chunk.getText().isBlank());
        }

        byte[] json = CourseIndexJsonCodec.toJsonBytes(index);
        CourseIndex loaded = CourseIndex.load(new ByteArrayInputStream(json));
        assertEquals(index.getDocuments().size(), loaded.getDocuments().size());
        assertEquals(index.getChunks().size(), loaded.getChunks().size());
        assertEquals(index.getDefaultIdf(), loaded.getDefaultIdf(), 0.000001f);
        assertEquals(index.getChunks().getFirst().getId(),
                loaded.getChunks().getFirst().getId());
        assertEquals(1, PdfCourseIndexBuilder.fromIndex(loaded).size());
    }

    @Test
    void pageChunksNeverCrossThePdfPageBoundary() {
        String page = ("line of Java material\n").repeat(120);

        List<String> chunks = PdfCourseIndexBuilder.splitPage(page);

        assertTrue(chunks.size() > 1);
        assertTrue(chunks.stream().allMatch(chunk -> chunk.length() <= 900));
    }

    @Test
    void libraryFingerprintChangesWhenAPdfChanges() throws Exception {
        Path pdf = temporary.resolve("Lecture.pdf");
        createPdf(pdf, List.of("First version"));
        String before = CourseLibraryFingerprint.of(temporary);

        createPdf(pdf, List.of("Second version", "New page"));
        String after = CourseLibraryFingerprint.of(temporary);

        assertNotEquals(before, after);
    }

    @Test
    void nonLatinFileNamesStillProduceStableDocumentIds() {
        String first = PdfCourseIndexBuilder.slugify("第一课 Java 基础");
        String second = PdfCourseIndexBuilder.slugify("第二课 Java 基础");

        assertTrue(first.startsWith("java-"));
        assertTrue(second.startsWith("java-"));
        assertNotEquals(first, second);
    }

    @Test
    void aggregatesDocumentsFromIndependentFolders() throws Exception {
        Path firstFolder = temporary.resolve("week-1");
        Path secondFolder = temporary.resolve("external-module");
        java.nio.file.Files.createDirectories(firstFolder);
        java.nio.file.Files.createDirectories(secondFolder);
        Path firstPdf = firstFolder.resolve("Java-Basics.pdf");
        Path secondPdf = secondFolder.resolve("Spring-Security.pdf");
        createPdf(firstPdf, List.of("Arrays\nArrays store values."));
        createPdf(secondPdf, List.of("Security\nFilters protect requests."));

        var first = PdfCourseIndexBuilder.parse(firstPdf);
        var second = PdfCourseIndexBuilder.parse(secondPdf);
        CourseIndex index = PdfCourseIndexBuilder.aggregate(List.of(first, second));

        assertEquals(2, index.getDocuments().size());
        assertEquals(2, PdfCourseIndexBuilder.fromIndex(index).size());
        assertTrue(index.getDocuments().stream()
                .anyMatch(document -> document.getFileName().equals("Java-Basics.pdf")));
        assertTrue(index.getDocuments().stream()
                .anyMatch(document -> document.getFileName().equals("Spring-Security.pdf")));
    }

    private static void createPdf(Path path, List<String> pages) throws Exception {
        try (PDDocument document = new PDDocument()) {
            for (String text : pages) {
                PDPage page = new PDPage();
                document.addPage(page);
                try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                    content.beginText();
                    content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    content.newLineAtOffset(72, 750);
                    for (String line : text.split("\\R")) {
                        content.showText(line);
                        content.newLineAtOffset(0, -18);
                    }
                    content.endText();
                }
            }
            document.save(path.toFile());
        }
    }
}
