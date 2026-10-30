package com.tongji.cst3492.ta.context;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkspaceChangeClassifierTest {
    @Test
    void countsOnlyOutlinableSourceFiles() {
        List<String> paths = List.of(
                "/project/src/main/java/demo/A.java",
                "/project/src/main/kotlin/demo/B.kt",
                "/project/src/main/groovy/demo/C.groovy",
                "/project/out/production/demo/A.class",
                "/project/README.md",
                "/project/src/main/resources/app.properties");

        assertEquals(3, WorkspaceChangeClassifier.countRelevant(paths));
    }

    @Test
    void compiledFilesAndBinariesAreNotRelevant() {
        assertFalse(WorkspaceChangeClassifier.isRelevant("/project/out/A.class"));
        assertFalse(WorkspaceChangeClassifier.isRelevant("/project/logo.png"));
        assertFalse(WorkspaceChangeClassifier.isRelevant("/project/lib/thing.jar"));
        assertFalse(WorkspaceChangeClassifier.isRelevant("pom.xml"));
    }

    @Test
    void ignoresMissingPaths() {
        assertFalse(WorkspaceChangeClassifier.isRelevant(null));
        assertFalse(WorkspaceChangeClassifier.isRelevant(""));
        assertFalse(WorkspaceChangeClassifier.isRelevant("   "));
        assertEquals(0, WorkspaceChangeClassifier.countRelevant(null));
        assertEquals(0, WorkspaceChangeClassifier.countRelevant(List.of()));
    }

    @Test
    void bulkStartsExactlyAtTheThreshold() {
        int threshold = WorkspaceChangeClassifier.BULK_CHANGE_THRESHOLD;

        assertFalse(WorkspaceChangeClassifier.isBulk(threshold - 1));
        assertTrue(WorkspaceChangeClassifier.isBulk(threshold));
        assertTrue(WorkspaceChangeClassifier.isBulk(threshold + 40));
    }

    @Test
    void aChangeBatchMixingCodeAndOutputIsClassifiedByCodeOnly() {
        List<String> gitCheckoutLikeBatch = List.of(
                "/p/src/A.java", "/p/out/A.class",
                "/p/src/B.java", "/p/out/B.class",
                "/p/build/report.html");

        assertEquals(2, WorkspaceChangeClassifier.countRelevant(gitCheckoutLikeBatch));
        assertFalse(WorkspaceChangeClassifier.isBulk(2));
    }
}
