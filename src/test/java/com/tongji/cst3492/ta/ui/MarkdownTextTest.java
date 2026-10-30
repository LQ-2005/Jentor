package com.tongji.cst3492.ta.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarkdownTextTest {
    @Test
    void stripsHeadingsEmphasisAndInlineCode() {
        String text = MarkdownText.toPlainText(
                "## Course basis\n\nAn **interface** is a *contract* with `List<String>`.");

        assertTrue(text.contains("Course basis"), text);
        assertFalse(text.contains("##"));
        assertFalse(text.contains("**"));
        assertFalse(text.contains("`"));
        assertTrue(text.contains("List<String>"));
    }

    @Test
    void keepsCodeFenceContentWithoutTheFence() {
        String text = MarkdownText.toPlainText(
                "Intro\n\n```java\nint value = 1;\n```\n");

        assertTrue(text.contains("int value = 1;"), text);
        assertFalse(text.contains("```"));
    }

    @Test
    void convertsLinksAndQuotes() {
        String text = MarkdownText.toPlainText("> See [the docs](https://example.com)");

        assertTrue(text.contains("See the docs (https://example.com)"), text);
        assertFalse(text.stripLeading().startsWith(">"));
    }

    @Test
    void collapsesBlankLinesAndHandlesEmptyInput() {
        assertEquals("", MarkdownText.toPlainText(null));
        assertEquals("", MarkdownText.toPlainText("   "));
        assertEquals("a\n\nb", MarkdownText.toPlainText("a\n\n\n\nb"));
    }
}
