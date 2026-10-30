package com.tongji.cst3492.ta.context;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkspaceExcerptTest {
    @Test
    void rendersTheHeaderAndKeepsShortFilesWhole() {
        String text = WorkspaceExcerpt.render("src/A.java", "class A {\n}\n", 400);

        assertTrue(text.startsWith("--- FILE: src/A.java ---"), text);
        assertTrue(text.contains("class A {"));
        assertFalse(text.contains("truncated"));
    }

    @Test
    void truncatesLongFilesByCharacterBudget() {
        StringBuilder body = new StringBuilder();
        for (int line = 0; line < 500; line++) {
            body.append("line ").append(line).append('\n');
        }

        String text = WorkspaceExcerpt.render("src/Big.java", body.toString(), 500);

        assertTrue(text.contains("truncated"), text);
        assertTrue(text.length() <= 700, () -> "rendered " + text.length() + " chars");
    }

    @Test
    void capsTheNumberOfLines() {
        StringBuilder body = new StringBuilder();
        for (int line = 0; line < 300; line++) {
            body.append("x\n");
        }

        String text = WorkspaceExcerpt.render("src/Many.java", body.toString(), 100_000);

        assertTrue(text.contains("truncated"));
        long renderedLines = text.lines().count();
        assertTrue(renderedLines <= WorkspaceExcerpt.MAX_LINES + 2,
                () -> "rendered " + renderedLines + " lines");
    }

    @Test
    void emptyInputRendersNothing() {
        assertEquals("", WorkspaceExcerpt.render(null, "class A {}", 100));
        assertEquals("", WorkspaceExcerpt.render("", "class A {}", 100));
        assertEquals("", WorkspaceExcerpt.render("src/A.java", "   ", 100));
        assertEquals("", WorkspaceExcerpt.render("src/A.java", "class A {}", 0));
    }
}
