package com.tongji.cst3492.jentor.intent;

import com.tongji.cst3492.jentor.llm.ChatMessage;

import java.util.List;
import java.util.Locale;

/** Deterministic route used only by offline Mock mode and unit tests. */
public final class MockIntentRouter {
    private MockIntentRouter() {
    }

    public static RouteDecision route(
            String question,
            List<ChatMessage> history,
            String lastAppliedEdit,
            boolean hasActiveFile,
            boolean zh) {
        String text = question == null ? "" : question.toLowerCase(Locale.ROOT);
        String advice = lastAssistant(history);
        if (containsAny(text, "改了什么", "修改了哪些", "成功了吗", "哪些文件",
                "what changed", "which files changed", "was it applied")) {
            return new RouteDecision(
                    ConversationIntent.STATUS, 0.95, question, false, List.of(), "");
        }
        boolean adoption = advice != null && containsAny(text,
                "都修", "都改", "全部修", "按你说的", "按你刚才", "采纳", "fix them", "do it");
        boolean edit = containsAny(text, "修复", "重构", "修改", "改成", "删除", "添加",
                "fix", "refactor", "change", "remove", "add");
        if ((edit || adoption) && hasActiveFile) {
            return new RouteDecision(
                    ConversationIntent.EDIT, 0.9, question, adoption, List.of(), "");
        }
        if (containsAny(text, "检查", "审查", "有什么问题", "有没有问题",
                "review", "analyze", "find issues")) {
            return new RouteDecision(
                    ConversationIntent.ANALYZE, 0.9, question, false, List.of(), "");
        }
        return new RouteDecision(
                ConversationIntent.CHAT, 0.9, question, false, List.of(), "");
    }

    private static String lastAssistant(List<ChatMessage> history) {
        if (history == null) {
            return null;
        }
        for (int index = history.size() - 1; index >= 0; index--) {
            ChatMessage message = history.get(index);
            if ("assistant".equals(message.role()) && message.content() != null
                    && !message.content().isBlank()) {
                return message.content();
            }
        }
        return null;
    }

    private static boolean containsAny(String text, String... terms) {
        for (String term : terms) {
            if (text.contains(term)) {
                return true;
            }
        }
        return false;
    }
}
