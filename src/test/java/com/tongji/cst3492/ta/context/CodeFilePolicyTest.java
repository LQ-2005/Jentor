package com.tongji.cst3492.ta.context;

import com.tongji.cst3492.ta.model.CodeFileSnapshot;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CodeFilePolicyTest {
    @Test
    void acceptsSourceFilesAndRejectsCompiledOrBinaryFiles() {
        assertTrue(CodeFilePolicy.isSupported("src/main/java/demo/App.java"));
        assertTrue(CodeFilePolicy.isSupported("src/main/resources/application.properties"));
        assertTrue(CodeFilePolicy.isSupported("src/main/resources/service.xml"));
        assertFalse(CodeFilePolicy.isSupported("out/production/demo/App.class"));
        assertFalse(CodeFilePolicy.isSupported("docs/slides.pdf"));
        assertFalse(CodeFilePolicy.isSupported("image.png"));
        assertFalse(CodeFilePolicy.isSupported("Makefile"));
        assertFalse(CodeFilePolicy.isSupported(""));
    }

    @Test
    void rejectsNamesThatUsuallyHoldCredentials() {
        assertFalse(CodeFilePolicy.isSupported("config/secrets.properties"));
        assertFalse(CodeFilePolicy.isSupported("src/main/resources/credentials.json"));
        assertFalse(CodeFilePolicy.isSupported("keys/id_rsa.java"));
        assertTrue(CodeFilePolicy.isSupported("src/main/resources/app.properties"));
    }

    @Test
    void keepsAShortFileWhole() {
        CodeFileSnapshot snapshot = CodeFilePolicy.snapshot(
                "src/demo/App.java", "JAVA", "class App {\n    int value = 1;\n}\n");

        assertEquals(3, snapshot.totalLines());
        assertEquals(1, snapshot.startLine());
        assertEquals(3, snapshot.endLine());
        assertFalse(snapshot.truncated());
        assertEquals("L1-L3", snapshot.locator());
        assertEquals("src/demo/App.java (L1-L3)", snapshot.describe());
    }

    @Test
    void truncatesAtTheLineLimit() {
        CodeFileSnapshot snapshot = CodeFilePolicy.snapshot(
                "src/demo/Big.java", "JAVA", numberedLines(500));

        assertTrue(snapshot.truncated());
        assertEquals(500, snapshot.totalLines());
        assertEquals(1, snapshot.startLine());
        assertEquals(CodeFilePolicy.MAX_LINES_PER_FILE, snapshot.endLine());
        assertEquals(400, snapshot.content().lines().count());
        assertTrue(snapshot.describe().contains("of 500"));
    }

    @Test
    void truncatesASingleLongLineAtTheCharacterLimit() {
        CodeFileSnapshot snapshot = CodeFilePolicy.snapshot(
                "src/demo/Wide.java", "JAVA", "x".repeat(5_000));

        assertTrue(snapshot.truncated());
        assertEquals(1, snapshot.endLine());
        assertEquals(CodeFilePolicy.MAX_CHARS_PER_FILE, snapshot.content().length());
    }

    @Test
    void normalizesWindowsLineEndings() {
        CodeFileSnapshot snapshot = CodeFilePolicy.snapshot(
                "src/demo/App.java", "JAVA", "one\r\ntwo\r\nthree");

        assertEquals(3, snapshot.totalLines());
        assertEquals("one\ntwo\nthree", snapshot.content());
    }

    private static String numberedLines(int count) {
        StringBuilder builder = new StringBuilder();
        for (int line = 1; line <= count; line++) {
            builder.append("line ").append(line).append('\n');
        }
        return builder.toString();
    }
}
