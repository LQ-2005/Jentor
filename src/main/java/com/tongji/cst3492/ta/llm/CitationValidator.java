package com.tongji.cst3492.ta.llm;

import com.tongji.cst3492.ta.model.Chunk;
import com.tongji.cst3492.ta.model.ContextBlock;
import com.tongji.cst3492.ta.model.SourceReference;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CitationValidator {
    private static final Pattern CITATION_PATTERN = Pattern.compile(
            "\\[([^,\\]]+),\\s*(?:p\\.|pp\\.)\\s*(\\d+)(?:\\s*-\\s*(\\d+))?\\]",
            Pattern.CASE_INSENSITIVE);

    public record Citation(String raw, String document, int startPage, int endPage) {
    }

    public List<Citation> parse(String answer) {
        List<Citation> citations = new ArrayList<>();
        if (answer == null || answer.isBlank()) {
            return citations;
        }
        Matcher matcher = CITATION_PATTERN.matcher(answer);
        while (matcher.find()) {
            int start = Integer.parseInt(matcher.group(2));
            int end = matcher.group(3) == null ? start : Integer.parseInt(matcher.group(3));
            citations.add(new Citation(
                    matcher.group(),
                    matcher.group(1).trim(),
                    Math.min(start, end),
                    Math.max(start, end)));
        }
        return citations;
    }

    /**
     * Returns invalid citation strings. "NO_CITATIONS" means a grounded answer
     * contained no citation and did not explicitly switch to general knowledge.
     */
    public List<String> invalidCitations(String answer, List<ContextBlock> blocks) {
        List<Citation> citations = parse(answer);
        List<String> invalid = new ArrayList<>();
        if (citations.isEmpty()) {
            if (!hasFallbackSentence(answer)) {
                invalid.add("NO_CITATIONS");
            }
            return invalid;
        }
        for (Citation citation : citations) {
            if (matchingBlock(citation, blocks) == null) {
                invalid.add(citation.raw());
            }
        }
        if (hasCitationInGeneralSection(answer)) {
            invalid.add("CITATION_IN_GENERAL_SECTION");
        }
        return invalid;
    }

    /**
     * Sources for citations that were actually cited and matched the context.
     * Deduplicated by document name and page range.
     */
    public List<SourceReference> citedSources(String answer, List<ContextBlock> blocks) {
        Map<String, SourceReference> sources = new LinkedHashMap<>();
        String sourceText = answer == null ? "" : answer;
        int generalIndex = generalSectionIndex(sourceText);
        if (generalIndex >= 0) {
            sourceText = sourceText.substring(0, generalIndex);
        }
        for (Citation citation : parse(sourceText)) {
            ContextBlock block = matchingBlock(citation, blocks);
            if (block == null) {
                continue;
            }
            String key = citation.document().toLowerCase(Locale.ROOT)
                    + "|" + citation.startPage() + "-" + citation.endPage();
            String locator = citation.startPage() == citation.endPage()
                    ? "p." + citation.startPage()
                    : "pp." + citation.startPage() + "-" + citation.endPage();
            sources.putIfAbsent(key, new SourceReference(
                    block.chunk().getFileName(),
                    block.chunk().getDocTitle(),
                    citation.startPage(),
                    citation.endPage(),
                    locator,
                    excerpt(block),
                    block.chunk().getId()));
        }
        return List.copyOf(sources.values());
    }

    public boolean hasFallbackSentence(String answer) {
        return answer != null
                && (answer.contains(PromptBuilder.FALLBACK_SENTENCE)
                || answer.contains(PromptBuilder.FALLBACK_SENTENCE_ZH));
    }

    public boolean hasCourseSection(String answer) {
        if (answer == null) {
            return false;
        }
        String lower = answer.toLowerCase(Locale.ROOT);
        return answer.contains("课程依据") || lower.contains("course basis");
    }

    public boolean hasCitationInGeneralSection(String answer) {
        if (answer == null) {
            return false;
        }
        int index = generalSectionIndex(answer);
        return index >= 0 && !parse(answer.substring(index)).isEmpty();
    }

    private int generalSectionIndex(String answer) {
        String lower = answer.toLowerCase(Locale.ROOT);
        int chinese = answer.indexOf("通用补充");
        int english = lower.indexOf("general supplement");
        return chinese >= 0 && english >= 0
                ? Math.min(chinese, english)
                : Math.max(chinese, english);
    }

    private ContextBlock matchingBlock(Citation citation, List<ContextBlock> blocks) {
        for (ContextBlock block : blocks) {
            Chunk chunk = block.chunk();
            if (!documentMatches(citation.document(), chunk)) {
                continue;
            }
            int overlapStart = Math.max(citation.startPage(), chunk.getPageStart());
            int overlapEnd = Math.min(citation.endPage(), chunk.getPageEnd());
            if (overlapStart <= overlapEnd) {
                return block;
            }
        }
        return null;
    }

    private String excerpt(ContextBlock block) {
        String text = block.text() == null ? "" : block.text().trim();
        if (text.length() > 1200) {
            text = text.substring(0, 1200) + "...";
        }
        if (block.truncated() && !text.endsWith("...")) {
            text = text + "...";
        }
        return text;
    }

    private boolean documentMatches(String citedDocument, Chunk chunk) {
        String cited = normalize(citedDocument);
        return cited.equals(normalize(chunk.getFileName()))
                || cited.equals(normalize(chunk.getDocTitle()));
    }

    private String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }
}
