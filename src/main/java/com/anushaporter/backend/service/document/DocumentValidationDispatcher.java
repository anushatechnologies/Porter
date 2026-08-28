package com.anushaporter.backend.service.document;

import com.anushaporter.backend.dto.ValidationResult;
import com.anushaporter.backend.model.DocumentType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
public class DocumentValidationDispatcher {

    private final Map<DocumentType, DocumentValidator> validators;

    @Autowired
    public DocumentValidationDispatcher(List<DocumentValidator> validatorList) {
        this.validators = new EnumMap<>(DocumentType.class);
        for (DocumentValidator validator : validatorList) {
            this.validators.put(validator.supports(), validator);
        }
    }

    /**
     * Dispatches image bytes to the specific DocumentValidator for validation.
     *
     * @param type       target DocumentType (AADHAAR, PAN, DRIVING_LICENCE, RC, BANK_DOCUMENT, FACE)
     * @param imageBytes raw image bytes
     * @return ValidationResult
     */
    public ValidationResult validate(DocumentType type, byte[] imageBytes) {
        if (type == null) {
            throw new IllegalArgumentException("DocumentType must not be null.");
        }

        DocumentValidator validator = validators.get(type);
        if (validator == null) {
            throw new IllegalArgumentException("No validator registered for " + type);
        }

        return validator.validate(imageBytes);
    }
}
