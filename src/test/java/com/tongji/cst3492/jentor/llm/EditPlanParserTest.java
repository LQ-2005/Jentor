package com.tongji.cst3492.jentor.llm;

import com.tongji.cst3492.jentor.model.EditPlan;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EditPlanParserTest {
    @Test
    void parsesMultipleFilesAndSkippedEntries() {
        EditPlan plan = EditPlanParser.parse("""
                ```json
                {
                  "summary": "修复订单模块",
                  "files": [
                    {"path": "src/OrderService.java", "instruction": "修复空指针"},
                    {"path": "src/OrderDemo.java", "instruction": "同步调用方式"}
                  ],
                  "skipped": [
                    {"path": "src/ReadOnlyService.java", "reason": "文件只读"}
                  ],
                  "clarification": ""
                }
                ```
                """);

        assertEquals("修复订单模块", plan.summary());
        assertEquals(2, plan.files().size());
        assertEquals("src/OrderService.java", plan.files().get(0).path());
        assertEquals(1, plan.skipped().size());
        assertTrue(plan.skipped().get(0).reason().contains("只读"));
    }

    @Test
    void malformedOrEmptyPlanBecomesClarification() {
        assertTrue(!EditPlanParser.parse("plain text").clarification().isBlank());
        assertTrue(!EditPlanParser.parse("").clarification().isBlank());
    }
}
