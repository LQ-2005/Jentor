package com.tongji.cst3492.jentor.context;

import com.tongji.cst3492.jentor.model.DocumentMeta;
import org.apache.poi.hslf.usermodel.HSLFSlideShow;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.sl.extractor.SlideShowExtractor;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFPictureData;
import org.apache.poi.xslf.usermodel.XSLFPictureShape;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTable;
import org.apache.poi.xslf.usermodel.XSLFTableCell;
import org.apache.poi.xslf.usermodel.XSLFTableRow;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.apache.poi.xssf.usermodel.XSSFPicture;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFPictureData;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Routes supported course files to text, table, slide/sheet and OCR parsers. */
public final class CourseDocumentParser {
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(
            "pdf", "docx", "pptx", "xlsx", "doc", "ppt", "xls",
            "txt", "md", "markdown", "html", "htm", "csv",
            "png", "jpg", "jpeg", "bmp", "tif", "tiff");

    private CourseDocumentParser() {
    }

    public static boolean isSupported(Path path) {
        return path != null && Files.isRegularFile(path) && hasSupportedExtension(path);
    }

    public static boolean hasSupportedExtension(Path path) {
        return path != null && SUPPORTED_EXTENSIONS.contains(extension(path));
    }

    public static String fileType(Path path) {
        return extension(path).toUpperCase(Locale.ROOT);
    }

    public static List<Path> listSupportedFiles(Path directory) throws IOException {
        try (var files = Files.list(directory)) {
            return files.filter(CourseDocumentParser::isSupported)
                    .sorted(Comparator.comparing(
                            path -> path.getFileName().toString().toLowerCase(Locale.ROOT)))
                    .toList();
        }
    }

    public static PdfCourseIndexBuilder.ParsedDocument parse(
            Path path,
            String documentId) throws IOException {
        return switch (extension(path)) {
            case "pdf" -> PdfCourseIndexBuilder.parse(path, documentId);
            case "docx" -> parseDocx(path, documentId);
            case "pptx" -> parsePptx(path, documentId);
            case "xlsx" -> parseXlsx(path, documentId);
            case "doc" -> parseLegacyDoc(path, documentId);
            case "ppt" -> parseLegacyPpt(path, documentId);
            case "xls" -> parseLegacyXls(path, documentId);
            case "html", "htm" -> parseHtml(path, documentId);
            case "csv" -> parseCsv(path, documentId);
            case "png", "jpg", "jpeg", "bmp", "tif", "tiff" ->
                    parseImage(path, documentId);
            case "txt", "md", "markdown" -> parsePlainText(path, documentId);
            default -> throw new IOException("Unsupported course file: " + path);
        };
    }

    private static PdfCourseIndexBuilder.ParsedDocument parseDocx(
            Path path,
            String documentId) throws IOException {
        List<PdfCourseIndexBuilder.RawChunk> chunks = new ArrayList<>();
        int section = 0;
        int tables = 0;
        int images = 0;
        boolean ocr = false;
        try (InputStream input = Files.newInputStream(path);
             XWPFDocument document = new XWPFDocument(input)) {
            for (IBodyElement element : document.getBodyElements()) {
                if (element instanceof XWPFParagraph paragraph) {
                    String text = paragraph.getText().strip();
                    if (!text.isEmpty()) {
                        section++;
                        chunks.add(chunk(documentId, path, "SECTION", section, 0,
                                firstLine(text), text));
                    }
                } else if (element instanceof XWPFTable table) {
                    section++;
                    tables++;
                    String text = docxTable(table);
                    if (!text.isBlank()) {
                        chunks.add(chunk(documentId, path, "SECTION", section, 0,
                                "[Table] " + path.getFileName(), text));
                    }
                }
            }
            for (XWPFPictureData picture : document.getAllPictures()) {
                images++;
                String text = recognize(picture.getData());
                if (!text.isBlank()) {
                    section++;
                    ocr = true;
                    chunks.add(chunk(documentId, path, "SECTION", section, 0,
                            "[Image OCR] " + path.getFileName(), text));
                }
            }
        }
        return parsed(path, documentId, section, chunks, tables, images, ocr,
                "Apache POI DOCX");
    }

    private static PdfCourseIndexBuilder.ParsedDocument parsePptx(
            Path path,
            String documentId) throws IOException {
        List<PdfCourseIndexBuilder.RawChunk> chunks = new ArrayList<>();
        int tables = 0;
        int images = 0;
        boolean ocr = false;
        try (InputStream input = Files.newInputStream(path);
             XMLSlideShow slideShow = new XMLSlideShow(input)) {
            int slideNumber = 0;
            for (XSLFSlide slide : slideShow.getSlides()) {
                slideNumber++;
                StringBuilder text = new StringBuilder();
                String title = "Slide " + slideNumber;
                for (XSLFShape shape : slide.getShapes()) {
                    if (shape instanceof XSLFTextShape textShape) {
                        String value = textShape.getText().strip();
                        if (!value.isEmpty()) {
                            if (text.length() == 0) {
                                title = firstLine(value);
                            }
                            text.append(value).append('\n');
                        }
                    } else if (shape instanceof XSLFTable table) {
                        tables++;
                        text.append("[Table]\n").append(pptxTable(table)).append('\n');
                    } else if (shape instanceof XSLFPictureShape picture) {
                        images++;
                        String recognized = recognize(picture.getPictureData().getData());
                        if (!recognized.isBlank()) {
                            ocr = true;
                            text.append("[Image OCR]\n").append(recognized).append('\n');
                        }
                    }
                }
                addSplitChunks(chunks, documentId, path, "SLIDE", slideNumber, title,
                        text.toString());
            }
        }
        return parsed(path, documentId, chunks.size(), chunks, tables, images, ocr,
                "Apache POI PPTX");
    }

    private static PdfCourseIndexBuilder.ParsedDocument parseXlsx(
            Path path,
            String documentId) throws IOException {
        List<PdfCourseIndexBuilder.RawChunk> chunks = new ArrayList<>();
        int tables = 0;
        int images = 0;
        boolean ocr = false;
        try (InputStream input = Files.newInputStream(path);
             XSSFWorkbook workbook = new XSSFWorkbook(input)) {
            DataFormatter formatter = new DataFormatter();
            int sheetNumber = 0;
            for (Sheet sheetBase : workbook) {
                XSSFSheet sheet = (XSSFSheet) sheetBase;
                sheetNumber++;
                String text = workbookSheet(sheet, formatter);
                if (!text.isBlank()) {
                    tables++;
                    addSplitChunks(chunks, documentId, path, "SHEET", sheetNumber,
                            sheet.getSheetName(), text);
                }
                if (sheet.getDrawingPatriarch() != null) {
                    for (var shape : sheet.getDrawingPatriarch().getShapes()) {
                        if (shape instanceof XSSFPicture picture) {
                            images++;
                            String recognized = recognize(picture.getPictureData().getData());
                            if (!recognized.isBlank()) {
                                ocr = true;
                                chunks.add(chunk(documentId, path, "SHEET", sheetNumber,
                                        chunks.size(), "[Image OCR] " + sheet.getSheetName(),
                                        recognized));
                            }
                        }
                    }
                }
            }
        }
        return parsed(path, documentId, chunks.size(), chunks, tables, images, ocr,
                "Apache POI XLSX");
    }

    private static PdfCourseIndexBuilder.ParsedDocument parseLegacyDoc(
            Path path,
            String documentId) throws IOException {
        try (InputStream input = Files.newInputStream(path);
             HWPFDocument document = new HWPFDocument(input);
             WordExtractor extractor = new WordExtractor(document)) {
            return textDocument(path, documentId, extractor.getText(), "Apache POI DOC");
        }
    }

    private static PdfCourseIndexBuilder.ParsedDocument parseLegacyPpt(
            Path path,
            String documentId) throws IOException {
        try (InputStream input = Files.newInputStream(path);
             HSLFSlideShow slideShow = new HSLFSlideShow(input);
             SlideShowExtractor<?, ?> extractor = new SlideShowExtractor<>(slideShow)) {
            return textDocument(path, documentId, extractor.getText(),
                    "Apache POI PPT");
        }
    }

    private static PdfCourseIndexBuilder.ParsedDocument parseLegacyXls(
            Path path,
            String documentId) throws IOException {
        try (InputStream input = Files.newInputStream(path);
             HSSFWorkbook workbook = new HSSFWorkbook(input)) {
            List<PdfCourseIndexBuilder.RawChunk> chunks = new ArrayList<>();
            DataFormatter formatter = new DataFormatter();
            int tables = 0;
            for (Sheet sheet : workbook) {
                String text = poiSheet(sheet, formatter);
                if (!text.isBlank()) {
                    tables++;
                    addSplitChunks(chunks, documentId, path, "SHEET",
                            sheet.getWorkbook().getSheetIndex(sheet) + 1,
                            sheet.getSheetName(), text);
                }
            }
            return parsed(path, documentId, chunks.size(), chunks, tables, 0, false,
                    "Apache POI XLS");
        }
    }

    private static PdfCourseIndexBuilder.ParsedDocument parseHtml(
            Path path,
            String documentId) throws IOException {
        org.jsoup.nodes.Document html = Jsoup.parse(Files.readString(
                path, StandardCharsets.UTF_8));
        List<PdfCourseIndexBuilder.RawChunk> chunks = new ArrayList<>();
        int section = 0;
        for (Element element : html.body().children()) {
            String text = element.text().strip();
            if (text.isEmpty()) {
                continue;
            }
            section++;
            chunks.add(chunk(documentId, path, "SECTION", section, 0,
                    firstLine(text), text));
        }
        int tables = html.select("table").size();
        return parsed(path, documentId, section, chunks, tables,
                html.select("img").size(), false, "JSoup HTML");
    }

    private static PdfCourseIndexBuilder.ParsedDocument parseCsv(
            Path path,
            String documentId) throws IOException {
        String content = Files.readString(path, StandardCharsets.UTF_8);
        String normalized = content.replace('\uFEFF', ' ');
        List<PdfCourseIndexBuilder.RawChunk> chunks = new ArrayList<>();
        addSplitChunks(chunks, documentId, path, "SECTION", 1,
                path.getFileName().toString(), normalized);
        return parsed(path, documentId, 1, chunks, 1, 0, false,
                "UTF-8 CSV");
    }

    private static PdfCourseIndexBuilder.ParsedDocument parseImage(
            Path path,
            String documentId) throws IOException {
        BufferedImage image = ImageIO.read(path.toFile());
        if (image == null) {
            throw new IOException("Unsupported image format: " + path.getFileName());
        }
        String text = OcrEngine.recognize(image);
        if (!OcrEngine.isLikelyUsefulText(text)) {
            throw new IOException("OCR found no useful text in " + path.getFileName());
        }
        PdfCourseIndexBuilder.RawChunk chunk = chunk(
                documentId, path, "IMAGE", 1, 0, path.getFileName().toString(), text);
        return new PdfCourseIndexBuilder.ParsedDocument(
                new DocumentMeta(documentId, stripExtension(path.getFileName().toString()),
                        path.getFileName().toString(), 1),
                List.of(chunk), 0, 1, true, "Tess4J OCR");
    }

    private static PdfCourseIndexBuilder.ParsedDocument parsePlainText(
            Path path,
            String documentId) throws IOException {
        return textDocument(path, documentId,
                Files.readString(path, StandardCharsets.UTF_8), "UTF-8 text");
    }

    private static PdfCourseIndexBuilder.ParsedDocument textDocument(
            Path path,
            String documentId,
            String content,
            String parser) throws IOException {
        List<PdfCourseIndexBuilder.RawChunk> chunks = new ArrayList<>();
        String[] sections = content.replace("\r\n", "\n").replace('\r', '\n')
                .split("\\n\\s*\\n");
        int section = 0;
        for (String value : sections) {
            String text = value.strip();
            if (text.isEmpty()) {
                continue;
            }
            section++;
            addSplitChunks(chunks, documentId, path, "SECTION", section,
                    firstLine(text), text);
        }
        return parsed(path, documentId, section, chunks, 0, 0, false, parser);
    }

    private static PdfCourseIndexBuilder.ParsedDocument parsed(
            Path path,
            String documentId,
            int locations,
            List<PdfCourseIndexBuilder.RawChunk> chunks,
            int tables,
            int images,
            boolean ocr,
            String parser) throws IOException {
        if (chunks.isEmpty()) {
            throw new IOException("No extractable course content found in " + path.getFileName());
        }
        DocumentMeta document = new DocumentMeta(
                documentId,
                stripExtension(path.getFileName().toString()),
                path.getFileName().toString(),
                Math.max(1, locations));
        return new PdfCourseIndexBuilder.ParsedDocument(
                document, chunks, tables, images, ocr, parser);
    }

    private static void addSplitChunks(
            List<PdfCourseIndexBuilder.RawChunk> chunks,
            String documentId,
            Path path,
            String locatorType,
            int location,
            String title,
            String text) {
        List<String> pieces = PdfCourseIndexBuilder.splitPage(text);
        for (int i = 0; i < pieces.size(); i++) {
            chunks.add(chunk(documentId, path, locatorType, location, i, title, pieces.get(i)));
        }
    }

    private static PdfCourseIndexBuilder.RawChunk chunk(
            String documentId,
            Path path,
            String locatorType,
            int location,
            int index,
            String title,
            String text) {
        return new PdfCourseIndexBuilder.RawChunk(
                documentId,
                stripExtension(path.getFileName().toString()),
                path.getFileName().toString(),
                locatorType,
                location,
                index,
                title,
                text);
    }

    private static String docxTable(XWPFTable table) {
        StringBuilder result = new StringBuilder();
        for (XWPFTableRow row : table.getRows()) {
            List<String> cells = row.getTableCells().stream()
                    .map(XWPFTableCell::getText)
                    .map(String::strip)
                    .toList();
            result.append(String.join("\t", cells)).append('\n');
        }
        return result.toString();
    }

    private static String pptxTable(XSLFTable table) {
        StringBuilder result = new StringBuilder();
        for (XSLFTableRow row : table.getRows()) {
            List<String> cells = row.getCells().stream()
                    .map(XSLFTableCell::getText)
                    .map(String::strip)
                    .toList();
            result.append(String.join("\t", cells)).append('\n');
        }
        return result.toString();
    }

    private static String workbookSheet(XSSFSheet sheet, DataFormatter formatter) {
        return poiSheet(sheet, formatter);
    }

    private static String poiSheet(Sheet sheet, DataFormatter formatter) {
        StringBuilder result = new StringBuilder();
        for (Row row : sheet) {
            List<String> values = new ArrayList<>();
            row.forEach(cell -> values.add(formatter.formatCellValue(cell).strip()));
            if (values.stream().anyMatch(value -> !value.isEmpty())) {
                result.append(String.join("\t", values)).append('\n');
            }
        }
        return result.toString();
    }

    private static String recognize(byte[] imageBytes) {
        if (imageBytes == null || imageBytes.length == 0) {
            return "";
        }
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageBytes));
            if (image == null || image.getWidth() < 64 || image.getHeight() < 32) {
                return "";
            }
            return OcrEngine.recognize(image);
        } catch (Exception | LinkageError error) {
            return "";
        }
    }

    private static String extension(Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static String firstLine(String text) {
        for (String line : text.split("\\R")) {
            String value = line.strip();
            if (!value.isEmpty()) {
                return value.substring(0, Math.min(160, value.length()));
            }
        }
        return "";
    }
}
