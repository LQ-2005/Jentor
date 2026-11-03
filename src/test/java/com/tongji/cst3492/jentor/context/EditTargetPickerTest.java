package com.tongji.cst3492.jentor.context;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class EditTargetPickerTest {
    @Test
    void anAttachedFileWins() {
        assertEquals("src/A.java",
                EditTargetPicker.pick(List.of("src/A.java", "src/B.java"), "src/Editor.java"));
    }

    @Test
    void fallsBackToTheEditorFile() {
        assertEquals("src/Editor.java", EditTargetPicker.pick(List.of(), "src/Editor.java"));
        assertEquals("src/Editor.java", EditTargetPicker.pick(null, "src/Editor.java"));
    }

    @Test
    void skipsBlankAttachedPaths() {
        assertEquals("src/Editor.java", EditTargetPicker.pick(List.of(" ", ""), "src/Editor.java"));
    }

    @Test
    void returnsNullWithoutAnyTarget() {
        assertNull(EditTargetPicker.pick(List.of(), null));
        assertNull(EditTargetPicker.pick(List.of("  "), ""));
        assertNull(EditTargetPicker.pick(null, null));
    }
}
