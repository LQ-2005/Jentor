package com.tongji.cst3492.ta.llm;

import com.tongji.cst3492.ta.index.Retriever;
import com.tongji.cst3492.ta.index.CourseGlossary;
import com.tongji.cst3492.ta.model.CodeContext;
import com.tongji.cst3492.ta.model.RetrievedChunk;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.function.Consumer;

public final class AnswerService {
    private final Retriever retriever;
    private final ChatClient chatClient;
    private final PromptBuilder promptBuilder;
    private final CitationValidator citationValidator;

    public AnswerService(Retriever retriever, ChatClient chatClient) {
        this.retriever = retriever;
        this.chatClient = chatClient;
        this.promptBuilder = new PromptBuilder();
        this.citationValidator = new CitationValidator();
    }

    public Answer answer(
            String question,
            CodeContext codeContext,
            LlmConfig config,
            int topK,
            double coverageThreshold,
            int maxContextChars,
            String language,
            List<ChatMessage> history,
            CancellationToken token,
            Consumer<String> stageListener) throws IOException {
        List<ChatMessage> safeHistory = history == null ? List.of() : history;
        token.throwIfCancelled();
        stage(stageListener, language, "retrieve");
        List<RetrievedChunk> retrieved = retriever.retrieve(
                buildRetrievalQuery(question, safeHistory, codeContext), topK);
        boolean grounded = retriever.isGrounded(retrieved, coverageThreshold);
        RagContext ragContext = promptBuilder.buildRagContext(retrieved, maxContextChars);
        token.throwIfCancelled();

        if (!grounded) {
            stage(stageListener, language, "generate");
            String text = chatClient.complete(
                    config,
                    promptBuilder.buildGeneralMessages(
                            question, codeContext, language, safeHistory),
                    token);
            String fallback = "zh".equals(language)
                    ? PromptBuilder.FALLBACK_SENTENCE_ZH
                    : PromptBuilder.FALLBACK_SENTENCE;
            if (!text.contains(fallback)) {
                text = fallback + "\n\n" + text;
            }
            text = stripDisclosure(text, fallback);
            return new Answer(
                    text,
                    List.of(),
                    retrieved,
                    false,
                    CitationStatus.NOT_APPLICABLE,
                    List.of(),
                    fallback);
        }

        stage(stageListener, language, "generate");
        String text = chatClient.complete(
                config,
                promptBuilder.buildGroundedMessages(
                        question, codeContext, ragContext, maxContextChars, language, safeHistory),
                token);
        token.throwIfCancelled();
        stage(stageListener, language, "validate");
        List<String> invalid = citationValidator.invalidCitations(text, ragContext.blocks());
        List<String> warnings = new ArrayList<>();
        if (!invalid.isEmpty()) {
            stage(stageListener, language, "repair");
            String retried = chatClient.complete(
                    config,
                    promptBuilder.buildCitationRetryMessages(
                            question,
                            codeContext,
                            ragContext,
                            text,
                            invalid,
                            maxContextChars,
                            language,
                            safeHistory),
                    token);
            token.throwIfCancelled();
            List<String> stillInvalid = citationValidator.invalidCitations(
                    retried, ragContext.blocks());
            text = retried;
            invalid = stillInvalid;
            if (!stillInvalid.isEmpty()) {
                warnings.add(("zh".equals(language) ? "引用校验失败：" : "Citation check failed: ")
                        + String.join("; ", stillInvalid));
            }
        }
        List<CitationValidator.Citation> parsed = citationValidator.parse(text);
        CitationStatus status;
        if (!invalid.isEmpty()) {
            status = CitationStatus.FAILED;
        } else if (parsed.isEmpty()) {
            if (citationValidator.hasFallbackSentence(text)
                    && !citationValidator.hasCourseSection(text)) {
                status = CitationStatus.NOT_APPLICABLE;
            } else {
                status = CitationStatus.FAILED;
                warnings.add(("zh".equals(language)
                        ? "回答缺少课程引用。"
                        : "The answer is missing course citations."));
            }
        } else {
            status = CitationStatus.VALIDATED;
        }
        List<com.tongji.cst3492.ta.model.SourceReference> sources =
                status == CitationStatus.NOT_APPLICABLE
                        ? List.of()
                        : citationValidator.citedSources(text, ragContext.blocks());
        String disclosure = "";
        if (status == CitationStatus.NOT_APPLICABLE) {
            String fallback = "zh".equals(language)
                    ? PromptBuilder.FALLBACK_SENTENCE_ZH
                    : PromptBuilder.FALLBACK_SENTENCE;
            if (text.contains(fallback)) {
                text = stripDisclosure(text, fallback);
                disclosure = fallback;
            }
        }
        return new Answer(
                text,
                sources,
                retrieved,
                true,
                status,
                warnings,
                disclosure);
    }

    private String stripDisclosure(String text, String disclosure) {
        String result = text.replace(disclosure, "");
        result = result.replaceAll("\\n{3,}", "\n\n").trim();
        return result;
    }

    private void stage(Consumer<String> listener, String language, String stage) {
        if (listener == null) {
            return;
        }
        boolean zh = "zh".equals(language);
        listener.accept(switch (stage) {
            case "retrieve" -> zh ? "检索课程资料" : "Retrieving course material";
            case "generate" -> zh ? "生成回答" : "Generating answer";
            case "validate" -> zh ? "校验引用" : "Validating citations";
            case "repair" -> zh ? "修复引用" : "Repairing citations";
            default -> "";
        });
    }

    private String buildRetrievalQuery(
            String question,
            List<ChatMessage> history,
            CodeContext codeContext) {
        String expanded = CourseGlossary.expand(question);
        String query;
        if (isFollowUp(question)) {
            String topic = findTopic(history);
            query = topic == null || topic.isBlank()
                    ? expanded
                    : topic + "\n" + expanded;
        } else {
            query = expanded;
        }
        return appendCodeSymbols(query, codeContext, question);
    }

    private boolean isFollowUp(String question) {
        String lower = question.toLowerCase(Locale.ROOT);
        return lower.contains(" it")
                || lower.contains("this")
                || lower.contains("that")
                || lower.contains("they")
                || lower.contains("them")
                || lower.contains("what about")
                || lower.contains("how about")
                || lower.contains("another example")
                || lower.contains("more detail")
                || lower.contains("tell me more")
                || question.contains("它")
                || question.contains("这个")
                || question.contains("那个")
                || question.contains("这些")
                || question.contains("那些")
                || question.startsWith("再")
                || question.contains("继续")
                || question.contains("还有")
                || question.contains("另外")
                || question.contains("举个")
                || question.contains("举例")
                || question.contains("详细")
                || question.contains("展开")
                || lower.equals("why?")
                || lower.equals("how?")
                || lower.equals("and?");
    }

    private String findTopic(List<ChatMessage> history) {
        for (int i = history.size() - 1; i >= 0; i--) {
            ChatMessage message = history.get(i);
            if ("user".equals(message.role()) && message.content() != null
                    && !message.content().isBlank()
                    && !isFollowUp(message.content())) {
                return message.content();
            }
        }
        return null;
    }

    private String appendCodeSymbols(
            String query,
            CodeContext codeContext,
            String question) {
        if (codeContext == null || !codeContext.hasSelection()) {
            return query;
        }
        String lower = question.toLowerCase(Locale.ROOT);
        boolean codeQuestion = question.contains("这段代码")
                || question.contains("这个代码")
                || question.contains("代码有")
                || question.contains("问题")
                || question.contains("bug")
                || lower.contains("this code")
                || lower.contains("what does this")
                || lower.contains("what is wrong")
                || lower.contains("improve")
                || lower.contains("explain");
        if (!codeQuestion) {
            return query;
        }

        Set<String> symbols = new LinkedHashSet<>();
        Matcher matcher = Pattern.compile(
                "@[A-Za-z_][A-Za-z0-9_]*|\\b[A-Z][A-Za-z0-9_]{2,}\\b|\\b[a-z][A-Za-z0-9_]*\\s*\\(")
                .matcher(codeContext.selected());
        while (matcher.find() && symbols.size() < 12) {
            String symbol = matcher.group().replace("(", "").trim();
            if (!Set.of("if", "for", "while", "switch", "catch", "return", "new",
                    "this", "super", "null", "true", "false").contains(symbol)) {
                symbols.add(symbol);
            }
        }
        return symbols.isEmpty()
                ? query
                : query + "\nCode symbols: " + String.join(" ", symbols);
    }

    public Retriever getRetriever() {
        return retriever;
    }
}
