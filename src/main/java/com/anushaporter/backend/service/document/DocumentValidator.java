package com.anushaporter.backend.service.document;

import com.anushaporter.backend.dto.ValidationResult;
import com.anushaporter.backend.model.DocumentType;

public interface DocumentValidator {
    
    /**
     * Validates the provided image bytes against the specific document type format rules.
     *
     * @param imageBytes raw image bytes (JPEG, PNG, WEBP, etc.)
     * @return ValidationResult containing status, validity, reason code, and extracted metadata
     */
    ValidationResult validate(byte[] imageBytes);

    /**
     * The DocumentType supported by this validator.
     */
    DocumentType supports();
}
