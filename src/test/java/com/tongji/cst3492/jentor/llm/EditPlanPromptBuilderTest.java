package com.tongji.cst3492.jentor.llm;

import com.tongji.cst3492.jentor.model.WorkspaceOutline;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class EditPlanPromptBuilderTest {
    @Test
    void suppliesPreviousAnalysisAndWorkspaceFiles() {
        List<ChatMessage> messages = new EditPlanPromptBuilder().build(
                "按刚才的分析都修",
                "建议修复 cancelOrder 空指针",
                "src/Order.java",
                "class Order { void run() { service.cancelOrder(null); } }",
                List.of(new WorkspaceOutline(
                        "src/OrderService.java",
                        "com.example",
                        "OrderService",
                        20,
                        List.of("void cancelOrder()"))),
                "zh");

        String prompt = messages.get(1).content();
        assertTrue(prompt.contains("ACTIVE FILE:"));
        assertTrue(prompt.contains("src/Order.java"));
        assertTrue(prompt.contains("ACTIVE FILE CONTENT:"));
        assertTrue(prompt.contains("service.cancelOrder"));
        assertTrue(prompt.contains("PREVIOUS ASSISTANT ANALYSIS:"));
        assertTrue(prompt.contains("cancelOrder 空指针"));
        assertTrue(prompt.contains("src/OrderService.java"));
        assertTrue(prompt.contains("LATEST USER REQUEST:"));
        assertTrue(messages.get(0).content().contains("CREATE"));
    }
}
