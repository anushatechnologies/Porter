package com.anushaporter.backend.service.document;

import com.anushaporter.backend.dto.ValidationReason;
import com.anushaporter.backend.dto.ValidationResult;
import com.anushaporter.backend.model.DocumentType;
import nu.pattern.OpenCV;
import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class FaceDetectionService {

    private static final Logger log = LoggerFactory.getLogger(FaceDetectionService.class);

    private static boolean openCvLoaded = false;
    private CascadeClassifier faceCascade;
    private boolean cascadeLoaded = false;

    // Quality thresholds
    public static final double MIN_BRIGHTNESS = 40.0;
    public static final double MAX_BRIGHTNESS = 245.0;
    public static final double MIN_LAPLACIAN_BLUR_SCORE = 45.0;
    public static final double MIN_FACE_AREA_RATIO = 0.07; // at least ~7-10% of frame

    @PostConstruct
    public void init() {
        try {
            OpenCV.loadLocally();
            openCvLoaded = true;
            log.info("✓ OpenCV native library loaded successfully.");

            // Load Haar Cascade XML
            Path tempCascade = Files.createTempFile("haarcascade_frontalface_", ".xml");
            tempCascade.toFile().deleteOnExit();

            InputStream is = getClass().getResourceAsStream("/haarcascades/haarcascade_frontalface_default.xml");
            if (is == null) {
                // Try alternate built-in path
                is = getClass().getClassLoader().getResourceAsStream("haarcascades/haarcascade_frontalface_default.xml");
            }

            if (is != null) {
                Files.copy(is, tempCascade, StandardCopyOption.REPLACE_EXISTING);
                faceCascade = new CascadeClassifier(tempCascade.toAbsolutePath().toString());
                if (!faceCascade.empty()) {
                    cascadeLoaded = true;
                    log.info("✓ OpenCV Face Cascade loaded successfully.");
                }
            } else {
                log.warn("Face cascade XML not found in resources, attempting default load.");
            }
        } catch (Throwable t) {
            log.warn("OpenCV native initialization notice: {}. Fallback image analysis will be used.", t.getMessage());
            openCvLoaded = false;
        }
    }

    /**
     * Comprehensive face validation checking:
     * 1. Darkness / brightness
     * 2. Blur / sharpness (Laplacian variance)
     * 3. Face count (must be exactly 1)
     * 4. Face frame area percentage
     */
    public ValidationResult validateFace(byte[] imageBytes) {
        if (imageBytes == null || imageBytes.length == 0) {
            return ValidationResult.reject(DocumentType.FACE, ValidationReason.INVALID_FILE, "No image file provided.");
        }

        try {
            BufferedImage bufferedImage = ImageIO.read(new ByteArrayInputStream(imageBytes));
            if (bufferedImage == null) {
                return ValidationResult.reject(DocumentType.FACE, ValidationReason.INVALID_FILE, "Unsupported or corrupted image format.");
            }

            // 1. Check Brightness / Darkness
            double brightness = calculateBrightness(bufferedImage);
            if (brightness < MIN_BRIGHTNESS) {
                ValidationResult res = ValidationResult.reject(DocumentType.FACE, ValidationReason.IMAGE_TOO_DARK, 
                        "Photo is too dark (brightness " + Math.round(brightness) + "/255). Please ensure good lighting and try again.");
                res.addData("brightness", Math.round(brightness));
                return res;
            }
            if (brightness > MAX_BRIGHTNESS) {
                ValidationResult res = ValidationResult.reject(DocumentType.FACE, ValidationReason.IMAGE_TOO_BRIGHT, 
                        "Photo is overexposed or too bright. Please adjust lighting and try again.");
                res.addData("brightness", Math.round(brightness));
                return res;
            }

            // 2. Check Blur / Sharpness (Laplacian variance)
            double blurScore = calculateBlurScore(bufferedImage);
            if (blurScore < MIN_LAPLACIAN_BLUR_SCORE) {
                ValidationResult res = ValidationResult.reject(DocumentType.FACE, ValidationReason.IMAGE_TOO_BLURRY, 
                        "Photo is too blurry (sharpness score " + Math.round(blurScore) + "). Please hold the camera steady and try again.");
                res.addData("blurScore", Math.round(blurScore));
                res.addData("brightness", Math.round(brightness));
                return res;
            }

            // 3. Face Detection
            FaceDetectionResult faceResult = detectFaces(bufferedImage, imageBytes);

            if (faceResult.faceCount == 0) {
                ValidationResult res = ValidationResult.reject(DocumentType.FACE, ValidationReason.NO_FACE_DETECTED, 
                        "No face detected in the photo. Please position your face clearly in the camera frame.");
                res.addData("brightness", Math.round(brightness));
                res.addData("blurScore", Math.round(blurScore));
                return res;
            }

            if (faceResult.faceCount > 1) {
                ValidationResult res = ValidationResult.reject(DocumentType.FACE, ValidationReason.MULTIPLE_FACES_DETECTED, 
                        "Multiple faces (" + faceResult.faceCount + ") detected. Please ensure only you are visible in the photo.");
                res.addData("faceCount", faceResult.faceCount);
                res.addData("brightness", Math.round(brightness));
                res.addData("blurScore", Math.round(blurScore));
                return res;
            }

            // 4. Face Area Ratio
            if (faceResult.maxFaceAreaRatio < MIN_FACE_AREA_RATIO) {
                ValidationResult res = ValidationResult.reject(DocumentType.FACE, ValidationReason.FACE_TOO_FAR, 
                        "Face is too small or far from the camera (" + Math.round(faceResult.maxFaceAreaRatio * 100) + "% of frame). Please move closer to the camera.");
                res.addData("faceAreaPercentage", Math.round(faceResult.maxFaceAreaRatio * 100) + "%");
                res.addData("brightness", Math.round(brightness));
                res.addData("blurScore", Math.round(blurScore));
                return res;
            }

            // All face quality checks passed!
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("faceCount", 1);
            data.put("faceAreaPercentage", Math.round(faceResult.maxFaceAreaRatio * 100) + "%");
            data.put("brightness", Math.round(brightness));
            data.put("blurScore", Math.round(blurScore));
            data.put("qualityStatus", "EXCELLENT");

            return ValidationResult.success(DocumentType.FACE, "Face photo validated successfully.", data, 0.98);

        } catch (Exception e) {
            log.error("Face validation error: {}", e.getMessage(), e);
            return ValidationResult.error(DocumentType.FACE, 500, "Internal Server Error", "PROCESSING_ERROR", "Failed to process face photo: " + e.getMessage());
        }
    }

    /**
     * Calculates the average brightness of the image (0 to 255).
     */
    public double calculateBrightness(BufferedImage image) {
        long totalLuminance = 0;
        int width = image.getWidth();
        int height = image.getHeight();
        int sampleStep = Math.max(1, (width * height) / 10000); // sample ~10,000 pixels for fast execution

        int count = 0;
        for (int y = 0; y < height; y += (sampleStep > 1 ? 2 : 1)) {
            for (int x = 0; x < width; x += (sampleStep > 1 ? 2 : 1)) {
                int rgb = image.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                // Standard ITU-R BT.601 luminance formula
                int luma = (int) (0.299 * r + 0.587 * g + 0.114 * b);
                totalLuminance += luma;
                count++;
            }
        }
        return count > 0 ? (double) totalLuminance / count : 128.0;
    }

    /**
     * Calculates blur score using Laplacian operator variance (sharpness score).
     */
    public double calculateBlurScore(BufferedImage image) {
        if (openCvLoaded) {
            try {
                byte[] bytes = bufferedImageToByteArray(image);
                Mat mat = Imgcodecs.imdecode(new MatOfByte(bytes), Imgcodecs.IMREAD_GRAYSCALE);
                if (!mat.empty()) {
                    Mat laplacian = new Mat();
                    Imgproc.Laplacian(mat, laplacian, CvType.CV_64F);
                    MatOfDouble mean = new MatOfDouble();
                    MatOfDouble stddev = new MatOfDouble();
                    Core.meanStdDev(laplacian, mean, stddev);
                    double variance = Math.pow(stddev.get(0, 0)[0], 2);
                    return variance;
                }
            } catch (Exception e) {
                log.debug("OpenCV Laplacian blur calc fallback: {}", e.getMessage());
            }
        }

        // Pure Java Laplacian Kernel approximation
        int width = image.getWidth();
        int height = image.getHeight();
        int[][] gray = new int[width][height];

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = image.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                gray[x][y] = (int) (0.299 * r + 0.587 * g + 0.114 * b);
            }
        }

        double sum = 0;
        double sumSq = 0;
        int count = 0;

        for (int y = 1; y < height - 1; y += 2) {
            for (int x = 1; x < width - 1; x += 2) {
                int lapVal = 4 * gray[x][y] - gray[x - 1][y] - gray[x + 1][y] - gray[x][y - 1] - gray[x][y + 1];
                sum += lapVal;
                sumSq += lapVal * lapVal;
                count++;
            }
        }

        if (count == 0) return 100.0;
        double mean = sum / count;
        double variance = (sumSq / count) - (mean * mean);
        return Math.max(0.0, variance);
    }

    /**
     * Detects faces using OpenCV Haar Cascade or skin-tone facial clustering fallback.
     */
    public FaceDetectionResult detectFaces(BufferedImage image, byte[] imageBytes) {
        int width = image.getWidth();
        int height = image.getHeight();
        double totalImageArea = (double) width * height;

        if (openCvLoaded && faceCascade != null && !faceCascade.empty()) {
            try {
                Mat mat = Imgcodecs.imdecode(new MatOfByte(imageBytes), Imgcodecs.IMREAD_COLOR);
                if (!mat.empty()) {
                    Mat gray = new Mat();
                    Imgproc.cvtColor(mat, gray, Imgproc.COLOR_BGR2GRAY);
                    Imgproc.equalizeHist(gray, gray);

                    MatOfRect faceDetections = new MatOfRect();
                    faceCascade.detectMultiScale(
                            gray,
                            faceDetections,
                            1.1,
                            4,
                            0,
                            new Size(width * 0.15, height * 0.15),
                            new Size(width * 0.95, height * 0.95)
                    );

                    Rect[] rects = faceDetections.toArray();
                    int faceCount = rects.length;

                    double maxAreaRatio = 0.0;
                    for (Rect r : rects) {
                        double faceArea = (double) r.width * r.height;
                        double ratio = faceArea / totalImageArea;
                        if (ratio > maxAreaRatio) {
                            maxAreaRatio = ratio;
                        }
                    }

                    log.info("✓ OpenCV detected {} face(s), max face area ratio: {}", faceCount, maxAreaRatio);
                    return new FaceDetectionResult(faceCount, maxAreaRatio);
                }
            } catch (Exception e) {
                log.warn("OpenCV face detection error, utilizing fallback: {}", e.getMessage());
            }
        }

        // Fallback: analyze portrait skin-tone density and facial center distribution
        return detectFaceFallback(image);
    }

    private FaceDetectionResult detectFaceFallback(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        int skinPixels = 0;
        int centerSkinPixels = 0;
        int totalSampled = 0;

        int minX = (int) (width * 0.2);
        int maxX = (int) (width * 0.8);
        int minY = (int) (height * 0.15);
        int maxY = (int) (height * 0.85);

        for (int y = 0; y < height; y += 4) {
            for (int x = 0; x < width; x += 4) {
                totalSampled++;
                int rgb = image.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;

                if (isSkinColor(r, g, b)) {
                    skinPixels++;
                    if (x >= minX && x <= maxX && y >= minY && y <= maxY) {
                        centerSkinPixels++;
                    }
                }
            }
        }

        double skinRatio = (double) skinPixels / totalSampled;
        double centerRatio = (double) centerSkinPixels / Math.max(1, skinPixels);

        // If skin tone ratio is reasonable and centered in portrait style
        if (skinRatio >= 0.08 && centerRatio >= 0.55) {
            return new FaceDetectionResult(1, Math.min(0.35, skinRatio * 1.5));
        }

        return new FaceDetectionResult(0, 0.0);
    }

    private boolean isSkinColor(int r, int g, int b) {
        // Standard Peer et al. RGB skin color bounding rule
        return (r > 95 && g > 40 && b > 20) &&
                (Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b)) > 15) &&
                (Math.abs(r - g) > 15) &&
                (r > g) && (r > b);
    }

    private byte[] bufferedImageToByteArray(BufferedImage image) {
        try {
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            ImageIO.write(image, "jpg", baos);
            return baos.toByteArray();
        } catch (Exception e) {
            return new byte[0];
        }
    }

    public static class FaceDetectionResult {
        public final int faceCount;
        public final double maxFaceAreaRatio;

        public FaceDetectionResult(int faceCount, double maxFaceAreaRatio) {
            this.faceCount = faceCount;
            this.maxFaceAreaRatio = maxFaceAreaRatio;
        }
    }
}
