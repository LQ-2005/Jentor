package com.tongji.cst3492.jentor.context;

import com.tongji.cst3492.jentor.model.CourseIndex;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextBox;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CourseDocumentParserTest {
    @TempDir
    Path temporary;

    @Test
    void parsesDocxTextAndRealTableStructure() throws Exception {
        Path path = temporary.resolve("Course-Notes.docx");
        try (XWPFDocument document = new XWPFDocument();
             var output = Files.newOutputStream(path)) {
            document.createParagraph().createRun()
                    .setText("Checked exceptions must be handled.");
            XWPFTable table = document.createTable(2, 2);
            table.getRow(0).getCell(0).setText("Exception");
            table.getRow(0).getCell(1).setText("Checked");
            table.getRow(1).getCell(0).setText("IOException");
            table.getRow(1).getCell(1).setText("Yes");
            document.write(output);
        }

        var parsed = CourseDocumentParser.parse(path, "docx-test");

        assertEquals(1, parsed.tableCount());
        assertTrue(parsed.chunks().stream().anyMatch(chunk ->
                chunk.text().contains("IOException\tYes")));
        assertTrue(parsed.chunks().stream().allMatch(chunk ->
                "SECTION".equals(chunk.locatorType())));
    }

    @Test
    void parsesPptxSlidesAndXlsxSheets() throws Exception {
        Path pptx = temporary.resolve("Lecture.pptx");
        try (XMLSlideShow slides = new XMLSlideShow();
             var output = Files.newOutputStream(pptx)) {
            XSLFSlide slide = slides.createSlide();
            XSLFTextBox text = slide.createTextBox();
            text.setText("Spring Security Filters");
            slides.write(output);
        }
        Path xlsx = temporary.resolve("Grades.xlsx");
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             var output = Files.newOutputStream(xlsx)) {
            var sheet = workbook.createSheet("Scores");
            var row = sheet.createRow(0);
            row.createCell(0).setCellValue("Student");
            row.createCell(1).setCellValue("Score");
            workbook.write(output);
        }

        var slide = CourseDocumentParser.parse(pptx, "pptx-test");
        var sheet = CourseDocumentParser.parse(xlsx, "xlsx-test");

        assertTrue(slide.chunks().stream().anyMatch(chunk ->
                "SLIDE".equals(chunk.locatorType())
                        && chunk.text().contains("Spring Security")));
        assertEquals(1, sheet.tableCount());
        assertTrue(sheet.chunks().stream().anyMatch(chunk ->
                "SHEET".equals(chunk.locatorType())
                        && chunk.text().contains("Student\tScore")));
    }

    @Test
    void parsesHtmlTablesAndPlainText() throws Exception {
        Path html = temporary.resolve("lesson.html");
        Files.writeString(html, "<h1>Arrays</h1><table><tr><td>Type</td>"
                + "<td>Value</td></tr><tr><td>int</td><td>1</td></tr></table>",
                StandardCharsets.UTF_8);
        Path text = temporary.resolve("notes.md");
        Files.writeString(text, "# Exceptions\nChecked exceptions require handling.",
                StandardCharsets.UTF_8);

        var parsedHtml = CourseDocumentParser.parse(html, "html-test");
        var parsedText = CourseDocumentParser.parse(text, "text-test");

        assertEquals(1, parsedHtml.tableCount());
        assertTrue(parsedText.chunks().getFirst().text().contains("Checked exceptions"));
    }

    @Test
    void recognizesTextFromAnImageWithBundledOfflineOcr() throws Exception {
        Path imagePath = createTextImage("architecture.png", "SPRING SECURITY", "FILTER CHAIN");
        var parsed = CourseDocumentParser.parse(imagePath, "image-test");
        CourseIndex index = PdfCourseIndexBuilder.aggregate(List.of(parsed));

        assertTrue(parsed.ocrUsed());
        assertEquals(1, parsed.imageCount());
        assertTrue(index.getChunks().stream().anyMatch(chunk ->
                chunk.getText().toUpperCase().contains("SPRING")));
    }

    @Test
    void recognizesEmbeddedImagesInPdfPages() throws Exception {
        Path imagePath = createTextImage("diagram.png", "ENTERPRISE JAVA", "SERVICE LAYER");
        Path pdf = temporary.resolve("scanned-slide.pdf");
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            PDImageXObject image = PDImageXObject.createFromFileByContent(
                    imagePath.toFile(), document);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.drawImage(image, 40, 300, 500, 160);
            }
            document.save(pdf.toFile());
        }

        var parsed = CourseDocumentParser.parse(pdf, "pdf-image-test");

        assertTrue(parsed.ocrUsed());
        assertTrue(parsed.imageCount() > 0);
        assertTrue(parsed.chunks().stream().anyMatch(chunk ->
                chunk.text().toUpperCase().contains("JAVA")));
    }

    @Test
    void detectsTableLikePdfLayout() throws Exception {
        Path pdf = temporary.resolve("table-slide.pdf");
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
                String[][] rows = {
                        {"Exception", "Checked"},
                        {"IOException", "Yes"},
                        {"RuntimeException", "No"}
                };
                int y = 740;
                for (String[] row : rows) {
                    content.beginText();
                    content.setFont(font, 12);
                    content.newLineAtOffset(70, y);
                    content.showText(row[0]);
                    content.endText();
                    content.beginText();
                    content.setFont(font, 12);
                    content.newLineAtOffset(350, y);
                    content.showText(row[1]);
                    content.endText();
                    y -= 20;
                }
            }
            document.save(pdf.toFile());
        }

        var parsed = CourseDocumentParser.parse(pdf, "pdf-table-test");

        assertTrue(parsed.tableCount() > 0,
                () -> parsed.chunks().stream().map(chunk -> chunk.text())
                        .reduce("", (left, right) -> left + "\n---\n" + right));
        assertTrue(parsed.chunks().stream().anyMatch(chunk ->
                chunk.text().contains("[TABLE]")));
    }

    private Path createTextImage(String fileName, String first, String second) throws Exception {
        Path imagePath = temporary.resolve(fileName);
        BufferedImage image = new BufferedImage(900, 240, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
            graphics.setColor(Color.BLACK);
            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 64));
            graphics.drawString(first, 40, 120);
            graphics.drawString(second, 40, 200);
        } finally {
            graphics.dispose();
        }
        ImageIO.write(image, "png", imagePath.toFile());
        return imagePath;
    }
}
