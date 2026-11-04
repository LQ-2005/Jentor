package com.tongji.cst3492.jentor.context;

import com.tongji.cst3492.jentor.model.WorkspaceIndex;
import com.tongji.cst3492.jentor.model.WorkspaceOutline;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class EditRefusalTargetTest {
    @Test
    void relocatesToTheOnlyFileNamedByTheRefusal() {
        WorkspaceIndex index = index(
                "src/main/java/com/example/OrderDemo.java",
                "src/main/java/com/example/OrderService.java");

        assertEquals("src/main/java/com/example/OrderService.java",
                EditRefusalTarget.resolve(
                        "The defects live in OrderService.java, not this file.",
                        "src/main/java/com/example/OrderDemo.java",
                        index));
    }

    @Test
    void refusesAmbiguousOrUnchangedTargets() {
        WorkspaceIndex ambiguous = index(
                "src/a/Demo.java",
                "src/b/Demo.java");
        assertNull(EditRefusalTarget.resolve(
                "The issue is in Demo.java", "src/Current.java", ambiguous));
        assertNull(EditRefusalTarget.resolve(
                "The issue is in Current.java", "src/Current.java", ambiguous));
    }

    private static WorkspaceIndex index(String... paths) {
        return new WorkspaceIndex(
                "D:/demo",
                1L,
                java.util.Arrays.stream(paths)
                        .map(path -> new WorkspaceOutline(path, "", "", 1, List.of()))
                        .toList());
    }
}
