package com.tongji.cst3492.jentor.context;

import com.tongji.cst3492.jentor.index.LocalEmbedder;
import com.tongji.cst3492.jentor.index.Tokens;
import com.tongji.cst3492.jentor.model.Chunk;
import com.tongji.cst3492.jentor.model.CourseIndex;
import com.tongji.cst3492.jentor.model.DocumentMeta;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Builds the same page-aware index as tools/build_index.py, but entirely inside
 * the plugin. PDFBox is used only for text extraction; page bounds, chunking,
 * tokenization, IDF and vectors are deterministic and shared with the runtime.
 */
public final class PdfCourseIndexBuilder {
    public static final int DIMENSIONS = 512;
    public static final int MAX_CHARS = 900;
    public static final int OVERLAP_CHARS = 120;
    private static final Pattern TOKEN =
            Pattern.compile("[A-Za-z0-9_]+|[\\u4e00-\\u9fff]");

    private PdfCourseIndexBuilder() {
    }

    public static CourseIndex build(Path materialsDirectory) throws IOException {
        if (materialsDirectory == null || !Files.isDirectory(materialsDirectory)) {
            throw new IOException("Course materials directory does not exist: "
                    + materialsDirectory);
        }
        List<Path> pdfs = listPdfFiles(materialsDirectory);
        if (pdfs.isEmpty()) {
            throw new IOException("No PDF files found in " + materialsDirectory);
        }

        List<ParsedDocument> parsed = new ArrayList<>();
        Set<String> documentIds = new HashSet<>();

        for (Path pdf : pdfs) {
            String title = stripExtension(pdf.getFileName().toString());
            String documentId = slugify(title);
            if (!documentIds.add(documentId)) {
                throw new IOException("Duplicate course document id: " + documentId);
            }
            parsed.add(parse(pdf, documentId));
        }
        return aggregate(parsed);
    }

    public static ParsedDocument parse(Path pdf) throws IOException {
        String title = stripExtension(pdf.getFileName().toString());
        return parse(pdf, slugify(title));
    }

    public static ParsedDocument parse(Path pdf, String documentId) throws IOException {
        if (pdf == null || !Files.isRegularFile(pdf)) {
            throw new IOException("Course PDF does not exist: " + pdf);
        }
        List<ExtractedPage> pages = extractPages(pdf);
        String title = stripExtension(pdf.getFileName().toString());
        DocumentMeta document = new DocumentMeta(documentId, title,
                pdf.getFileName().toString(), pages.size());
        List<RawChunk> chunks = new ArrayList<>();
        int tableCount = 0;
        int imageCount = 0;
        boolean ocrUsed = false;
        for (ExtractedPage page : pages) {
            tableCount += page.tableCount();
            imageCount += page.imageCount();
            ocrUsed |= page.ocrUsed();
            String pageTitle = pageTitle(page.text());
            List<String> pieces = splitPage(page.text());
            for (int chunkIndex = 0; chunkIndex < pieces.size(); chunkIndex++) {
                chunks.add(new RawChunk(
                        documentId,
                        title,
                        pdf.getFileName().toString(),
                        "PAGE",
                        page.number(),
                        chunkIndex,
                        pageTitle,
                        pieces.get(chunkIndex)));
            }
        }
        if (chunks.isEmpty()) {
            throw new IOException("No extractable text found in " + pdf.getFileName());
        }
        return new ParsedDocument(document, chunks, tableCount, imageCount, ocrUsed,
                ocrUsed ? "PDFBox + Tess4J OCR" : "PDFBox");
    }

    public static CourseIndex aggregate(List<ParsedDocument> parsedDocuments) {
        if (parsedDocuments == null || parsedDocuments.isEmpty()) {
            throw new IllegalArgumentException("At least one parsed course document is required");
        }
        List<DocumentMeta> documents = new ArrayList<>();
        List<RawChunk> pending = new ArrayList<>();
        Set<String> documentIds = new HashSet<>();
        for (ParsedDocument parsed : parsedDocuments) {
            if (!documentIds.add(parsed.document().getId())) {
                throw new IllegalArgumentException(
                        "Duplicate course document id: " + parsed.document().getId());
            }
            documents.add(parsed.document());
            pending.addAll(parsed.chunks());
        }
        if (pending.isEmpty()) {
            throw new IllegalArgumentException(
                    "The selected PDFs did not contain extractable text");
        }

        Map<String, Integer> documentFrequency = new HashMap<>();
        Map<String, List<String>> tokensByChunk = new LinkedHashMap<>();
        for (RawChunk chunk : pending) {
            List<String> tokens = Tokens.tokenize(chunk.indexedText());
            tokensByChunk.put(chunk.key(), tokens);
            for (String token : new HashSet<>(tokens)) {
                documentFrequency.merge(token, 1, Integer::sum);
            }
        }

        int chunkCount = pending.size();
        Map<String, Float> idf = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : documentFrequency.entrySet()) {
            double value = Math.log((chunkCount + 1.0) / (entry.getValue() + 1.0)) + 1.0;
            idf.put(entry.getKey(), round6(value));
        }
        float defaultIdf = round6(Math.log(chunkCount + 1.0) + 1.0);

        List<Chunk> chunks = new ArrayList<>(chunkCount);
        for (RawChunk pendingChunk : pending) {
            float[] vector = LocalEmbedder.embed(
                    pendingChunk.indexedText(), DIMENSIONS, idf, defaultIdf);
            roundVector(vector);
            chunks.add(new Chunk(
                    pendingChunk.key(),
                    pendingChunk.docId(),
                    pendingChunk.docTitle(),
                    pendingChunk.fileName(),
                    pendingChunk.locatorType(),
                    pendingChunk.page(),
                    pendingChunk.page(),
                    pendingChunk.chunkIndex(),
                    pendingChunk.title(),
                    pendingChunk.text(),
                    vector));
        }

        return CourseIndex.create(
                1,
                "local-sha256-tfidf",
                DIMENSIONS,
                chunkCount,
                defaultIdf,
                idf,
                documents,
                chunks);
    }

    public static List<ParsedDocument> fromIndex(CourseIndex index) {
        Map<String, List<RawChunk>> chunksByDocument = new LinkedHashMap<>();
        for (Chunk chunk : index.getChunks()) {
            chunksByDocument.computeIfAbsent(chunk.getDocId(), ignored -> new ArrayList<>())
                    .add(new RawChunk(
                            chunk.getDocId(),
                            chunk.getDocTitle(),
                            chunk.getFileName(),
                            chunk.getLocatorType(),
                            chunk.getPageStart(),
                            chunk.getChunkIndex(),
                            chunk.getTitle(),
                            chunk.getText()));
        }
        List<ParsedDocument> result = new ArrayList<>();
        for (DocumentMeta document : index.getDocuments()) {
            List<RawChunk> chunks = chunksByDocument.get(document.getId());
            if (chunks != null && !chunks.isEmpty()) {
                result.add(new ParsedDocument(document, chunks));
            }
        }
        return result;
    }

    public static List<Path> listPdfFiles(Path directory) throws IOException {
        try (Stream<Path> files = Files.list(directory)) {
            return files
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)
                            .endsWith(".pdf"))
                    .sorted(Comparator.comparing(
                            path -> path.getFileName().toString().toLowerCase(Locale.ROOT)))
                    .toList();
        }
    }

    private static List<ExtractedPage> extractPages(Path pdf) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf.toFile())) {
            List<ExtractedPage> pages = new ArrayList<>();
            PDFRenderer renderer = new PDFRenderer(document);
            for (int page = 1; page <= document.getNumberOfPages(); page++) {
                String layout = cleanText(stripPage(document, page, true), page);
                String plain = cleanText(stripPage(document, page, false), page);
                String text = choosePageText(layout, plain);
                TableMarkup markup = annotateTables(text);
                StringBuilder combined = new StringBuilder(markup.text());
                int images = 0;
                boolean ocrUsed = false;
                PDPage pdfPage = document.getPage(page - 1);
                for (String imageText : ocrEmbeddedImages(pdfPage)) {
                    images++;
                    if (!imageText.isBlank()) {
                        combined.append("\n[Image OCR]\n").append(imageText);
                        ocrUsed = true;
                    }
                }
                if (!OcrEngine.isLikelyUsefulText(combined.toString())) {
                    try {
                        String pageOcr = OcrEngine.recognize(renderer.renderImageWithDPI(
                                page - 1, 180, ImageType.RGB));
                        if (!pageOcr.isBlank()) {
                            combined.append("\n[OCR]\n").append(pageOcr);
                            ocrUsed = true;
                        }
                    } catch (Exception | LinkageError ignored) {
                        // Keep native text when OCR is unavailable or fails.
                    }
                }
                text = combined.toString().strip();
                if (!text.isBlank()) {
                    pages.add(new ExtractedPage(page, text, markup.tableCount(), images, ocrUsed));
                }
            }
            return pages;
        } catch (IOException error) {
            throw new IOException("Cannot parse " + pdf.getFileName() + ": "
                    + error.getMessage(), error);
        }
    }

    private static List<String> ocrEmbeddedImages(PDPage page) {
        List<String> result = new ArrayList<>();
        try {
            PDResources resources = page.getResources();
            if (resources == null) {
                return result;
            }
            for (COSName name : resources.getXObjectNames()) {
                PDXObject object = resources.getXObject(name);
                if (!(object instanceof PDImageXObject image)) {
                    continue;
                }
                if (image.getWidth() < 160 || image.getHeight() < 80) {
                    continue;
                }
                try {
                    result.add(OcrEngine.recognize(image.getImage()));
                } catch (Exception | LinkageError ignored) {
                    result.add("");
                }
            }
        } catch (IOException ignored) {
            // A malformed image does not invalidate the page's text layer.
        }
        return result;
    }

    private static TableMarkup annotateTables(String text) {
        String[] lines = text.split("\\R", -1);
        List<String> output = new ArrayList<>(lines.length);
        int tables = 0;
        int i = 0;
        while (i < lines.length) {
            int columns = columnCount(lines[i]);
            if (columns < 2) {
                output.add(lines[i++]);
                continue;
            }
            int start = i;
            List<String> block = new ArrayList<>();
            int expectedColumns = columns;
            while (i < lines.length && columnCount(lines[i]) == expectedColumns) {
                block.add(toColumns(lines[i]));
                i++;
            }
            if (block.size() >= 2) {
                tables++;
                output.add("[TABLE]");
                output.addAll(block);
                output.add("[/TABLE]");
            } else {
                for (int j = start; j < i; j++) {
                    output.add(lines[j]);
                }
            }
        }
        return new TableMarkup(String.join("\n", output), tables);
    }

    private static int columnCount(String line) {
        String stripped = line.strip();
        if (stripped.isEmpty()) {
            return 0;
        }
        int explicit = stripped.split("\\t|\\s{2,}").length;
        if (explicit >= 2) {
            return explicit;
        }
        if (looksLikeDenseRow(stripped)) {
            return stripped.split("\\s+").length;
        }
        return 1;
    }

    private static boolean looksLikeDenseRow(String line) {
        if (line.length() > 160 || line.matches(".*[.!?;:].*")) {
            return false;
        }
        String[] words = line.split("\\s+");
        if (words.length < 2 || words.length > 8) {
            return false;
        }
        return Character.isUpperCase(words[0].charAt(0))
                || words[0].matches(".*[A-Z_$].*");
    }

    private static String toColumns(String line) {
        String stripped = line.stripTrailing();
        if (stripped.contains("\t") || stripped.matches(".*\\s{2,}.*")) {
            return stripped.replaceAll("\\s{2,}", "\t");
        }
        return String.join("\t", stripped.strip().split("\\s+"));
    }

    private static String stripPage(PDDocument document, int page, boolean layout)
            throws IOException {
        PDFTextStripper stripper = new PDFTextStripper();
        stripper.setSortByPosition(layout);
        stripper.setStartPage(page);
        stripper.setEndPage(page);
        return stripper.getText(document);
    }

    static String cleanText(String text, int pageNumber) {
        String value = text == null ? "" : text.replace('\u00a0', ' ').replace("\f", "");
        StringBuilder cleaned = new StringBuilder();
        String[] lines = value.split("\\R", -1);
        for (String rawLine : lines) {
            String line = rawLine.stripTrailing();
            if (line.matches("\\s*" + pageNumber + "\\s*")) {
                continue;
            }
            if (line.length() < 160
                    && line.matches(".*\\s{2,}" + pageNumber + "\\s*")) {
                continue;
            }
            cleaned.append(line).append('\n');
        }
        String result = cleaned.toString().replaceAll("\\n{3,}", "\n\n").strip();
        String suffix = Integer.toString(pageNumber);
        if (result.endsWith(suffix) && result.length() > suffix.length()) {
            String prefix = result.substring(0, result.length() - suffix.length());
            char last = prefix.charAt(prefix.length() - 1);
            if (Character.isLetter(last) || ")]\"'".indexOf(last) >= 0) {
                result = prefix.stripTrailing();
            }
        }
        return result;
    }

    static String choosePageText(String layoutText, String plainText) {
        if (layoutText == null || layoutText.isBlank()) {
            return plainText == null ? "" : plainText;
        }
        if (plainText == null || plainText.isBlank()) {
            return layoutText;
        }
        double layoutRatio = singleLetterRatio(layoutText);
        double plainRatio = singleLetterRatio(plainText);
        if (layoutRatio > 0.12 && plainRatio < layoutRatio * 0.5) {
            return plainText;
        }
        if (layoutText.lines().count() >= 3 && plainText.lines().count() <= 1) {
            return layoutText;
        }
        return layoutText;
    }

    private static double singleLetterRatio(String text) {
        Matcher matcher = TOKEN.matcher(text);
        int total = 0;
        int single = 0;
        while (matcher.find()) {
            total++;
            String token = matcher.group();
            if (token.length() == 1 && Character.isLetter(token.charAt(0))) {
                single++;
            }
        }
        return total == 0 ? 0.0 : (double) single / total;
    }

    static String pageTitle(String text) {
        for (String line : text.split("\\R")) {
            String title = line.replaceFirst("^[ \\t•\\-–]+", "").strip();
            if (!title.isEmpty()) {
                return title.substring(0, Math.min(160, title.length()));
            }
        }
        return "";
    }

    static List<String> splitPage(String text) {
        if (text.length() <= MAX_CHARS) {
            return List.of(text);
        }
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + MAX_CHARS, text.length());
            if (end < text.length()) {
                int minimum = start + (int) (MAX_CHARS * 0.6);
                int newline = text.lastIndexOf('\n', end - 1);
                if (newline >= minimum) {
                    end = newline;
                }
            }
            String chunk = text.substring(start, end).strip();
            if (!chunk.isEmpty()) {
                chunks.add(chunk);
            }
            if (end >= text.length()) {
                break;
            }
            start = Math.max(end - OVERLAP_CHARS, start + 1);
        }
        return chunks;
    }

    static String slugify(String name) {
        String slug = name.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        if (!slug.isEmpty()) {
            return name.matches("[A-Za-z0-9 ._-]+")
                    ? slug
                    : slug + "-" + shortHash(name);
        }
        return "document-" + shortHash(name);
    }

    private static String shortHash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder suffix = new StringBuilder();
            for (int i = 0; i < 6; i++) {
                suffix.append(String.format(Locale.ROOT, "%02x", digest[i]));
            }
            return suffix.toString();
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is required", error);
        }
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static float round6(double value) {
        return (float) (Math.round(value * 1_000_000.0) / 1_000_000.0);
    }

    private static void roundVector(float[] vector) {
        for (int i = 0; i < vector.length; i++) {
            vector[i] = round6(vector[i]);
        }
    }

    private record ExtractedPage(
            int number,
            String text,
            int tableCount,
            int imageCount,
            boolean ocrUsed) {
    }

    private record TableMarkup(String text, int tableCount) {
    }

    public record ParsedDocument(
            DocumentMeta document,
            List<RawChunk> chunks,
            int tableCount,
            int imageCount,
            boolean ocrUsed,
            String parser) {
        public ParsedDocument {
            chunks = chunks == null ? List.of() : List.copyOf(chunks);
            parser = parser == null ? "" : parser;
        }

        public ParsedDocument(DocumentMeta document, List<RawChunk> chunks) {
            this(document, chunks, 0, 0, false, "");
        }
    }

    public record RawChunk(
            String docId,
            String docTitle,
            String fileName,
            String locatorType,
            int page,
            int chunkIndex,
            String title,
            String text) {
        private String key() {
            String prefix = switch (locatorType == null ? "PAGE" : locatorType.toUpperCase()) {
                case "SLIDE" -> "s";
                case "SECTION" -> "sec";
                case "SHEET" -> "sh";
                case "IMAGE" -> "img";
                default -> "p";
            };
            return docId + "-" + prefix + page + "-c" + chunkIndex;
        }

        private String indexedText() {
            return title + "\n" + title + "\n" + text;
        }
    }
}
