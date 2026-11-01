package com.tongji.cst3492.jentor.ui;

import org.commonmark.Extension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.Code;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.HardLineBreak;
import org.commonmark.node.Image;
import org.commonmark.node.IndentedCodeBlock;
import org.commonmark.node.Node;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.node.SourceSpan;
import org.commonmark.node.Text;
import org.commonmark.parser.IncludeSourceSpans;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.regex.Pattern;

/** Parses answer Markdown, keeping code blocks available for native code cards. */
final class MarkdownContent {
    private static final List<Extension> EXTENSIONS = List.of(TablesExtension.create());
    private static final Parser PARSER = Parser.builder()
            .extensions(EXTENSIONS)
            .includeSourceSpans(IncludeSourceSpans.BLOCKS)
            .build();
    private static final HtmlRenderer RENDERER = HtmlRenderer.builder()
            .extensions(EXTENSIONS)
            .escapeHtml(true)
            .sanitizeUrls(true)
            .build();
    private static final Pattern COMPACT_HEADING =
            Pattern.compile("^( {0,3})(#{2,6})([^#\\s].*)$");

    private MarkdownContent() {
    }

    static List<Part> parse(String markdown) {
        String source = markdown == null ? "" : markdown;
        Node document = PARSER.parse(source);
        String normalized = normalizeHeadings(source, document);
        if (!normalized.equals(source)) {
            document = PARSER.parse(normalized);
        }
        replaceImages(document);

        List<Part> parts = new ArrayList<>();
        StringBuilder html = new StringBuilder();
        for (Node node = document.getFirstChild(); node != null; node = node.getNext()) {
            if (node instanceof FencedCodeBlock code) {
                flushProse(parts, html);
                String info = code.getInfo().trim();
                String language = info.isEmpty() ? "" : info.split("\\s+", 2)[0];
                parts.add(new Part("", language, code.getLiteral()));
            } else if (node instanceof IndentedCodeBlock code) {
                flushProse(parts, html);
                parts.add(new Part("", "", code.getLiteral()));
            } else {
                html.append(RENDERER.render(node));
            }
        }
        flushProse(parts, html);
        return List.copyOf(parts);
    }

    private static void flushProse(List<Part> parts, StringBuilder html) {
        if (!html.isEmpty()) {
            parts.add(new Part(html.toString(), "", null));
            html.setLength(0);
        }
    }

    private static String normalizeHeadings(String source, Node document) {
        BitSet codeLines = new BitSet();
        document.accept(new AbstractVisitor() {
            @Override
            public void visit(FencedCodeBlock block) {
                protect(block);
            }

            @Override
            public void visit(IndentedCodeBlock block) {
                protect(block);
            }

            private void protect(Node block) {
                for (SourceSpan span : block.getSourceSpans()) {
                    codeLines.set(span.getLineIndex());
                }
            }
        });
        String[] lines = source.split("\\R", -1);
        boolean changed = false;
        for (int i = 0; i < lines.length; i++) {
            if (!codeLines.get(i)) {
                String next = COMPACT_HEADING.matcher(lines[i]).replaceFirst("$1$2 $3");
                changed |= !next.equals(lines[i]);
                lines[i] = next;
            }
        }
        return changed ? String.join("\n", lines) : source;
    }

    private static void replaceImages(Node document) {
        document.accept(new AbstractVisitor() {
            @Override
            public void visit(Image image) {
                StringBuilder alt = new StringBuilder();
                image.accept(new AbstractVisitor() {
                    @Override
                    public void visit(Text text) {
                        alt.append(text.getLiteral());
                    }

                    @Override
                    public void visit(Code code) {
                        alt.append(code.getLiteral());
                    }

                    @Override
                    public void visit(SoftLineBreak lineBreak) {
                        alt.append(' ');
                    }

                    @Override
                    public void visit(HardLineBreak lineBreak) {
                        alt.append(' ');
                    }
                });
                image.insertBefore(new Text(alt.toString()));
                image.unlink();
            }
        });
    }

    record Part(String html, String language, String code) {
    }
}
