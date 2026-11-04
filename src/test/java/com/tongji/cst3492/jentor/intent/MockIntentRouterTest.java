package com.tongji.cst3492.jentor.intent;

import com.tongji.cst3492.jentor.llm.ChatMessage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MockIntentRouterTest {
    @Test
    void statusWinsOverTheWordModify() {
        RouteDecision decision = MockIntentRouter.route(
                "修改了哪些东西？", List.of(), "", true, true);
        assertEquals(ConversationIntent.STATUS, decision.intent());
    }

    @Test
    void editWinsWhenTheUserSaysCheckAndFix() {
        RouteDecision decision = MockIntentRouter.route(
                "检查并修复 OrderService", List.of(), "", true, true);
        assertEquals(ConversationIntent.EDIT, decision.intent());
    }

    @Test
    void analyzeDoesNotModifyCode() {
        RouteDecision decision = MockIntentRouter.route(
                "检查 OrderService 有什么问题", List.of(), "", true, true);
        assertEquals(ConversationIntent.ANALYZE, decision.intent());
    }

    @Test
    void adoptionUsesThePreviousAssistantAnswer() {
        RouteDecision decision = MockIntentRouter.route(
                "那就按你说的都修改",
                List.of(new ChatMessage("assistant", "建议修复 cancelOrder 的空指针")),
                "", true, true);
        assertEquals(ConversationIntent.EDIT, decision.intent());
    }
}
