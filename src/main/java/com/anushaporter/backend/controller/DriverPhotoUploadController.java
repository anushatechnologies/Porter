package com.anushaporter.backend.controller;

import com.anushaporter.backend.dto.DriverPhotoUploadResponse;
import com.anushaporter.backend.model.Driver;
import com.anushaporter.backend.repository.AppUserRepository;
import com.anushaporter.backend.repository.DriverRepository;
import com.anushaporter.backend.service.DriverAuthService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.UUID;

@RestController
@CrossOrigin(originPatterns = "*")
public class DriverPhotoUploadController {

    private static final Logger log = LoggerFactory.getLogger(DriverPhotoUploadController.class);

    @Autowired
    private DriverRepository driverRepository;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private DriverAuthService driverAuthService;

    @Autowired
    private com.anushaporter.backend.service.document.DocumentValidationDispatcher documentDispatcher;

    @Value("${file.upload-dir:uploads/}")
    private String baseUploadDir;

    @PostMapping({
            "/api/driver/photo",
            "/api/driver/uploadPhoto",
            "/driver/photo",
            "/driver/uploadPhoto",
            "/api/drivers/photo",
            "/api/driver/registerFace",
            "/driver/registerFace"
    })
    public ResponseEntity<DriverPhotoUploadResponse> uploadDriverPhoto(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "driverId", required = false) Long driverId,
            @RequestParam(value = "phone", required = false) String phone,
            @RequestParam(value = "email", required = false) String email,
            HttpServletRequest request) {

        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(DriverPhotoUploadResponse.failure("Photo file is required. Please choose or capture an image."));
        }

        if (file.getSize() > 10 * 1024 * 1024L) {
            return ResponseEntity.badRequest()
                    .body(DriverPhotoUploadResponse.failure("Image file size exceeds maximum limit of 10 MB."));
        }

        try {
            // Biometric Human Face Validation
            if (documentDispatcher != null) {
                com.anushaporter.backend.dto.ValidationResult faceResult = 
                        documentDispatcher.validate(com.anushaporter.backend.model.DocumentType.FACE, file.getBytes());
                if (!faceResult.isValid()) {
                    return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                            .body(DriverPhotoUploadResponse.failure(faceResult.getMessage()));
                }
            }

            // 1. Resolve or auto-provision target driver
            Driver driver = findDriver(driverId, phone, email, request);
            if (driver == null) {
                driver = new Driver();
                String driverPhone = (phone != null && !phone.isBlank()) ? phone.trim()
                        : (driverId != null ? "98765" + String.format("%05d", driverId % 100000) : "9876543210");
                driver.setPhone(driverPhone);
                driver.setName(driverId != null ? "Driver #" + driverId : "Driver");
                driver.setStatus("active");
                driver.setKyc("verified");
                if (email != null && !email.isBlank()) {
                    driver.setEmail(email.trim());
                }
                driver = driverRepository.save(driver);
                log.info("Auto-created driver profile ID #{} for photo upload.", driver.getId());
            }

            // 2. Ensure target upload directory exists
            String targetDir = baseUploadDir.endsWith("/") ? baseUploadDir + "driver_photos/" : baseUploadDir + "/driver_photos/";
            File dir = new File(targetDir);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            // 3. Save photo to storage
            String originalFileName = file.getOriginalFilename();
            String extension = (originalFileName != null && originalFileName.contains("."))
                    ? originalFileName.substring(originalFileName.lastIndexOf("."))
                    : ".jpg";
            String savedFileName = "driver-photo-" + driver.getId() + "-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 6) + extension;
            Path savePath = Paths.get(targetDir + savedFileName);

            try (InputStream in = file.getInputStream()) {
                Files.copy(in, savePath, StandardCopyOption.REPLACE_EXISTING);
            }

            String photoUrl = "/uploads/driver_photos/" + savedFileName;

            // 4. Update Driver profile photo URI
            driver.setProfilePhotoUri(photoUrl);
            driverRepository.save(driver);

            // 5. Also synchronize with AppUser profile if present
            if (driver.getPhone() != null && !driver.getPhone().isBlank()) {
                try {
                    appUserRepository.findFirstByPhoneOrderByIdDesc(driver.getPhone().trim()).ifPresent(u -> {
                        u.setProfilePhotoUri(photoUrl);
                        appUserRepository.save(u);
                    });
                } catch (Exception ex) {
                    log.warn("Could not sync photo to AppUser: {}", ex.getMessage());
                }
            }

            log.info("✓ Driver photo successfully saved for driver ID #{}: {}", driver.getId(), photoUrl);

            return ResponseEntity.ok(DriverPhotoUploadResponse.success(
                    driver.getId(),
                    driver.getName(),
                    photoUrl,
                    "Driver photo uploaded successfully."
            ));

        } catch (Exception e) {
            log.error("Failed to upload driver photo: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(DriverPhotoUploadResponse.failure("Failed to save driver photo: " + e.getMessage()));
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
