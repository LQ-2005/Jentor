package com.tongji.cst3492.jentor.llm;

public record LlmConfig(
        String baseUrl,
        String apiKey,
        String model,
        double temperature,
        int maxTokens) {
}
