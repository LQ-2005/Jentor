package com.tongji.cst3492.ta.context;

import com.tongji.cst3492.ta.model.WorkspaceIndex;
import com.tongji.cst3492.ta.model.WorkspaceOutline;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class WorkspaceIndexCodecTest {
    @Test
    void roundTripsThroughJson() {
        WorkspaceIndex index = new WorkspaceIndex("/project", 1_700_000_000_000L, List.of(
                new WorkspaceOutline("src/A.java", "demo", "class A", 12,
                        List.of("void run()", "int value()")),
                new WorkspaceOutline("src/B.java", "demo", "interface B", 3, List.of())));

        String json = WorkspaceIndexCodec.toJson(index);
        WorkspaceIndex restored = WorkspaceIndexCodec.fromJson(json);

        assertNotNull(restored);
        assertEquals(2, restored.size());
        assertEquals("/project", restored.projectPath());
        assertEquals(1_700_000_000_000L, restored.builtAt());
        assertEquals(index.files(), restored.files());
    }

    @Test
    void rejectsMalformedPayloads() {
        assertNull(WorkspaceIndexCodec.fromJson(""));
        assertNull(WorkspaceIndexCodec.fromJson("{not json"));
    }
}
