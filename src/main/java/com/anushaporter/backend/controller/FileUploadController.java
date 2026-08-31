package com.anushaporter.backend.controller;

import com.anushaporter.backend.service.S3ImageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/upload")
public class FileUploadController {

    @Autowired
    private S3ImageService s3ImageService;

    @PostMapping("/image")
    public ResponseEntity<?> uploadImage(@RequestParam("file") MultipartFile file,
                                         @RequestParam(value = "category", defaultValue = "misc") String category) {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "No file provided", "success", false));
        }

        try {
            String fileUrl = s3ImageService.uploadImage(file, category);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "url", fileUrl
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("success", false, "error", "Failed to save file: " + e.getMessage()));
        }
    }
}
