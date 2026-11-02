package com.tongji.cst3492.jentor.context;

import com.tongji.cst3492.jentor.index.LocalEmbedder;
import com.tongji.cst3492.jentor.index.Tokens;
import com.tongji.cst3492.jentor.model.Chunk;
import com.tongji.cst3492.jentor.model.CourseIndex;
import com.tongji.cst3492.jentor.model.DocumentMeta;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
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

        List<DocumentMeta> documents = new ArrayList<>();
        List<PendingChunk> pending = new ArrayList<>();
        Set<String> documentIds = new HashSet<>();

        for (Path pdf : pdfs) {
            List<PageText> pages = extractPages(pdf);
            String title = stripExtension(pdf.getFileName().toString());
            String documentId = slugify(title);
            if (!documentIds.add(documentId)) {
                throw new IOException("Duplicate course document id: " + documentId);
            }
            documents.add(new DocumentMeta(documentId, title, pdf.getFileName().toString(),
                    pages.size()));
            for (PageText page : pages) {
                String pageTitle = pageTitle(page.text());
                List<String> pieces = splitPage(page.text());
                for (int chunkIndex = 0; chunkIndex < pieces.size(); chunkIndex++) {
                    pending.add(new PendingChunk(
                            documentId,
                            title,
                            pdf.getFileName().toString(),
                            page.number(),
                            chunkIndex,
                            pageTitle,
                            pieces.get(chunkIndex)));
                }
            }
        }

        if (pending.isEmpty()) {
            throw new IOException("The selected PDFs did not contain extractable text");
        }

        Map<String, Integer> documentFrequency = new HashMap<>();
        Map<String, List<String>> tokensByChunk = new LinkedHashMap<>();
        for (PendingChunk chunk : pending) {
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
        for (PendingChunk pendingChunk : pending) {
            float[] vector = LocalEmbedder.embed(
                    pendingChunk.indexedText(), DIMENSIONS, idf, defaultIdf);
            roundVector(vector);
            chunks.add(new Chunk(
                    pendingChunk.key(),
                    pendingChunk.documentId(),
                    pendingChunk.documentTitle(),
                    pendingChunk.fileName(),
                    "PAGE",
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

    private static List<PageText> extractPages(Path pdf) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf.toFile())) {
            List<PageText> pages = new ArrayList<>();
            for (int page = 1; page <= document.getNumberOfPages(); page++) {
                String layout = cleanText(stripPage(document, page, true), page);
                String plain = cleanText(stripPage(document, page, false), page);
                String text = choosePageText(layout, plain);
                if (!text.isBlank()) {
                    pages.add(new PageText(page, text));
                }
            }
            return pages;
        } catch (IOException error) {
            throw new IOException("Cannot parse " + pdf.getFileName() + ": "
                    + error.getMessage(), error);
        }
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

    private record PageText(int number, String text) {
    }

    private record PendingChunk(
            String documentId,
            String documentTitle,
            String fileName,
            int page,
            int chunkIndex,
            String title,
            String text) {
        private String key() {
            return documentId + "-p" + page + "-c" + chunkIndex;
        }

        private String indexedText() {
            return title + "\n" + title + "\n" + text;
        }
    }
}
