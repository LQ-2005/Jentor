package com.tongji.cst3492.jentor.llm;

import com.tongji.cst3492.jentor.context.CodeFilePolicy;
import com.tongji.cst3492.jentor.model.CodeFileSnapshot;
import com.tongji.cst3492.jentor.model.WorkspaceOutline;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class EditPlanPromptBuilderTest {
    @Test
    void suppliesPreviousAnalysisAndWorkspaceFiles() {
        CodeFileSnapshot order = CodeFilePolicy.snapshot(
                "src/Order.java",
                "JAVA",
                "class Order { void run() { service.cancelOrder(null); } }");
        List<ChatMessage> messages = new EditPlanPromptBuilder().build(
                "按刚才的分析都修",
                "建议修复 cancelOrder 空指针",
                "src/Order.java",
                "",
                List.of(new WorkspaceOutline(
                        "src/OrderService.java",
                        "com.example",
                        "OrderService",
                        20,
                        List.of("void cancelOrder()"))),
                List.of(order),
                "zh");

        String prompt = messages.get(1).content();
        assertTrue(prompt.contains("ACTIVE FILE:"));
        assertTrue(prompt.contains("src/Order.java"));
        assertTrue(prompt.contains("SOURCE SNAPSHOT:"));
        assertTrue(prompt.contains("service.cancelOrder"));
        assertTrue(prompt.contains("PREVIOUS ASSISTANT ANALYSIS:"));
        assertTrue(prompt.contains("cancelOrder 空指针"));
        assertTrue(prompt.contains("src/OrderService.java"));
        assertTrue(prompt.contains("LATEST USER REQUEST:"));
        assertTrue(messages.get(0).content().contains("CREATE"));
    }

    @Test
    void preferredTargetIsPrimaryButMayExpandToRelatedFiles() {
        CodeFileSnapshot service = CodeFilePolicy.snapshot(
                "src/main/java/demo/GradeService.java",
                "JAVA",
                "class GradeService { double average(int[] scores) { return 0; } }");
        CodeFileSnapshot demo = CodeFilePolicy.snapshot(
                "src/main/java/demo/GradeDemo.java",
                "JAVA",
                "class GradeDemo { public static void main(String[] args) { } }");
        List<ChatMessage> messages = new EditPlanPromptBuilder().build(
                "Add countPassingStudents and update GradeDemo to print it.",
                "",
                "src/main/java/demo/GradeService.java",
                "",
                List.of(
                        new WorkspaceOutline(
                                "src/main/java/demo/GradeService.java",
                                "demo", "GradeService", 20, List.of("average(int[])")),
                        new WorkspaceOutline(
                                "src/main/java/demo/GradeDemo.java",
                                "demo", "GradeDemo", 10, List.of("main(String[])"))),
                List.of(service, demo),
                "en",
                "src/main/java/demo/GradeService.java");

        assertTrue(messages.get(0).content().contains("PREFERRED TARGET"));
        assertTrue(messages.get(0).content().contains(
                "may still include other source"));
        assertTrue(messages.get(1).content().contains(
                "PREFERRED TARGET:\nsrc/main/java/demo/GradeService.java"));
        assertTrue(messages.get(1).content().contains(
                "src/main/java/demo/GradeDemo.java"));
    }
}
