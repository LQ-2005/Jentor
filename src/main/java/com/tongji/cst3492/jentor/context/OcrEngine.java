package com.tongji.cst3492.jentor.context;

import net.sourceforge.tess4j.Tesseract;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Offline Tesseract OCR with bundled English and Simplified Chinese data. */
public final class OcrEngine {
    private static final List<String> DATA_FILES =
            List.of("eng.traineddata", "chi_sim.traineddata", "osd.traineddata");
    private static volatile Path storageRoot =
            Path.of(System.getProperty("java.io.tmpdir"), "jentor-ocr");
    private static volatile Path dataDirectory;

    private OcrEngine() {
    }

    public static String recognize(BufferedImage image) throws IOException {
        if (image == null || image.getWidth() < 8 || image.getHeight() < 8) {
            return "";
        }
        try {
            Tesseract tesseract = new Tesseract();
            tesseract.setDatapath(dataDirectory().toString());
            tesseract.setLanguage("chi_sim+eng");
            tesseract.setVariable("user_defined_dpi", "300");
            return tesseract.doOCR(image).strip();
        } catch (Exception | LinkageError error) {
            throw new IOException("Offline OCR failed: " + error.getMessage(), error);
        }
    }

    public static boolean isLikelyUsefulText(String text) {
        if (text == null) {
            return false;
        }
        long lettersOrDigits = text.codePoints()
                .filter(codePoint -> Character.isLetterOrDigit(codePoint))
                .count();
        return lettersOrDigits >= 8;
    }

    public static void configureStorageRoot(Path root) {
        if (root != null) {
            storageRoot = root;
            dataDirectory = null;
        }
    }

    private static Path dataDirectory() throws IOException {
        Path current = dataDirectory;
        if (current != null) {
            return current;
        }
        synchronized (OcrEngine.class) {
            current = dataDirectory;
            if (current != null) {
                return current;
            }
            current = storageRoot.resolve("tessdata");
            Files.createDirectories(current);
            for (String fileName : DATA_FILES) {
                Path target = current.resolve(fileName);
                if (Files.isRegularFile(target) && Files.size(target) > 0) {
                    continue;
                }
                try (InputStream input = OcrEngine.class.getResourceAsStream(
                        "/tessdata/" + fileName)) {
                    if (input == null) {
                        throw new IOException("Bundled OCR data is missing: " + fileName);
                    }
                    Files.copy(input, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            }
            dataDirectory = current;
            return current;
        }
    }
}
