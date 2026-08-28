package com.anushaporter.backend.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

public enum DocumentType {
    AADHAAR("Aadhaar Card"),
    PAN("PAN Card"),
    DRIVING_LICENCE("Driving Licence"),
    RC("Vehicle Registration Certificate (RC)"),
    BANK_DOCUMENT("Bank Document"),
    FACE("Face Photo");

    private final String displayName;

    DocumentType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    @JsonValue
    public String getCode() {
        return name();
    }

    @JsonCreator
    public static DocumentType fromString(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        String cleanValue = value;
        if (cleanValue.contains(",")) {
            cleanValue = cleanValue.split(",")[0].trim();
        }
        String normalized = cleanValue.trim().toUpperCase(Locale.ROOT)
                .replace("-", "_")
                .replace(" ", "_");

        switch (normalized) {
            case "AADHAAR":
            case "AADHAR":
            case "ADHAR":
            case "UIDAI":
                return AADHAAR;
            case "PAN":
            case "PAN_CARD":
            case "PANCARD":
                return PAN;
            case "DRIVING_LICENCE":
            case "DRIVING_LICENSE":
            case "DL":
            case "LICENSE":
            case "LICENCE":
                return DRIVING_LICENCE;
            case "RC":
            case "RC_BOOK":
            case "REGISTRATION_CERTIFICATE":
            case "VEHICLE_RC":
                return RC;
            case "BANK":
            case "BANK_DOCUMENT":
            case "BANK_PASSBOOK":
            case "PASSBOOK":
            case "CHEQUE":
            case "CHECK":
            case "STATEMENT":
            case "BANK_STATEMENT":
                return BANK_DOCUMENT;
            case "FACE":
            case "FACE_PHOTO":
            case "PHOTO":
            case "SELFIE":
            case "PROFILE_PHOTO":
                return FACE;
            default:
                try {
                    return DocumentType.valueOf(normalized);
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("Unknown document type: " + value + 
                            ". Supported types are: AADHAAR, PAN, DRIVING_LICENCE, RC, BANK_DOCUMENT, FACE.");
                }
        }
    }
}
