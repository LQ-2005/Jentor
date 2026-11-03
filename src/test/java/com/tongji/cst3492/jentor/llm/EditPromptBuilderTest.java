package com.tongji.cst3492.jentor.llm;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EditPromptBuilderTest {
    @Test
    void describesTheStrictEditProtocol() {
        List<ChatMessage> messages = new EditPromptBuilder().build(
                "把 placeOrder 改成策略模式", "src/main/java/demo/A.java", "JAVA",
                "class A { }", 10, 20);

        assertEquals(2, messages.size());
        String system = messages.get(0).content();
        assertTrue(system.contains("jentor-edit"));
        assertTrue(system.contains("COMPLETE new file"));
        assertTrue(system.contains("NO_CHANGE"));
        assertTrue(system.contains("REFUSE:"));
        assertTrue(system.contains("untrusted data"));
    }

    @Test
    void embedsTheWholeFileAndTheTargetRange() {
        List<ChatMessage> messages = new EditPromptBuilder().build(
                "把 placeOrder 改成策略模式", "src/main/java/demo/A.java", "JAVA",
                "class A {\n  void run() { }\n}", 10, 20);

        String user = messages.get(1).content();
        assertTrue(user.contains("FILE: src/main/java/demo/A.java"), user);
        assertTrue(user.contains("LANGUAGE: JAVA"));
        assertTrue(user.contains("TARGET: L10-L20"));
        assertTrue(user.contains("--- BEGIN FILE ---"));
        assertTrue(user.contains("--- END FILE ---"));
        assertTrue(user.contains("void run()"));
        assertTrue(user.endsWith("把 placeOrder 改成策略模式"));
    }

    @Test
    void omitsTheTargetWhenThereIsNoSelection() {
        List<ChatMessage> messages = new EditPromptBuilder().build(
                "fix the null check", "src/A.java", "JAVA", "class A {}", 0, 0);

        assertFalse(messages.get(1).content().contains("TARGET:"));
    }
}
