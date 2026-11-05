package com.tongji.cst3492.jentor.context;

import com.tongji.cst3492.jentor.model.CodeContext;
import com.tongji.cst3492.jentor.model.CodeFileSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkspaceContextBuilderTest {
    @Test
    void detectsACompleteSourceChangeBetweenAnalysisAndEdit() {
        CodeFileSnapshot before = snapshot("src/A.java", "class A { int value = 1; }", false);
        CodeFileSnapshot after = snapshot("src/A.java", "class A { int value = 2; }", false);

        assertEquals(List.of("src/A.java"), WorkspaceContextBuilder.changedPaths(
                CodeContext.ofFiles(List.of(before)),
                CodeContext.ofFiles(List.of(after))));
    }

    @Test
    void ignoresContentDifferencesInMethodAwareExcerpts() {
        CodeFileSnapshot before = snapshot("src/A.java", "[lines 1-2]\nclass A {}", true);
        CodeFileSnapshot after = snapshot("src/A.java", "[lines 2-3]\nclass A {}", true);

        assertTrue(WorkspaceContextBuilder.changedPaths(
                CodeContext.ofFiles(List.of(before)),
                CodeContext.ofFiles(List.of(after))).isEmpty());
    }

    @Test
    void summaryDistinguishesCompleteFilesAndExcerpts() {
        CodeContext context = CodeContext.ofFiles(List.of(
                snapshot("src/A.java", "class A {}", false),
                snapshot("src/B.java", "[lines 1-2]\nclass B {}", true)));

        String summary = WorkspaceContextBuilder.summary(context, false);
        assertTrue(summary.contains("2 files"));
        assertTrue(summary.contains("1 full"));
        assertTrue(summary.contains("1 method-aware"));
    }

    private static CodeFileSnapshot snapshot(String path, String content, boolean truncated) {
        String name = path.substring(path.lastIndexOf('/') + 1);
        return new CodeFileSnapshot(
                path, name, "JAVA", 1, 3, 3, content, truncated);
    }
}
