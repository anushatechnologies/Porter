package com.anushaporter.backend.service.document;

import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.RescaleOp;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

@Service
public class OcrExtractionService {

    private static final Logger log = LoggerFactory.getLogger(OcrExtractionService.class);

    private ITesseract tesseract;
    private boolean tesseractInitialized = false;

    public OcrExtractionService() {
        initTesseract();
    }

    private synchronized void initTesseract() {
        try {
            tesseract = new Tesseract();
            tesseract.setLanguage("eng");
            
            // 1. Check system property or env var
            String tessDataDir = System.getProperty("TESSDATA_PREFIX");
            if (tessDataDir == null || tessDataDir.isBlank()) {
                tessDataDir = System.getenv("TESSDATA_PREFIX");
            }

            // 2. Check local directories
            if (tessDataDir == null || !new File(tessDataDir).exists()) {
                Path localTessData = Paths.get("tessdata");
                if (Files.exists(localTessData)) {
                    tessDataDir = localTessData.toAbsolutePath().toString();
                } else {
                    // Create temp tessdata directory
                    Path tempDir = Paths.get(System.getProperty("java.io.tmpdir"), "porter_tessdata");
                    Files.createDirectories(tempDir);
                    
                    // Copy sample or english traineddata if available from classpath
                    try (InputStream is = getClass().getResourceAsStream("/tessdata/eng.traineddata")) {
                        if (is != null) {
                            Path targetFile = tempDir.resolve("eng.traineddata");
                            Files.copy(is, targetFile, StandardCopyOption.REPLACE_EXISTING);
                        }
                    } catch (Exception ignored) {}

                    tessDataDir = tempDir.toAbsolutePath().toString();
                }
            }

            if (tessDataDir != null) {
                tesseract.setDatapath(tessDataDir);
            }

            // Page segmentation mode 1 = Automatic page segmentation with OSD, or 3 = Fully automatic
            tesseract.setPageSegMode(3);
            tesseract.setOcrEngineMode(1); // LSTM engine

            tesseractInitialized = true;
            log.info("✓ Tess4J OCR Engine initialized with datapath: {}", tessDataDir);
        } catch (Throwable t) {
            log.warn("Tess4J initialization notice (Native libraries or tessdata will use fallback where needed): {}", t.getMessage());
            tesseractInitialized = false;
        }
    }

    /**
     * Extracts text from raw image bytes.
     *
     * @param imageBytes image file bytes
     * @return Extracted OCR text (normalized uppercase) or empty string
     */
    public String extractText(byte[] imageBytes) {
        if (imageBytes == null || imageBytes.length == 0) {
            return "";
        }

        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageBytes));
            if (image == null) {
                return "";
            }
            return extractText(image);
        } catch (Exception e) {
            log.warn("Failed to decode image for OCR: {}", e.getMessage());
            return "";
        }
    }

    /**
     * Extracts OCR text from a BufferedImage with preprocessing.
     */
    public String extractText(BufferedImage image) {
        if (image == null) {
            return "";
        }

        if (tesseract == null || !tesseractInitialized) {
            initTesseract();
        }

        if (tesseract != null && tesseractInitialized) {
            // Pass 1: Try with enhanced preprocessed image
            try {
                BufferedImage processedImage = preprocessForOcr(image);
                String ocrResult = tesseract.doOCR(processedImage);
                if (ocrResult != null && !ocrResult.isBlank()) {
                    String norm = normalizeText(ocrResult);
                    if (isTextReadable(norm)) {
                        return norm;
                    }
                }
            } catch (Throwable t) {
                log.debug("Pass 1 OCR attempt note: {}", t.getMessage());
            }

            // Pass 2: Try directly on raw original image
            try {
                String rawResult = tesseract.doOCR(image);
                if (rawResult != null && !rawResult.isBlank()) {
                    String norm = normalizeText(rawResult);
                    if (isTextReadable(norm)) {
                        return norm;
                    }
                }
            } catch (Throwable t) {
                log.debug("Pass 2 OCR attempt note: {}", t.getMessage());
            }

            // Pass 3: Try with rotated image if text is still too short
            try {
                BufferedImage rotated = rotateImage(image, 90);
                String rotResult = tesseract.doOCR(rotated);
                if (rotResult != null && !rotResult.isBlank()) {
                    String norm = normalizeText(rotResult);
                    if (isTextReadable(norm)) {
                        return norm;
                    }
                }
            } catch (Throwable ignored) {}
        }

        return "";
    }

    private BufferedImage rotateImage(BufferedImage img, double angleDegrees) {
        int w = img.getWidth();
        int h = img.getHeight();
        BufferedImage rotated = new BufferedImage(h, w, img.getType() == 0 ? BufferedImage.TYPE_INT_RGB : img.getType());
        Graphics2D g2d = rotated.createGraphics();
        g2d.translate((h - w) / 2.0, (h - w) / 2.0);
        g2d.rotate(Math.toRadians(angleDegrees), w / 2.0, w / 2.0);
        g2d.drawImage(img, 0, 0, null);
        g2d.dispose();
        return rotated;
    }

    /**
     * Preprocesses image for improved OCR accuracy:
     * - Upscales if image resolution is low
     * - Converts to grayscale
     * - Enhances contrast
     */
    public BufferedImage preprocessForOcr(BufferedImage original) {
        if (original == null) return null;

        int width = original.getWidth();
        int height = original.getHeight();

        double scale = 1.0;
        if (width < 900) {
            scale = 900.0 / width;
        }

        int targetWidth = (int) (width * scale);
        int targetHeight = (int) (height * scale);

        BufferedImage grayImage = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g2d = grayImage.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.drawImage(original, 0, 0, targetWidth, targetHeight, null);
        g2d.dispose();

        try {
            RescaleOp rescale = new RescaleOp(1.15f, -5.0f, null);
            BufferedImage contrastImage = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_BYTE_GRAY);
            rescale.filter(grayImage, contrastImage);
            return contrastImage;
        } catch (Exception e) {
            return grayImage;
        }
    }

    /**
     * Cleans and normalizes OCR text for robust regex and keyword matching.
     */
    public String normalizeText(String text) {
        if (text == null) return "";
        return text
                .replace("\r", " ")
                .replaceAll("[\\t\\v\\f]+", " ")
                .replaceAll(" +", " ")
                .toUpperCase(Locale.ROOT)
                .trim();
    }

    /**
     * Checks if OCR extracted meaningful readable text.
     */
    public boolean isTextReadable(String text) {
        if (text == null || text.trim().isEmpty()) {
            return false;
        }
        // Count alphanumeric characters - threshold relaxed to 4 characters for mobile compressed photos
        long alnumCount = text.chars().filter(Character::isLetterOrDigit).count();
        return alnumCount >= 4;
    }
}
