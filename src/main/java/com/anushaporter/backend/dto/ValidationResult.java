package com.anushaporter.backend.dto;

import com.anushaporter.backend.model.DocumentType;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ValidationResult {

    private boolean valid;
    private int status;
    private DocumentType documentType;
    private String reason;
    private String message;
    private String error;
    private Double confidence;
    private Map<String, Object> extractedData;

    public ValidationResult() {
        this.extractedData = new LinkedHashMap<>();
    }

    public static ValidationResult success(DocumentType type, String message, Map<String, Object> extractedData, Double confidence) {
        ValidationResult result = new ValidationResult();
        result.valid = true;
        result.status = 200;
        result.documentType = type;
        result.reason = ValidationReason.SUCCESS;
        result.message = message != null ? message : type.getDisplayName() + " validated successfully.";
        result.confidence = confidence;
        result.extractedData = extractedData != null ? new LinkedHashMap<>(extractedData) : new LinkedHashMap<>();
        return result;
    }

    public static ValidationResult mismatch(DocumentType type, String message) {
        ValidationResult result = new ValidationResult();
        result.valid = false;
        result.status = 422;
        result.documentType = type;
        result.error = "Unprocessable Entity";
        result.reason = ValidationReason.DOCUMENT_TYPE_MISMATCH;
        result.message = message;
        return result;
    }

    public static ValidationResult reject(DocumentType type, String reason, String message) {
        ValidationResult result = new ValidationResult();
        result.valid = false;
        result.status = 422;
        result.documentType = type;
        result.error = "Unprocessable Entity";
        result.reason = reason;
        result.message = message;
        return result;
    }

    public static ValidationResult error(DocumentType type, int status, String error, String reason, String message) {
        ValidationResult result = new ValidationResult();
        result.valid = false;
        result.status = status;
        result.documentType = type;
        result.error = error;
        result.reason = reason;
        result.message = message;
        return result;
    }

    public boolean isValid() {
        return valid;
    }

    public void setValid(boolean valid) {
        this.valid = valid;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public DocumentType getDocumentType() {
        return documentType;
    }

    public void setDocumentType(DocumentType documentType) {
        this.documentType = documentType;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public Double getConfidence() {
        return confidence;
    }

    public void setConfidence(Double confidence) {
        this.confidence = confidence;
    }

    public Map<String, Object> getExtractedData() {
        return extractedData;
    }

    public void setExtractedData(Map<String, Object> extractedData) {
        this.extractedData = extractedData;
    }

    public ValidationResult addData(String key, Object value) {
        if (this.extractedData == null) {
            this.extractedData = new LinkedHashMap<>();
        }
        this.extractedData.put(key, value);
        return this;
    }
}
