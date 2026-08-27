package com.anushaporter.backend.controller;

import com.anushaporter.backend.dto.FaceAuthResponse;
import com.anushaporter.backend.model.Driver;
import com.anushaporter.backend.model.DriverFaceEmbedding;
import com.anushaporter.backend.repository.DriverFaceEmbeddingRepository;
import com.anushaporter.backend.repository.DriverRepository;
import com.anushaporter.backend.service.DriverAuthService;
import com.anushaporter.backend.service.face.FaceEmbeddingService;
import com.anushaporter.backend.service.face.OpenCVFaceService;
import com.anushaporter.backend.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@CrossOrigin(originPatterns = "*")
public class DriverFaceAuthController {

    private static final Logger log = LoggerFactory.getLogger(DriverFaceAuthController.class);

    @Autowired
    private DriverRepository driverRepository;

    @Autowired
    private DriverFaceEmbeddingRepository faceEmbeddingRepository;

    @Autowired
    private OpenCVFaceService openCVFaceService;

    @Autowired
    private FaceEmbeddingService faceEmbeddingService;

    @Autowired
    private DriverAuthService driverAuthService;

    // ─────────────────────────────────────────────────────────────────────────
    // 1. REGISTER DRIVER FACE EMBEDDING: POST /driver/registerFace
    // ─────────────────────────────────────────────────────────────────────────
    @PostMapping({"/driver/registerFace", "/api/driver/registerFace", "/api/drivers/registerFace", "/drivers/registerFace"})
    @Transactional
    public ResponseEntity<FaceAuthResponse> registerFace(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "driverId", required = false) Long driverId,
            @RequestParam(value = "phone", required = false) String phone,
            @RequestParam(value = "email", required = false) String email,
            HttpServletRequest request) {

        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(FaceAuthResponse.failure("Face image file is required."));
        }

        if (file.getSize() > 10 * 1024 * 1024L) {
            return ResponseEntity.badRequest().body(FaceAuthResponse.failure("Image file size exceeds maximum limit of 10 MB."));
        }

        // 1. Resolve or auto-provision target driver
        Driver driver = findDriver(driverId, phone, email, request);
        if (driver == null) {
            driver = new Driver();
            String driverPhone = (phone != null && !phone.isBlank()) ? phone.trim()
                    : (driverId != null ? "98765" + String.format("%05d", driverId % 100000) : "9876543210");
            driver.setPhone(driverPhone);
            driver.setName(driverId != null ? "Driver #" + driverId : "Registered Driver");
            driver.setStatus("active");
            driver.setKyc("verified");
            if (email != null && !email.isBlank()) {
                driver.setEmail(email.trim());
            }
            driver = driverRepository.save(driver);
            log.info("Auto-created driver profile ID #{} for face registration.", driver.getId());
        }

        try {
            byte[] imageBytes = file.getBytes();

            // 2. Detect face & perform liveness/anti-spoofing check
            OpenCVFaceService.DetectionResult detection = openCVFaceService.processAndExtractFace(imageBytes);
            if (!detection.isSuccess()) {
                return ResponseEntity.badRequest().body(
                        FaceAuthResponse.failure(detection.getErrorMessage(), 0.0, detection.getLivenessScore(), detection.getFaceCount())
                );
            }

            // 3. Generate high-dimensional facial biometric embedding vector
            float[] embeddingVector = faceEmbeddingService.generateEmbedding(detection.getFaceMat());
            String serializedVector = faceEmbeddingService.serializeVector(embeddingVector);
            String faceHash = faceEmbeddingService.computeFaceHash(embeddingVector);

            // 4. Save face photo to uploads directory
            String uploadDir = "uploads/driver_faces/";
            File dir = new File(uploadDir);
            if (!dir.exists()) dir.mkdirs();

            String originalName = file.getOriginalFilename();
            String ext = (originalName != null && originalName.contains("."))
                    ? originalName.substring(originalName.lastIndexOf(".")) : ".jpg";
            String fileName = "driver-face-" + driver.getId() + "-" + UUID.randomUUID().toString() + ext;
            Path savePath = Paths.get(uploadDir + fileName);

            try (InputStream in = file.getInputStream()) {
                Files.copy(in, savePath, StandardCopyOption.REPLACE_EXISTING);
            }
            String photoUrl = "/uploads/driver_faces/" + fileName;

            // 5. Deactivate previous face profiles for this driver
            List<DriverFaceEmbedding> existingProfiles = faceEmbeddingRepository.findAllByDriverId(driver.getId());
            for (DriverFaceEmbedding oldProfile : existingProfiles) {
                oldProfile.setStatus("SUPERSEDED");
            }
            faceEmbeddingRepository.saveAll(existingProfiles);

            // 6. Save new face biometric profile
            DriverFaceEmbedding newEmbedding = new DriverFaceEmbedding(
                    driver.getId(),
                    serializedVector,
                    embeddingVector.length,
                    faceHash,
                    detection.getLivenessScore(),
                    photoUrl
            );
            faceEmbeddingRepository.save(newEmbedding);

            // Update driver profile photo URI if empty
            if (driver.getProfilePhotoUri() == null || driver.getProfilePhotoUri().isBlank()) {
                driver.setProfilePhotoUri(photoUrl);
                driverRepository.save(driver);
            }

            log.info("✓ Face biometric profile successfully registered for driver ID #{}", driver.getId());

            return ResponseEntity.ok(FaceAuthResponse.registrationSuccess(
                    driver.getId(),
                    driver.getName(),
                    detection.getLivenessScore(),
                    photoUrl,
                    "Driver face biometric profile registered successfully."
            ));

        } catch (Exception e) {
            log.error("Failed to register driver face: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(FaceAuthResponse.failure("Internal error during face registration: " + e.getMessage()));
        }
    }

    private Driver findDriver(Long driverId, String phone, String email, HttpServletRequest request) {
        if (driverId != null) {
            Optional<Driver> opt = driverRepository.findById(driverId);
            if (opt.isPresent()) return opt.get();
        }
        if (phone != null && !phone.isBlank()) {
            Optional<Driver> opt = driverRepository.findByPhone(phone.trim());
            if (opt.isPresent()) return opt.get();
        }
        if (email != null && !email.isBlank()) {
            Optional<Driver> opt = driverRepository.findByEmail(email.trim());
            if (opt.isPresent()) return opt.get();
        }
        if (request != null) {
            return driverAuthService.resolveAuthenticatedDriver(request);
        }
        return null;
    }
}
