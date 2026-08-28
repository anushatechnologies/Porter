package com.anushaporter.backend.controller;

import com.anushaporter.backend.dto.ValidationReason;
import com.anushaporter.backend.dto.ValidationResult;
import com.anushaporter.backend.model.DocumentType;
import com.anushaporter.backend.service.document.DocumentValidationDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Base64;
import java.util.Map;

@RestController
@RequestMapping("/api/documents")
@CrossOrigin(originPatterns = "*")
public class DocumentValidationController {

    private static final Logger log = LoggerFactory.getLogger(DocumentValidationController.class);

    private final DocumentValidationDispatcher dispatcher;

    @Autowired
    public DocumentValidationController(DocumentValidationDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    /**
     * Primary document validation endpoint supporting query param '?type=...' or path variable '/{type}'.
     *
     * Example:
     * POST /api/documents/validate?type=PAN
     * Form-Data: file=[binary image]
     */
    @PostMapping(value = {
            "/validate",
            "/validate/{type}"
    }, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> validateDocumentMultipart(
            @PathVariable(value = "type", required = false) String pathType,
            @RequestParam(value = "type", required = false) String paramType,
            @RequestParam(value = "documentType", required = false) String docTypeParam,
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "image", required = false) MultipartFile image,
            @RequestParam(value = "document", required = false) MultipartFile document) {

        String typeStr = (pathType != null && !pathType.isBlank()) ? pathType :
                ((paramType != null && !paramType.isBlank()) ? paramType : docTypeParam);

        if (typeStr != null && typeStr.contains(",")) {
            typeStr = typeStr.split(",")[0].trim();
        }

        if (typeStr == null || typeStr.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", 400,
                    "error", "Bad Request",
                    "reason", ValidationReason.MISSING_REQUIRED_FIELDS,
                    "message", "Missing required parameter 'type'. Supported types: AADHAAR, PAN, DRIVING_LICENCE, RC, BANK_DOCUMENT, FACE."
            ));
        }

        DocumentType docType;
        try {
            docType = DocumentType.fromString(typeStr);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", 400,
                    "error", "Bad Request",
                    "reason", ValidationReason.INVALID_FILE,
                    "message", e.getMessage()
            ));
        }

        MultipartFile targetFile = (file != null && !file.isEmpty()) ? file :
                ((image != null && !image.isEmpty()) ? image : document);

        if (targetFile == null || targetFile.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", 400,
                    "error", "Bad Request",
                    "reason", ValidationReason.INVALID_FILE,
                    "message", "No file uploaded. Please upload a photo or document image."
            ));
        }

        try {
            byte[] bytes = targetFile.getBytes();
            ValidationResult result = dispatcher.validate(docType, bytes);

            if (result.isValid()) {
                return ResponseEntity.ok(result);
            } else {
                return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(result);
            }
        } catch (Exception e) {
            log.error("Failed to process document validation: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "status", 500,
                    "error", "Internal Server Error",
                    "message", "Failed to process image: " + e.getMessage()
            ));
        }
    }

    /**
     * JSON Payload validation endpoint supporting base64 encoded images.
     *
     * Example:
     * POST /api/documents/validate
     * Content-Type: application/json
     * Body: { "type": "PAN", "image": "data:image/jpeg;base64,..." }
     */
    @PostMapping(value = {
            "/validate",
            "/validate/{type}"
    }, consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> validateDocumentJson(
            @PathVariable(value = "type", required = false) String pathType,
            @RequestParam(value = "type", required = false) String paramType,
            @RequestBody Map<String, Object> body) {

        String typeStr = (pathType != null && !pathType.isBlank()) ? pathType :
                ((paramType != null && !paramType.isBlank()) ? paramType : 
                        (body != null ? (String) body.getOrDefault("type", body.get("documentType")) : null));

        if (typeStr == null || typeStr.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", 400,
                    "error", "Bad Request",
                    "reason", ValidationReason.MISSING_REQUIRED_FIELDS,
                    "message", "Missing required field 'type'. Supported types: AADHAAR, PAN, DRIVING_LICENCE, RC, BANK_DOCUMENT, FACE."
            ));
        }

        DocumentType docType;
        try {
            docType = DocumentType.fromString(typeStr);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", 400,
                    "error", "Bad Request",
                    "reason", ValidationReason.INVALID_FILE,
                    "message", e.getMessage()
            ));
        }

        String base64Image = null;
        if (body != null) {
            if (body.get("image") instanceof String) {
                base64Image = (String) body.get("image");
            } else if (body.get("imageBase64") instanceof String) {
                base64Image = (String) body.get("imageBase64");
            } else if (body.get("file") instanceof String) {
                base64Image = (String) body.get("file");
            }
        }

        if (base64Image == null || base64Image.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", 400,
                    "error", "Bad Request",
                    "reason", ValidationReason.INVALID_FILE,
                    "message", "No base64 image provided in JSON payload."
            ));
        }

        try {
            // Strip data URL prefix if present (e.g. data:image/jpeg;base64,...)
            if (base64Image.contains(",")) {
                base64Image = base64Image.substring(base64Image.indexOf(",") + 1);
            }
            byte[] bytes = Base64.getDecoder().decode(base64Image.trim());

            ValidationResult result = dispatcher.validate(docType, bytes);
            if (result.isValid()) {
                return ResponseEntity.ok(result);
            } else {
                return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(result);
            }
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", 400,
                    "error", "Bad Request",
                    "reason", ValidationReason.INVALID_FILE,
                    "message", "Invalid Base64 encoded image string."
            ));
        } catch (Exception e) {
            log.error("Failed to process document validation (JSON): {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "status", 500,
                    "error", "Internal Server Error",
                    "message", "Failed to process image: " + e.getMessage()
            ));
        }
    }
}
