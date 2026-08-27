package com.anushaporter.backend.service.face;

import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;

@Service
public class FaceEmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(FaceEmbeddingService.class);
    private static final int GRID_CELLS = 8;       // 8x8 grid on 128x128 ROI = 16x16 per cell
    private static final int FEATURES_PER_CELL = 4; // LBP mean, LBP variance, Gradient magnitude, Dominant angle
    public static final int EMBEDDING_DIM = GRID_CELLS * GRID_CELLS * FEATURES_PER_CELL; // 256 dimensions

    /**
     * Extracts a high-dimensional normalized biometric facial embedding vector from a standardized 128x128 face Mat.
     */
    public float[] generateEmbedding(Mat faceMat) {
        if (faceMat == null || faceMat.empty()) {
            throw new IllegalArgumentException("Cannot generate embedding from empty face Mat.");
        }

        int width = faceMat.cols();
        int height = faceMat.rows();
        int cellW = width / GRID_CELLS;
        int cellH = height / GRID_CELLS;

        float[] embedding = new float[EMBEDDING_DIM];
        int featureIdx = 0;

        // Compute Sobel gradients for directional edge features
        Mat gradX = new Mat();
        Mat gradY = new Mat();
        Imgproc.Sobel(faceMat, gradX, CvType.CV_32F, 1, 0);
        Imgproc.Sobel(faceMat, gradY, CvType.CV_32F, 0, 1);

        byte[] pixelBuffer = new byte[width * height];
        faceMat.get(0, 0, pixelBuffer);

        float[] gxBuffer = new float[width * height];
        float[] gyBuffer = new float[width * height];
        gradX.get(0, 0, gxBuffer);
        gradY.get(0, 0, gyBuffer);

        for (int gy = 0; gy < GRID_CELLS; gy++) {
            for (int gx = 0; gx < GRID_CELLS; gx++) {
                int startX = gx * cellW;
                int startY = gy * cellH;

                double sumLbp = 0;
                double sumLbpSq = 0;
                double sumGradMag = 0;
                double sumAngle = 0;
                int count = 0;

                for (int y = startY + 1; y < startY + cellH - 1 && y < height - 1; y++) {
                    for (int x = startX + 1; x < startX + cellW - 1 && x < width - 1; x++) {
                        int centerVal = pixelBuffer[y * width + x] & 0xFF;

                        // 8-neighbor Local Binary Pattern (LBP) code
                        int lbp = 0;
                        if ((pixelBuffer[(y - 1) * width + (x - 1)] & 0xFF) >= centerVal) lbp |= 1;
                        if ((pixelBuffer[(y - 1) * width + x] & 0xFF) >= centerVal) lbp |= 2;
                        if ((pixelBuffer[(y - 1) * width + (x + 1)] & 0xFF) >= centerVal) lbp |= 4;
                        if ((pixelBuffer[y * width + (x + 1)] & 0xFF) >= centerVal) lbp |= 8;
                        if ((pixelBuffer[(y + 1) * width + (x + 1)] & 0xFF) >= centerVal) lbp |= 16;
                        if ((pixelBuffer[(y + 1) * width + x] & 0xFF) >= centerVal) lbp |= 32;
                        if ((pixelBuffer[(y + 1) * width + (x - 1)] & 0xFF) >= centerVal) lbp |= 64;
                        if ((pixelBuffer[y * width + (x - 1)] & 0xFF) >= centerVal) lbp |= 128;

                        sumLbp += lbp;
                        sumLbpSq += (lbp * lbp);

                        // Gradient magnitude and orientation
                        float dx = gxBuffer[y * width + x];
                        float dy = gyBuffer[y * width + x];
                        double mag = Math.sqrt(dx * dx + dy * dy);
                        double angle = Math.atan2(dy, dx) + Math.PI; // normalized [0, 2*PI]

                        sumGradMag += mag;
                        sumAngle += angle;
                        count++;
                    }
                }

                if (count > 0) {
                    double lbpMean = sumLbp / count;
                    double lbpVar = Math.sqrt(Math.max(0, (sumLbpSq / count) - (lbpMean * lbpMean)));
                    double gradMagMean = sumGradMag / count;
                    double angleMean = sumAngle / count;

                    embedding[featureIdx++] = (float) (lbpMean / 255.0);
                    embedding[featureIdx++] = (float) (lbpVar / 128.0);
                    embedding[featureIdx++] = (float) Math.min(1.0, gradMagMean / 100.0);
                    embedding[featureIdx++] = (float) (angleMean / (2 * Math.PI));
                } else {
                    embedding[featureIdx++] = 0f;
                    embedding[featureIdx++] = 0f;
                    embedding[featureIdx++] = 0f;
                    embedding[featureIdx++] = 0f;
                }
            }
        }

        // L2 Unit Normalization
        normalizeL2(embedding);
        return embedding;
    }

    /**
     * Normalizes vector to unit length ||v||_2 = 1.0.
     */
    public void normalizeL2(float[] vector) {
        double sumSq = 0;
        for (float val : vector) {
            sumSq += (val * val);
        }
        double norm = Math.sqrt(sumSq);
        if (norm > 1e-7) {
            for (int i = 0; i < vector.length; i++) {
                vector[i] = (float) (vector[i] / norm);
            }
        }
    }

    /**
     * Computes Cosine Similarity between two L2-normalized vectors.
     * Returns a score between -1.0 and 1.0 (typically 0.0 to 1.0 for image embeddings).
     */
    public double computeCosineSimilarity(float[] vecA, float[] vecB) {
        if (vecA == null || vecB == null || vecA.length != vecB.length) {
            return 0.0;
        }
        double dotProduct = 0.0;
        for (int i = 0; i < vecA.length; i++) {
            dotProduct += (vecA[i] * vecB[i]);
        }
        return Math.max(-1.0, Math.min(1.0, dotProduct));
    }

    /**
     * Computes Euclidean Distance between two vectors.
     */
    public double computeEuclideanDistance(float[] vecA, float[] vecB) {
        if (vecA == null || vecB == null || vecA.length != vecB.length) {
            return Double.MAX_VALUE;
        }
        double sumSq = 0.0;
        for (int i = 0; i < vecA.length; i++) {
            double diff = vecA[i] - vecB[i];
            sumSq += (diff * diff);
        }
        return Math.sqrt(sumSq);
    }

    /**
     * Calibrates similarity score into a confidence metric between 0.0 and 1.0.
     */
    public double calculateConfidenceScore(double cosineSimilarity) {
        // High similarity (>0.85) yields >90% confidence
        // Mid similarity (~0.75) yields ~75% confidence
        // Low similarity (<0.60) drops sharply
        if (cosineSimilarity >= 0.95) {
            return 0.98;
        } else if (cosineSimilarity >= 0.85) {
            return 0.85 + ((cosineSimilarity - 0.85) / 0.10) * 0.13;
        } else if (cosineSimilarity >= 0.70) {
            return 0.65 + ((cosineSimilarity - 0.70) / 0.15) * 0.20;
        } else {
            return Math.max(0.05, cosineSimilarity * 0.8);
        }
    }

    /**
     * Serializes float array into compact comma-separated string for DB storage.
     */
    public String serializeVector(float[] vector) {
        if (vector == null) return "";
        StringBuilder sb = new StringBuilder(vector.length * 8);
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(Float.toString(vector[i]));
        }
        return sb.toString();
    }

    /**
     * Deserializes comma-separated string back into float array.
     */
    public float[] deserializeVector(String serialized) {
        if (serialized == null || serialized.trim().isEmpty()) {
            return new float[0];
        }
        String[] tokens = serialized.split(",");
        float[] vec = new float[tokens.length];
        for (int i = 0; i < tokens.length; i++) {
            try {
                vec[i] = Float.parseFloat(tokens[i].trim());
            } catch (NumberFormatException e) {
                vec[i] = 0f;
            }
        }
        return vec;
    }

    /**
     * Computes SHA-256 integrity hash of vector to prevent database tampering.
     */
    public String computeFaceHash(float[] vector) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String data = Arrays.toString(vector);
            byte[] hashBytes = digest.digest(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hashBytes) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            return "";
        }
    }
}
