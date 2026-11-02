package com.tongji.cst3492.jentor.llm;

import com.tongji.cst3492.jentor.model.Chunk;
import com.tongji.cst3492.jentor.model.ContextBlock;
import com.tongji.cst3492.jentor.model.SourceReference;
import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.HardLineBreak;
import org.commonmark.node.Heading;
import org.commonmark.node.Node;
import org.commonmark.node.Paragraph;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.node.Text;
import org.commonmark.parser.Parser;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CitationValidator {
    private static final Parser MARKDOWN = Parser.builder().build();
    private static final Pattern CITATION_PATTERN = Pattern.compile(
            "\\[([^,\\]]+),\\s*(p\\.|pp\\.|slide|slides|section|sections|"
                    + "sheet|sheets|image|images)\\s*(\\d+)(?:\\s*-\\s*(\\d+))?\\]",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern GENERAL_TITLE = sectionPattern("General supplement", "通用补充");
    private static final Pattern COURSE_TITLE = sectionPattern("Course basis", "课程依据");

    public record Citation(
            String raw,
            String document,
            String locatorType,
            int startPage,
            int endPage) {
    }

    public List<Citation> parse(String answer) {
        return analyze(answer).citations;
    }

    private static List<Citation> parseText(String answer) {
        List<Citation> citations = new ArrayList<>();
        if (answer == null || answer.isBlank()) {
            return citations;
        }
        Matcher matcher = CITATION_PATTERN.matcher(answer);
        while (matcher.find()) {
            int start = Integer.parseInt(matcher.group(3));
            int end = matcher.group(4) == null ? start : Integer.parseInt(matcher.group(4));
            String prefix = matcher.group(2).toLowerCase(Locale.ROOT);
            citations.add(new Citation(
                    matcher.group(),
                    matcher.group(1).trim(),
                    locatorType(prefix),
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
        Analysis analysis = analyze(answer);
        List<Citation> citations = analysis.citations;
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
        if (analysis.hasGeneralCitation) {
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
        for (Citation citation : analyze(answer).courseCitations) {
            ContextBlock block = matchingBlock(citation, blocks);
            if (block == null) {
                continue;
            }
            String key = citation.document().toLowerCase(Locale.ROOT)
                    + "|" + citation.locatorType()
                    + "|" + citation.startPage() + "-" + citation.endPage();
            String locator = formatLocator(citation);
            sources.putIfAbsent(key, new SourceReference(
                    block.chunk().getFileName(),
                    block.chunk().getDocTitle(),
                block.chunk().getDocId(),
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
        return analyze(answer).hasCourseSection;
    }

    public boolean hasCitationInGeneralSection(String answer) {
        return analyze(answer).hasGeneralCitation;
    }

    private static Pattern sectionPattern(String english, String chinese) {
        return Pattern.compile("^(?:" + english + "|" + chinese
                + ")(?:\\s*[:：/（(—–-].*)?$", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }

    private static String sectionTitle(String line) {
        return line.strip().replaceFirst("^#{1,6}\\s*", "")
                .replaceFirst("^\\d+[.)、]\\s*", "").strip();
    }

    /** Resolve real Markdown sections, rather than treating a keyword as the rest of the answer. */
    private static Analysis analyze(String answer) {
        Analysis result = new Analysis();
        if (answer == null || answer.isBlank()) {
            return result;
        }
        int generalLevel = 0;
        Node document = MARKDOWN.parse(answer);
        for (Node node = document.getFirstChild(); node != null; node = node.getNext()) {
            String text = visibleText(node);
            if (node instanceof Heading heading) {
                if (generalLevel > 0 && heading.getLevel() <= generalLevel) {
                    generalLevel = 0;
                }
                String title = sectionTitle(text);
                if (GENERAL_TITLE.matcher(title).matches() && generalLevel == 0) {
                    generalLevel = heading.getLevel();
                }
                result.hasCourseSection |= COURSE_TITLE.matcher(title).matches();
                result.add(text, generalLevel > 0);
            } else if (node instanceof Paragraph) {
                // Retain support for plain/bold section labels used by existing clients.
                StringBuilder paragraph = new StringBuilder();
                for (String line : text.split("\\R", -1)) {
                    String title = sectionTitle(line);
                    boolean generalTitle = GENERAL_TITLE.matcher(title).matches();
                    boolean courseTitle = COURSE_TITLE.matcher(title).matches();
                    if (generalTitle || courseTitle) {
                        result.add(paragraph.toString(), generalLevel > 0);
                        paragraph.setLength(0);
                        generalLevel = generalTitle ? 2 : 0;
                        result.hasCourseSection |= courseTitle;
                    }
                    paragraph.append(line).append('\n');
                }
                result.add(paragraph.toString(), generalLevel > 0);
            } else {
                result.add(text, generalLevel > 0);
            }
        }
        return result;
    }

    private static String visibleText(Node node) {
        StringBuilder text = new StringBuilder();
        node.accept(new AbstractVisitor() {
            @Override
            public void visit(Text value) {
                text.append(value.getLiteral());
            }

            @Override
            public void visit(SoftLineBreak lineBreak) {
                text.append('\n');
            }

            @Override
            public void visit(HardLineBreak lineBreak) {
                text.append('\n');
            }

            @Override
            public void visit(Paragraph paragraph) {
                visitChildren(paragraph);
                text.append('\n');
            }
        });
        // Code nodes store literals, not Text children, so examples do not become references.
        return text.toString();
    }

    private static final class Analysis {
        private final List<Citation> citations = new ArrayList<>();
        private final List<Citation> courseCitations = new ArrayList<>();
        private boolean hasCourseSection;
        private boolean hasGeneralCitation;

        private void add(String text, boolean general) {
            List<Citation> found = parseText(text);
            citations.addAll(found);
            if (general) {
                hasGeneralCitation |= !found.isEmpty();
            } else {
                courseCitations.addAll(found);
            }
        }
    }

    private ContextBlock matchingBlock(Citation citation, List<ContextBlock> blocks) {
        for (ContextBlock block : blocks) {
            Chunk chunk = block.chunk();
            if (!documentMatches(citation.document(), chunk)) {
                continue;
            }
            if (!citation.locatorType().equalsIgnoreCase(chunk.getLocatorType())) {
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

    private static String locatorType(String prefix) {
        return switch (prefix) {
            case "slide", "slides" -> "SLIDE";
            case "section", "sections" -> "SECTION";
            case "sheet", "sheets" -> "SHEET";
            case "image", "images" -> "IMAGE";
            default -> "PAGE";
        };
    }

    private static String formatLocator(Citation citation) {
        String singular;
        String plural;
        switch (citation.locatorType()) {
            case "SLIDE":
                singular = "slide ";
                plural = "slides ";
                break;
            case "SECTION":
                singular = "section ";
                plural = "sections ";
                break;
            case "SHEET":
                singular = "sheet ";
                plural = "sheets ";
                break;
            case "IMAGE":
                singular = "image ";
                plural = "images ";
                break;
            default:
                singular = "p.";
                plural = "pp.";
                break;
        }
        return citation.startPage() == citation.endPage()
                ? singular + citation.startPage()
                : plural + citation.startPage() + "-" + citation.endPage();
    }
}
