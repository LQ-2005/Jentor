package com.tongji.cst3492.jentor.llm;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MockChatClientEditTest {
    private static final LlmConfig CONFIG = new LlmConfig("mock", "mock", "mock", 0.0, 256);

    @Test
    void anEditPromptProducesAParseableBlock() {
        String prompt = "FILE: src/A.java\nLANGUAGE: JAVA\n"
                + "--- BEGIN FILE ---\nclass A {}\n--- END FILE ---\n\n"
                + "INSTRUCTION:\nadd a field";

        String reply = new MockChatClient().complete(CONFIG, List.of(
                ChatMessage.system("Reply with a jentor-edit block."),
                ChatMessage.user(prompt)), new CancellationToken());

        EditProposalParser.Result result = EditProposalParser.parse(reply, 7L, "add a field");
        assertTrue(result.hasProposal(), reply);
        assertEquals("src/A.java", result.proposal().path());
        assertEquals(7L, result.proposal().baseModificationStamp());
        assertTrue(result.proposal().newText().contains("class A {}"));
        assertTrue(result.proposal().newText().contains("jentor mock edit"));
    }

    @Test
    void aTeachingPromptStillUsesTheCitationPath() {
        String reply = new MockChatClient().complete(CONFIG, List.of(
                ChatMessage.system("You are the teaching assistant."),
                ChatMessage.user("[K1] document=\"Lec-2.pdf\" locator=\"p.6\"\nquestion")),
                new CancellationToken());

        assertTrue(reply.contains("[Lec-2.pdf, p.6]"), reply);
    }
}
