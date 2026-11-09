package com.tongji.cst3492.jentor.llm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LlmConfigTest {
    @Test
    void structuredTasksHaveRoomForReasoningAndUseDeterministicSampling() {
        LlmConfig original = new LlmConfig(
                "https://example.invalid", "key", "MiniMax-M3.1-Flash", 0.4, 600);

        LlmConfig structured = original.forStructuredTask();

        assertEquals(4_096, structured.maxTokens());
        assertEquals(0.0, structured.temperature());
        assertEquals(original.baseUrl(), structured.baseUrl());
        assertEquals(original.apiKey(), structured.apiKey());
        assertEquals(original.model(), structured.model());
        assertEquals(600, original.maxTokens());
    }

    @Test
    void fullFileEditsDoNotInheritTheSmallChatBudget() {
        LlmConfig edit = new LlmConfig(
                "url", "key", "minimax/MiniMax-M3.1-Flash", 0.2, 1_500).forCodeEdit();

        assertEquals(8_192, edit.maxTokens());
        assertEquals(0.2, edit.temperature());
    }

    @Test
    void stageBudgetsPreserveHigherConfiguredLimits() {
        LlmConfig original = new LlmConfig("url", "key", "MiniMax-M3", 0.2, 16_384);

        assertEquals(16_384, original.forStructuredTask().maxTokens());
        assertEquals(16_384, original.forCodeEdit().maxTokens());
    }

    @Test
    void customProvidersCanKeepTheirLowerOutputLimits() {
        LlmConfig original = new LlmConfig("url", "key", "small-local-model", 0.2, 512);

        assertEquals(512, original.forStructuredTask().maxTokens());
        assertEquals(0.0, original.forStructuredTask().temperature());
        assertEquals(512, original.forCodeEdit().maxTokens());
        assertEquals(0.2, original.forCodeEdit().temperature());
    }
}
