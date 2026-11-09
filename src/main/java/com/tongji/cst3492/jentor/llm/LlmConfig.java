package com.tongji.cst3492.jentor.llm;

import java.util.Locale;

public record LlmConfig(
        String baseUrl,
        String apiKey,
        String model,
        double temperature,
        int maxTokens) {

    /**
     * MiniMax M3 counts thinking against the output budget, even when the
     * visible routing decision or edit plan is only a small JSON object.
     */
    public LlmConfig forStructuredTask() {
        return new LlmConfig(baseUrl, apiKey, model, 0.0, reasoningBudget(4_096));
    }

    /** Full-file edits need room for both reasoning and the complete file. */
    public LlmConfig forCodeEdit() {
        return new LlmConfig(baseUrl, apiKey, model, temperature, reasoningBudget(8_192));
    }

    private int reasoningBudget(int minimum) {
        // Only raise the initial budget for the known reasoning-model family.
        // Unknown compatible providers may have lower hard output limits.
        return model != null && model.toLowerCase(Locale.ROOT).contains("minimax-m3")
                ? Math.max(minimum, maxTokens) : maxTokens;
    }
}
