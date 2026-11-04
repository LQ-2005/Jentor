package com.tongji.cst3492.jentor.intent;

import java.util.List;

/** Structured result of semantic conversation routing. */
public record RouteDecision(
        ConversationIntent intent,
        double confidence,
        String instruction,
        boolean usePreviousAnalysis,
        List<String> targetHints,
        String clarification) {

    public RouteDecision {
        intent = intent == null ? ConversationIntent.CLARIFY : intent;
        confidence = Math.max(0.0, Math.min(1.0, confidence));
        instruction = instruction == null ? "" : instruction.strip();
        targetHints = targetHints == null ? List.of() : List.copyOf(targetHints);
        clarification = clarification == null ? "" : clarification.strip();
    }

    public static RouteDecision clarify(String message) {
        return new RouteDecision(
                ConversationIntent.CLARIFY, 0.0, "", false, List.of(), message);
    }
}
