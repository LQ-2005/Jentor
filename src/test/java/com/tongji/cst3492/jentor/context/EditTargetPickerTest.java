package com.tongji.cst3492.jentor.context;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EditTargetPickerTest {
    @Test
    void anAttachedFileWinsWithoutAnUnrelatedSelection() {
        EditTargetPicker.Result result = EditTargetPicker.pick(
                List.of("src/A.java"), "src/Editor.java",
                "Editor.java", "Editor.java", 10, 20);

        assertTrue(result.hasTarget());
        assertEquals("src/A.java", result.target().path());
        assertEquals(0, result.target().startLine());
    }

    @Test
    void usesTheEditorSelectionWhenNoFileIsAttached() {
        EditTargetPicker.Result result = EditTargetPicker.pick(
                List.of(), "src/Editor.java", "Editor.java", "Editor.java", 10, 20);

        assertEquals("src/Editor.java", result.target().path());
        assertEquals(10, result.target().startLine());
        assertEquals(20, result.target().endLine());
    }

    @Test
    void usesARangeOnlyWhenItBelongsToTheTargetFile() {
        EditTargetPicker.Result unrelated = EditTargetPicker.pick(
                List.of("src/A.java"), "src/Editor.java",
                "Editor.java", "Other.java", 10, 20);
        assertEquals(0, unrelated.target().startLine());

        EditTargetPicker.Result attachedSelection = EditTargetPicker.pick(
                List.of("src/A.java"), "src/A.java",
                "A.java", "A.java", 10, 20);
        assertEquals(10, attachedSelection.target().startLine());
    }

    @Test
    void rejectsMultipleAttachedTargets() {
        EditTargetPicker.Result result = EditTargetPicker.pick(
                List.of("src/A.java", "src/B.java"), "src/Editor.java",
                "Editor.java", null, 0, 0);

        assertFalse(result.hasTarget());
        assertEquals("multiple attached target files", result.error());
    }

    @Test
    void ignoresBlankAndUnsafeAttachedPaths() {
        EditTargetPicker.Result result = EditTargetPicker.pick(
                List.of(" ", "../outside/A.java"), "src/Editor.java",
                "Editor.java", null, 0, 0);

        assertEquals("src/Editor.java", result.target().path());
    }

    @Test
    void returnsNoTargetWithoutAnEditorOrAttachment() {
        assertNull(EditTargetPicker.pick(List.of(), null, null, null, 0, 0).target());
        assertNull(EditTargetPicker.pick(List.of("  "), "", "", "", 0, 0).target());
        assertNull(EditTargetPicker.pick(null, null, null, null, 0, 0).target());
    }
}
