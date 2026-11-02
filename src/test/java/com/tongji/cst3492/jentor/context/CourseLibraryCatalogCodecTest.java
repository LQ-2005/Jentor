package com.tongji.cst3492.jentor.context;

import com.tongji.cst3492.jentor.model.CourseDocumentSource;
import com.tongji.cst3492.jentor.model.CourseLibraryCatalog;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CourseLibraryCatalogCodecTest {
    @Test
    void roundTripsIndependentSourcePathsAndStatus() throws Exception {
        CourseLibraryCatalog catalog = new CourseLibraryCatalog(1, List.of(
                new CourseDocumentSource("doc-1", "D:/Course/A.pdf", "A.pdf",
                        "hash-a", 100, 10, 4, 5,
                        CourseDocumentSource.READY, "", 123),
                new CourseDocumentSource("doc-2", "E:/Teaching/B.pdf", "B.pdf",
                        "hash-b", 200, 20, 0, 0,
                        CourseDocumentSource.MISSING, "not found", 0)));

        CourseLibraryCatalog restored = CourseLibraryCatalogCodec.fromJson(
                CourseLibraryCatalogCodec.toJsonBytes(catalog));

        assertEquals(2, restored.documents().size());
        assertEquals("E:/Teaching/B.pdf", restored.documents().get(1).path());
        assertEquals(CourseDocumentSource.MISSING,
                restored.documents().get(1).status());
    }

    @Test
    void malformedCatalogBecomesEmptyInsteadOfBreakingStartup() {
        CourseLibraryCatalog restored = CourseLibraryCatalogCodec.fromJson(
                "{not-json".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        assertTrue(restored.documents().isEmpty());
    }
}
