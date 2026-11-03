package com.tongji.cst3492.jentor.context;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EditGuardTest {
    @Test
    void acceptsEditableTextFilesAndRejectsBinaries() {
        assertTrue(EditGuard.isWritablePath("src/main/java/demo/A.java"));
        assertTrue(EditGuard.isWritablePath("src/main/kotlin/demo/A.kt"));
        assertTrue(EditGuard.isWritablePath("build.gradle"));
        assertFalse(EditGuard.isWritablePath("src/main/resources/app.properties"));
        assertFalse(EditGuard.isWritablePath("out/production/demo/A.class"));
        assertFalse(EditGuard.isWritablePath("data/image.png"));
        assertFalse(EditGuard.isWritablePath("config/secrets.properties"));
        assertFalse(EditGuard.isWritablePath(""));
    }

    @Test
    void rejectsOversizedContent() {
        assertFalse(EditGuard.isTooLarge("x"));
        assertFalse(EditGuard.isTooLarge(null));
        assertTrue(EditGuard.isTooLarge("x".repeat(EditGuard.MAX_CHARS + 1)));
    }

    @Test
    void treatsLineEndingOnlyDifferencesAsNoOp() {
        assertTrue(EditGuard.isNoOp("a\r\nb", "a\nb"));
        assertTrue(EditGuard.isNoOp(null, ""));
        assertFalse(EditGuard.isNoOp("abc", "abd"));
    }

    @Test
    void conflictsOnlyWhenABaselineWasRecorded() {
        assertFalse(EditGuard.isConflict(0L, 5L));
        assertFalse(EditGuard.isConflict(5L, 5L));
        assertTrue(EditGuard.isConflict(5L, 6L));
    }

    @Test
    void rejectsAbsoluteAndParentTraversalPaths() {
        assertEquals("src/main/java/demo/A.java",
                EditGuard.normalizeRelativePath("src\\main\\java\\demo\\A.java"));
        assertEquals("", EditGuard.normalizeRelativePath("../outside/A.java"));
        assertEquals("", EditGuard.normalizeRelativePath("src/../../outside/A.java"));
        assertEquals("", EditGuard.normalizeRelativePath("C:/outside/A.java"));
        assertEquals("", EditGuard.normalizeRelativePath("/outside/A.java"));
        assertFalse(EditGuard.isWritablePath("../outside/A.java"));
    }

    @Test
    void preservesTheExistingLineSeparatorStyle() {
        assertEquals("one\r\ntwo\r\n", EditGuard.preserveLineSeparators(
                "old\r\ncontent\r\n", "one\ntwo\n"));
        assertEquals("one\ntwo\n", EditGuard.preserveLineSeparators(
                "old\ncontent\n", "one\ntwo\n"));
    }
}
