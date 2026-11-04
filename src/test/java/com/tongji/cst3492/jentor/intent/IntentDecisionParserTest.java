package com.tongji.cst3492.jentor.intent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntentDecisionParserTest {
    @Test
    void parsesEditDecisionsAndTargetHints() {
        RouteDecision decision = IntentDecisionParser.parse("""
                ```json
                {
                  "intent": "EDIT",
                  "confidence": 0.93,
                  "instruction": "修复上一轮提到的三个问题",
                  "usePreviousAnalysis": true,
                  "targetHints": ["OrderService", "cancelOrder"],
                  "clarification": ""
                }
                ```
                """, "都修", true);

        assertEquals(ConversationIntent.EDIT, decision.intent());
        assertEquals(0.93, decision.confidence());
        assertTrue(decision.usePreviousAnalysis());
        assertEquals(2, decision.targetHints().size());
    }

    @Test
    void lowConfidenceAndMalformedOutputBecomeClarification() {
        assertEquals(ConversationIntent.CLARIFY, IntentDecisionParser.parse("""
                {"intent":"EDIT","confidence":0.3,"instruction":"fix"}
                """, "fix", true).intent());
        assertEquals(ConversationIntent.CLARIFY,
                IntentDecisionParser.parse("plain text", "fix", true).intent());
        assertFalse(IntentDecisionParser.parse("plain text", "fix", true)
                .clarification().isBlank());
    }
}
