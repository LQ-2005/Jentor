package com.tongji.cst3492.jentor.context;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CodeEditIntentTest {
    @Test
    void recognizesNaturalModificationRequests() {
        assertTrue(CodeEditIntent.isDirectEditRequest("修复这段代码里的空指针"));
        assertTrue(CodeEditIntent.isDirectEditRequest("把 paymentFee 重构为策略模式"));
        assertTrue(CodeEditIntent.isDirectEditRequest("Please fix the boundary check"));
        assertTrue(CodeEditIntent.isDirectEditRequest("你能帮我改一下这个方法吗？"));
    }

    @Test
    void leavesInformationalQuestionsInNormalChat() {
        assertFalse(CodeEditIntent.isDirectEditRequest("如何修复这个空指针？"));
        assertFalse(CodeEditIntent.isDirectEditRequest("什么是策略模式？"));
        assertFalse(CodeEditIntent.isDirectEditRequest("先检查我的代码"));
        assertFalse(CodeEditIntent.isDirectEditRequest("请解释如何重构 paymentFee"));
        assertFalse(CodeEditIntent.isDirectEditRequest("What is an array?"));
    }

    @Test
    void recognizesAdoptionOnlyAfterAnAssistantSuggestion() {
        assertTrue(CodeEditIntent.isAdoption("都修吧", "建议修复 cancelOrder 和 findOrder"));
        assertTrue(CodeEditIntent.isAdoption("按这个改", "建议使用策略模式"));
        assertFalse(CodeEditIntent.isAdoption("都修吧", ""));
        assertFalse(CodeEditIntent.isAdoption("继续解释", "建议修复两个方法"));
        assertFalse(CodeEditIntent.isAdoption("好的", "我是 Jentor，你的课程助教。"));
    }
}
