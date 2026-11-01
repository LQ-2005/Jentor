package com.tongji.cst3492.jentor.ui;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarkdownContentTest {
    @Test
    void rendersHeadingsAndParagraphs() {
        String html = html("## Course basis\n\nA paragraph\ncontinued here.\n\n###要点提示");
        assertTrue(html.contains("<h2>Course basis</h2>"));
        assertTrue(html.contains("<h3>要点提示</h3>"));
        assertTrue(html.contains("<p>A paragraph\ncontinued here.</p>"));
    }

    @Test
    void rendersOrderedAndNestedListsAndInlineFormatting() {
        String html = html("1. Parent\n   - **bold**\n   - *italic* and `List<String>`\n2. Next");
        assertTrue(html.contains("<ol>"));
        assertTrue(html.contains("<ul>"));
        assertTrue(html.contains("<strong>bold</strong>"));
        assertTrue(html.contains("<em>italic</em>"));
        assertTrue(html.contains("<code>List&lt;String&gt;</code>"));
    }

    @Test
    void rendersTablesAndQuotes() {
        String html = html("| Feature | Value |\n| --- | --- |\n| Java | 21 |\n\n> A quote");
        assertTrue(html.contains("<table>"));
        assertTrue(html.contains("<th>Feature</th>"));
        assertTrue(html.contains("<td>21</td>"));
        assertTrue(html.contains("<blockquote>"));
    }

    @Test
    void separatesCodeWithoutChangingItsContent() {
        List<MarkdownContent.Part> parts = MarkdownContent.parse(
                "Before\n\n```java\nList<String> values = List.of();\n##LeaveThisAlone\n```\n\nAfter");
        assertEquals(3, parts.size());
        assertEquals("java", parts.get(1).language());
        assertEquals("List<String> values = List.of();\n##LeaveThisAlone\n", parts.get(1).code());
        assertTrue(parts.getFirst().html().contains("<p>Before</p>"));
        assertTrue(parts.getLast().html().contains("<p>After</p>"));
    }

    @Test
    void acceptsUnclosedFencesDuringStreamingAndIndentedCode() {
        List<MarkdownContent.Part> streaming = MarkdownContent.parse("```java\nint value = 1;");
        assertEquals(1, streaming.size());
        assertEquals("int value = 1;\n", streaming.getFirst().code());

        List<MarkdownContent.Part> indented = MarkdownContent.parse("    ##Code\n    int value = 1;\n");
        assertEquals("##Code\nint value = 1;\n", indented.getFirst().code());
    }

    @Test
    void leavesNestedCodeUntouchedWhenNormalizingHeadings() {
        String html = html("##Course basis\n\n- Example:\n\n  ```text\n  ##KeepExact\n  ```");
        assertTrue(html.contains("<h2>Course basis</h2>"));
        assertTrue(html.contains("##KeepExact\n"));
        assertFalse(html.contains("## KeepExact"));
    }

    @Test
    void escapesRawHtmlAndReplacesImagesWithAltText() {
        String html = html("<script>alert('x')</script>\n\n![**Diagram** of `List<T>`](https://example.com/private.png)");
        assertTrue(html.contains("&lt;script&gt;"));
        assertFalse(html.contains("<script>"));
        assertTrue(html.contains("Diagram of List&lt;T&gt;"));
        assertFalse(html.contains("<img"));
        assertFalse(html.contains("private.png"));
    }

    @Test
    void keepsSafeLinksAndRemovesUnsafeSchemes() {
        String html = html("[Docs](https://example.com/docs) and [Bad](javascript:alert%281%29)");
        assertTrue(html.contains("href=\"https://example.com/docs\""));
        assertFalse(html.contains("href=\"javascript:"));
    }

    private String html(String markdown) {
        StringBuilder html = new StringBuilder();
        for (MarkdownContent.Part part : MarkdownContent.parse(markdown)) {
            if (part.code() == null) {
                html.append(part.html());
            }
        }
        return html.toString();
    }
}
