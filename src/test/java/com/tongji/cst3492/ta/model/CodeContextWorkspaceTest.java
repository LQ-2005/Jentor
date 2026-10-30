package com.tongji.cst3492.ta.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CodeContextWorkspaceTest {
    private static final WorkspaceContext OUTLINE =
            new WorkspaceContext("PROJECT OUTLINE (2 source files):\n--- A.java ---\n", false, 2);

    @Test
    void theLegacyConstructorHasNoFilesAndNoWorkspace() {
        CodeContext context = new CodeContext(
                "Demo.java", "JAVA", 10, 12, "void run()", "class Demo", "", "run();", "");

        assertTrue(context.hasSelection());
        assertFalse(context.hasFiles());
        assertFalse(context.hasWorkspace());
        assertFalse(context.isEmpty());
        assertTrue(context.workspace().isEmpty());
    }

    @Test
    void withWorkspaceKeepsTheSelectionAndTheFiles() {
        CodeContext base = new CodeContext(
                "Demo.java", "JAVA", 10, 12, "void run()", "class Demo", "", "run();", "")
                .withFiles(List.of(file()));

        CodeContext withOutline = base.withWorkspace(OUTLINE);

        assertTrue(withOutline.hasSelection());
        assertTrue(withOutline.hasFiles());
        assertTrue(withOutline.hasWorkspace());
        assertSame(OUTLINE, withOutline.workspace());
        assertEquals(base.files(), withOutline.files());
    }

    @Test
    void withFilesKeepsTheWorkspace() {
        CodeContext base = CodeContext.empty().withWorkspace(OUTLINE);

        CodeContext withFile = base.withFiles(List.of(file()));

        assertTrue(withFile.hasWorkspace());
        assertTrue(withFile.hasFiles());
    }

    @Test
    void aWorkspaceOnlyContextIsNotEmpty() {
        CodeContext context = CodeContext.empty().withWorkspace(OUTLINE);

        assertTrue(context.hasWorkspace());
        assertFalse(context.isEmpty());
        assertFalse(context.hasSelection());
        assertFalse(context.hasFiles());
    }

    @Test
    void anEmptyWorkspaceDoesNotMakeAContextNonEmpty() {
        CodeContext context = CodeContext.empty().withWorkspace(WorkspaceContext.empty());

        assertFalse(context.hasWorkspace());
        assertTrue(context.isEmpty());
    }

    private static CodeFileSnapshot file() {
        return new CodeFileSnapshot("src/A.java", "A.java", "JAVA", 1, 3, 3, "class A {}\n", false);
    }
}
