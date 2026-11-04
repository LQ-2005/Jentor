package com.tongji.cst3492.jentor.intent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/** Parses and validates the semantic router's JSON response. */
public final class IntentDecisionParser {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final double MIN_CONFIDENCE = 0.65;

    private IntentDecisionParser() {
    }

    public static RouteDecision parse(String answer, String question, boolean zh) {
        if (answer == null || answer.isBlank()) {
            return fallback(zh);
        }
        int start = answer.indexOf('{');
        int end = answer.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return fallback(zh);
        }
        try {
            JsonNode root = MAPPER.readTree(answer.substring(start, end + 1));
            ConversationIntent intent;
            try {
                intent = ConversationIntent.valueOf(
                        root.path("intent").asText("").trim().toUpperCase());
            } catch (IllegalArgumentException invalidIntent) {
                return fallback(zh);
            }
            double confidence = root.path("confidence").asDouble(0.0);
            String instruction = root.path("instruction").asText("");
            boolean usePrevious = root.path("usePreviousAnalysis").asBoolean(false);
            List<String> hints = new ArrayList<>();
            JsonNode hintsNode = root.path("targetHints");
            if (hintsNode.isArray()) {
                hintsNode.forEach(node -> {
                    String hint = node.asText("").trim();
                    if (!hint.isBlank()) {
                        hints.add(hint);
                    }
                });
            }
            String clarification = root.path("clarification").asText("");
            if (confidence < MIN_CONFIDENCE
                    || intent == ConversationIntent.EDIT && instruction.isBlank()) {
                return new RouteDecision(
                        ConversationIntent.CLARIFY,
                        confidence,
                        "",
                        false,
                        hints,
                        clarification.isBlank()
                                ? (zh ? "我还不能确定你希望我做什么。可以再具体说明吗？"
                                : "I am not yet sure what you want me to do. Could you clarify?")
                                : clarification);
            }
            return new RouteDecision(
                    intent, confidence, instruction.isBlank() ? question : instruction,
                    usePrevious, hints, clarification);
        } catch (Exception error) {
            return fallback(zh);
        }
    }

    private static RouteDecision fallback(boolean zh) {
        return RouteDecision.clarify(zh
                ? "我还不能确定你希望我回答、分析还是修改代码。可以再具体说明吗？"
                : "I cannot yet tell whether you want an answer, analysis, or a code change. Could you clarify?");
    }
}
