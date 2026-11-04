package com.tongji.cst3492.jentor.context;

import java.util.List;
import java.util.Locale;

/** Lightweight intent routing so code editing feels like ordinary conversation. */
public final class CodeEditIntent {
    private static final List<String> ACTION_TERMS = List.of(
            "修改", "修复", "重构", "改成", "改为", "替换", "删除", "移除",
            "改一下", "改下", "增加", "添加", "实现", "优化", "提取", "重命名",
            "fix", "repair", "refactor", "change", "modify", "replace",
            "remove", "add ", "implement", "extract", "rename", "optimize");
    private static final List<String> REQUEST_PREFIXES = List.of(
            "帮我", "请你", "请直接", "直接", "能否", "能不能", "可以帮",
            "能帮", "can you", "could you", "please");
    private static final List<String> ADOPTION_PHRASES = List.of(
            "修", "修吧", "都修", "全修", "改吧", "按这个改", "就按这个改",
            "都改", "全部修", "全部改", "按你说的", "按你刚才", "按上面",
            "采纳", "采纳第", "修复第", "修第", "第一个", "第二个",
            "执行", "开始修改", "fix it", "do it",
            "apply it", "go ahead");

    private CodeEditIntent() {
    }

    public static boolean isDirectEditRequest(String question) {
        String text = normalized(question);
        if (text.isEmpty()) {
            return false;
        }
        boolean action = ACTION_TERMS.stream().anyMatch(text::contains);
        if (!action) {
            return false;
        }
        if (text.contains("解释")
                || text.contains("说明")
                || text.contains("讲解")
                || text.contains("explain")) {
            return false;
        }
        boolean explicitlyRequested = REQUEST_PREFIXES.stream().anyMatch(text::contains);
        return explicitlyRequested || !isInterrogative(text);
    }

    public static boolean isAdoption(String question, String previousAssistantAnswer) {
        if (!hasActionableAdvice(previousAssistantAnswer)) {
            return false;
        }
        String text = normalized(question);
        return ADOPTION_PHRASES.stream().anyMatch(phrase ->
                text.equals(phrase) || (phrase.length() > 1 && text.contains(phrase)));
    }

    public static boolean hasActionableAdvice(String assistantAnswer) {
        if (assistantAnswer == null || assistantAnswer.isBlank()) {
            return false;
        }
        String advice = normalized(assistantAnswer);
        return advice.contains("建议")
                || advice.contains("可以改")
                || advice.contains("应该")
                || advice.contains("修复")
                || advice.contains("重构")
                || advice.contains("空指针")
                || advice.contains("越界")
                || advice.contains(".java")
                || advice.contains("bug")
                || advice.contains("issue")
                || advice.contains("refactor")
                || advice.contains("fix");
    }

    private static boolean isInterrogative(String text) {
        return text.startsWith("如何")
                || text.startsWith("怎么")
                || text.startsWith("为什么")
                || text.startsWith("什么是")
                || text.startsWith("what")
                || text.startsWith("how")
                || text.startsWith("why")
                || text.endsWith("?")
                || text.endsWith("？")
                || text.endsWith("吗")
                || text.endsWith("嗎");
    }

    private static String normalized(String text) {
        return text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
    }
}
