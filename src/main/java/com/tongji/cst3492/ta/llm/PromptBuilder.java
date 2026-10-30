package com.tongji.cst3492.ta.llm;

import com.tongji.cst3492.ta.context.CodeFilePolicy;
import com.tongji.cst3492.ta.model.CodeFileSnapshot;
import com.tongji.cst3492.ta.model.WorkspaceContext;
import com.tongji.cst3492.ta.model.RetrievedChunk;
import com.tongji.cst3492.ta.model.CodeContext;
import com.tongji.cst3492.ta.model.ContextBlock;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class PromptBuilder {
    public static final String FALLBACK_SENTENCE =
            "Response is based on general knowledge; no specific course material is referenced.";
    public static final String FALLBACK_SENTENCE_ZH =
            "本回答基于通用知识，未引用具体课程材料。";

    private static final String ROLE = """
            You are the teaching assistant for CST3492 Java Enterprise Application Development.
            Be precise, concise, and helpful to a student working inside IntelliJ IDEA.
            Use the conversation history to resolve follow-up references such as
            "it", "that", or "the previous example". Answer the latest USER QUESTION.
            """;

    private static final String GROUNDED_RULES = """
            Answer using ONLY the course material excerpts below when they are relevant.
            For every claim taken from an excerpt, cite it exactly as
            [<document name>, p.<page>] or [<document name>, pp.<start>-<end>].
            Never invent a document name, page number, or quotation.
            Structure the answer with a "Course basis" (课程依据) section and, only if
            needed, a separate "General supplement" (通用补充) section. Do not attach
            course citations to general-knowledge content.
            Use Markdown headings for these sections. Keep every course-derived explanation
            and its citations in Course basis. A new code example that you write yourself
            belongs in General supplement, with no course citations in its code, comments,
            or explanation. Only present an example as a course example if it is actually
            included in the supplied excerpts; cite that example in Course basis.
            If the excerpts do not contain enough information, use the required fallback
            sentence specified below. Then answer from general Java knowledge and do not
            invent citations.
            """;

    private static final String GENERAL_RULES = """
            No sufficiently relevant course excerpts were retrieved for this question.
            Start with the required fallback sentence specified below.
            Then answer from general Java knowledge.
            Do not use a course-basis section, invent citations, or claim that a slide
            supports the answer.
            """;

    public List<ChatMessage> buildGroundedMessages(
            String question,
            CodeContext codeContext,
            RagContext context,
            int maxContextChars,
            String language,
            List<ChatMessage> history) {
        String prompt = "COURSE MATERIAL:\n"
                + context.text()
                + "\n\n"
                + buildIdeContext(codeContext)
                + "\nUSER QUESTION:\n"
                + question.trim();
        return buildMessages(
                ROLE + GROUNDED_RULES + languageInstruction(language),
                prompt,
                history);
    }

    public List<ChatMessage> buildGeneralMessages(
            String question,
            CodeContext codeContext,
            String language,
            List<ChatMessage> history) {
        String prompt = buildIdeContext(codeContext)
                + "\nUSER QUESTION:\n"
                + question.trim();
        return buildMessages(
                ROLE + GENERAL_RULES + languageInstruction(language),
                prompt,
                history);
    }

    public List<ChatMessage> buildCitationRetryMessages(
            String question,
            CodeContext codeContext,
            RagContext context,
            String previousAnswer,
            List<String> invalidCitations,
            int maxContextChars,
            String language,
            List<ChatMessage> history) {
        String retryInstruction = """
                Your previous answer contained citations that could not be verified against the
                provided excerpts: %s
                Rewrite the answer. Use only the excerpts below. Cite every course-derived claim
                exactly as [<document name>, p.<page>] or [<document name>, pp.<start>-<end>].
                Do not cite a page that is not listed below. If the excerpts are insufficient,
                use the required general-knowledge fallback sentence and do not invent citations.
                """.formatted(String.join("; ", invalidCitations));
        if (invalidCitations.contains("CITATION_IN_GENERAL_SECTION")) {
            retryInstruction += """
                    CITATION_IN_GENERAL_SECTION means that a course citation appeared inside
                    General supplement. Move course-supported statements AND their citations
                    into Course basis. Keep any original example and general explanation in
                    General supplement without course citations. Preserve valid course citations
                    in Course basis; do not simply remove them from course-derived claims.
                    """;
        }

        String prompt = "COURSE MATERIAL:\n"
                + context.text()
                + "\n\n"
                + buildIdeContext(codeContext)
                + "\nPREVIOUS ANSWER TO REWRITE:\n"
                + previousAnswer.trim()
                + "\n\nUSER QUESTION:\n"
                + question.trim();
        return buildMessages(
                ROLE + GROUNDED_RULES + retryInstruction + languageInstruction(language),
                prompt,
                history);
    }

    private List<ChatMessage> buildMessages(
            String systemPrompt,
            String userPrompt,
            List<ChatMessage> history) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system(systemPrompt));
        messages.addAll(trimHistory(history));
        messages.add(ChatMessage.user(userPrompt));
        return messages;
    }

    private List<ChatMessage> trimHistory(List<ChatMessage> history) {
        if (history == null || history.isEmpty()) {
            return List.of();
        }
        List<ChatMessage> result = new ArrayList<>();
        int budget = 3000;
        for (int i = history.size() - 1; i >= 0 && result.size() < 6; i--) {
            ChatMessage message = history.get(i);
            if (message == null || message.content() == null
                    || message.content().isBlank() || "system".equals(message.role())) {
                continue;
            }
            String content = message.content();
            if (content.length() > budget) {
                if (result.isEmpty()) {
                    content = content.substring(0, budget);
                } else {
                    break;
                }
            }
            result.add(0, new ChatMessage(message.role(), content));
            budget -= content.length();
            if (budget <= 0) {
                break;
            }
        }
        while (!result.isEmpty() && !"user".equals(result.get(0).role())) {
            result.remove(0);
        }
        return result;
    }

    private String languageInstruction(String language) {
        return "zh".equals(language)
                ? "The required fallback sentence is exactly:\n"
                + "\"" + FALLBACK_SENTENCE_ZH + "\"\n"
                + "Answer in Simplified Chinese."
                : "The required fallback sentence is exactly:\n"
                + "\"" + FALLBACK_SENTENCE + "\"\n"
                + "Answer in English.";
    }

    public RagContext buildRagContext(List<RetrievedChunk> chunks, int maxContextChars) {
        if (chunks == null || chunks.isEmpty()) {
            return new RagContext("(no course material retrieved)", List.of());
        }
        StringBuilder context = new StringBuilder();
        List<ContextBlock> blocks = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int index = 0;
        for (RetrievedChunk retrieved : chunks) {
            if (!seen.add(retrieved.getChunk().getId())) {
                continue;
            }
            index++;
            String header = "[K" + index + "] document=\""
                    + retrieved.getChunk().getFileName()
                    + "\" locator=\"" + retrieved.getChunk().locator() + "\"\n"
                    ;
            String text = retrieved.getChunk().getText().trim();
            boolean truncated = false;
            if (header.length() + text.length() + 2 > maxContextChars) {
                int available = maxContextChars - header.length() - 5;
                if (available <= 0) {
                    break;
                }
                if (available < text.length()) {
                    text = text.substring(0, available) + "...";
                    truncated = true;
                }
            }
            String block = header + text + "\n\n";
            if (context.length() + block.length() > maxContextChars && context.length() > 0) {
                break;
            }
            context.append(block);
            blocks.add(new ContextBlock(retrieved.getChunk(), text, truncated));
            if (truncated) {
                break;
            }
        }
        return new RagContext(context.toString().trim(), blocks);
    }

    public String buildIdeContext(CodeContext context) {
        if (context == null || context.isEmpty()) {
            return "IDE CONTEXT:\n(none)\n";
        }

        StringBuilder result = new StringBuilder();
        if (context.hasSelection()) {
            result.append(buildSelectionContext(context));
        }
        if (context.hasFiles()) {
            appendSection(result, buildWorkspaceFiles(context.files()));
        }
        if (context.hasWorkspace()) {
            appendSection(result, buildWorkspaceOutline(context.workspace()));
        }
        return result.toString();
    }

    private static void appendSection(StringBuilder target, String section) {
        if (section == null || section.isEmpty()) {
            return;
        }
        if (target.length() > 0) {
            target.append('\n');
        }
        target.append(section);
    }

    /**
     * Renders the workspace outline. This is the weakest context: it helps locate
     * code, it never answers for the slides, and it may be marked stale when the
     * workspace changed after it was built.
     */
    public String buildWorkspaceOutline(WorkspaceContext workspace) {
        if (workspace == null || workspace.isEmpty()) {
            return "";
        }
        StringBuilder result = new StringBuilder(workspace.text().stripTrailing()).append('\n');
        if (workspace.stale()) {
            result.append("NOTE: the workspace changed after this outline was built, ")
                    .append("so it may be outdated.\n");
        }
        result.append("--- end of project outline ---\n");
        return result.toString();
    }

    /**
     * Renders attached workspace files. This is the lowest-priority context: it is
     * placed after the selection under its own heading and says explicitly that
     * course material wins, so a stale or unrelated file cannot override a
     * grounded answer or invent course citations.
     */
    public String buildWorkspaceFiles(List<CodeFileSnapshot> files) {
        StringBuilder result = new StringBuilder(
                "WORKSPACE FILES (reference only, lowest priority: the course material and the "
                        + "USER QUESTION take precedence, and these files must never receive "
                        + "course citations. Use them whenever the question is about the "
                        + "project code):\n");
        int budget = CodeFilePolicy.MAX_TOTAL_CHARS;
        int index = 0;
        for (CodeFileSnapshot file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            String block = "--- " + (index + 1) + ". " + file.describe() + " ---\n"
                    + file.content() + "\n";
            if (block.length() > budget) {
                result.append("--- ").append(file.describe())
                        .append(" omitted: context budget reached ---\n");
                break;
            }
            budget -= block.length();
            result.append(block);
            index++;
        }
        result.append("--- end of workspace files ---\n");
        return result.toString();
    }

    private String buildSelectionContext(CodeContext context) {
        String method = truncateSignature(context.methodSignature(), 300);
        String declaringClass = truncateSignature(context.classSignature(), 300);
        String range = context.startLine() == context.endLine()
                ? "L" + context.startLine()
                : "L" + context.startLine() + "-" + context.endLine();

        StringBuilder header = new StringBuilder("IDE CONTEXT:\n");
        header.append("File: ").append(safe(context.fileName())).append('\n');
        if (!safe(context.language()).isBlank()) {
            header.append("Language: ").append(context.language()).append('\n');
        }
        header.append("Lines: ").append(range)
                .append(" (+").append(CodeContext.CONTEXT_LINES).append(" lines)\n");
        if (!declaringClass.isEmpty()) {
            header.append("Class: ").append(declaringClass).append('\n');
        }
        if (!method.isEmpty()) {
            header.append("Method: ").append(method).append('\n');
        }

        String selected = safe(context.selected()).trim();
        String before = safe(context.before()).trim();
        String after = safe(context.after()).trim();
        int remaining = 6000 - header.length();
        if (selected.length() > Math.max(1000, remaining - 300)) {
            selected = head(selected, Math.max(1000, remaining - 300));
        }
        remaining = Math.max(0, remaining - selected.length());
        int half = remaining / 2;
        before = tail(before, half);
        after = head(after, remaining - half);

        StringBuilder result = new StringBuilder(header);
        if (!before.isEmpty()) {
            result.append("--- code before ---\n").append(before).append("\n");
        }
        result.append("--- selected code ---\n").append(selected).append("\n");
        if (!after.isEmpty()) {
            result.append("--- code after ---\n").append(after).append("\n");
        }
        return result.toString();
    }

    private String truncateSignature(String signature, int maxLength) {
        String value = safe(signature).trim();
        return value.length() <= maxLength ? value : value.substring(0, maxLength) + "...";
    }

    private String head(String text, int maxLength) {
        if (maxLength <= 0) {
            return "";
        }
        if (text.length() <= maxLength) {
            return text;
        }
        return maxLength <= 3 ? "" : text.substring(0, maxLength - 3) + "...";
    }

    private String tail(String text, int maxLength) {
        if (maxLength <= 0) {
            return "";
        }
        if (text.length() <= maxLength) {
            return text;
        }
        return maxLength <= 3 ? "" : "..." + text.substring(text.length() - (maxLength - 3));
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
