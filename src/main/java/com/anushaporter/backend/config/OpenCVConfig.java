package com.anushaporter.backend.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Configuration
public class OpenCVConfig {

    private static final Logger log = LoggerFactory.getLogger(OpenCVConfig.class);
    private static volatile boolean openCVLoaded = false;
    private static String faceCascadePath;
    private static String eyeCascadePath;

    @PostConstruct
    public void initOpenCV() {
        try {
            nu.pattern.OpenCV.loadLocally();
            openCVLoaded = true;
            log.info("✓ OpenCV native libraries initialized successfully.");
        } catch (Throwable t) {
            log.warn("OpenCV native loadLocally failed (fallback to System.loadLibrary or pure-java mode): {}", t.getMessage());
            try {
                System.loadLibrary(org.opencv.core.Core.NATIVE_LIBRARY_NAME);
                openCVLoaded = true;
                log.info("✓ OpenCV loaded via System.loadLibrary({}).", org.opencv.core.Core.NATIVE_LIBRARY_NAME);
            } catch (Throwable t2) {
                log.warn("OpenCV System.loadLibrary also failed: {}. Will use fallback heuristic engine.", t2.getMessage());
            }
        }

        // Initialize Haar Cascades from resources to temp files
        initHaarCascades();
    }

    private void initHaarCascades() {
        try {
            Path tempDir = Files.createTempDirectory("opencv_cascades");
            tempDir.toFile().deleteOnExit();

            faceCascadePath = extractResource("/haarcascades/haarcascade_frontalface_alt.xml", tempDir, "haarcascade_frontalface_alt.xml");
            eyeCascadePath = extractResource("/haarcascades/haarcascade_eye.xml", tempDir, "haarcascade_eye.xml");

            if (faceCascadePath != null) {
                log.info("✓ Face Haar Cascade initialized at: {}", faceCascadePath);
            }
        } catch (Exception e) {
            log.warn("Could not extract bundled Haar cascades: {}", e.getMessage());
        }
    }

    private String extractResource(String resourcePath, Path targetDir, String fileName) {
        try (InputStream in = getClass().getResourceAsStream(resourcePath)) {
            if (in == null) {
                return null;
            }
            Path targetFile = targetDir.resolve(fileName);
            Files.copy(in, targetFile, StandardCopyOption.REPLACE_EXISTING);
            targetFile.toFile().deleteOnExit();
            return targetFile.toAbsolutePath().toString();
        } catch (Exception e) {
            log.debug("Resource not found or failed to extract: {}", resourcePath);
            return null;
        }
    }

    public static boolean isOpenCVLoaded() {
        return openCVLoaded;
    }

    public static String getFaceCascadePath() {
        return faceCascadePath;
    }

    public static String getEyeCascadePath() {
        return eyeCascadePath;
    }
}
