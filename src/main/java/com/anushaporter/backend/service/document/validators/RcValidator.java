package com.anushaporter.backend.service.document.validators;

import com.anushaporter.backend.dto.ValidationReason;
import com.anushaporter.backend.dto.ValidationResult;
import com.anushaporter.backend.model.DocumentType;
import com.anushaporter.backend.service.document.DocumentValidator;
import com.anushaporter.backend.service.document.OcrExtractionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class RcValidator implements DocumentValidator {

    private static final Logger log = LoggerFactory.getLogger(RcValidator.class);

    private final OcrExtractionService ocrService;

    // Standard Indian Vehicle Registration Number (e.g. MH12AB1234, DL01A1234, KA03HA1234, HR26DQ5551)
    private static final Pattern VEHICLE_REG_PATTERN = Pattern.compile("\\b([A-Z]{2}[-\\s]?[0-9]{1,2}[-\\s]?[A-Z]{1,3}[-\\s]?[0-9]{4})\\b");

    private static final List<String> RC_KEYWORDS = Arrays.asList(
            "REGISTRATION CERTIFICATE",
            "CERTIFICATE OF REGISTRATION",
            "FORM 23",
            "FORM-23",
            "CHASSIS NO",
            "CHASSIS NUMBER",
            "ENGINE NO",
            "ENGINE NUMBER",
            "REGISTERING AUTHORITY",
            "REGISTRATION NO",
            "REGN NO",
            "REG. NO",
            "VEHICLE CLASS",
            "CLASS OF VEHICLE",
            "UNLADEN WT",
            "GROSS VEHICLE WT",
            "CUBIC CAPACITY",
            "SEATING CAPACITY",
            "FUEL TYPE",
            "MAKER'S CLASSIFICATION",
            "MAKER NAME",
            "MODEL NAME",
            "BHARAT STAGE",
            "VAHAN"
    );

    @Autowired
    public RcValidator(OcrExtractionService ocrService) {
        this.ocrService = ocrService;
    }

    @Override
    public DocumentType supports() {
        return DocumentType.RC;
    }

    @Override
    public ValidationResult validate(byte[] imageBytes) {
        if (imageBytes == null || imageBytes.length == 0) {
            return ValidationResult.reject(DocumentType.RC, ValidationReason.INVALID_FILE, "No image file provided.");
        }

        String extractedText = ocrService.extractText(imageBytes);
        log.info("RC Validator OCR Extracted Text: [{}]", extractedText.length() > 200 ? extractedText.substring(0, 200) + "..." : extractedText);

        if (!ocrService.isTextReadable(extractedText)) {
            return ValidationResult.reject(
                    DocumentType.RC,
                    ValidationReason.TEXT_NOT_READABLE,
                    "Text on the image is not clear or readable. Please upload a clear photo of your Vehicle Registration Certificate (RC) with good lighting."
            );
        }

        // 1. Check for Vehicle Number pattern
        String matchedVehicleNumber = null;
        Matcher matcher = VEHICLE_REG_PATTERN.matcher(extractedText);
        while (matcher.find()) {
            String candidate = matcher.group(1).replaceAll("[-\\s]+", "");
            // Filter out common false positives (like date strings or PANs)
            if (candidate.length() >= 8 && candidate.length() <= 11) {
                matchedVehicleNumber = candidate;
                break;
            }
        }

        // 2. Check for RC Keywords
        List<String> matchedKeywords = new ArrayList<>();
        for (String kw : RC_KEYWORDS) {
            if (extractedText.contains(kw)) {
                matchedKeywords.add(kw);
            }
        }

        boolean hasRcKeyword = !matchedKeywords.isEmpty();
        boolean hasVehiclePattern = (matchedVehicleNumber != null);

        // 3. Validation Logic: Pattern + Keyword, or 2+ Strong Keywords (e.g. Chassis No + Engine No)
        if ((hasVehiclePattern && hasRcKeyword) || (matchedKeywords.size() >= 2)) {
            Map<String, Object> data = new LinkedHashMap<>();
            if (matchedVehicleNumber != null) {
                data.put("vehicleNumber", matchedVehicleNumber);
            }
            data.put("matchedKeywords", matchedKeywords);
            data.put("confidence", 0.95);

            return ValidationResult.success(
                    DocumentType.RC,
                    "Vehicle Registration Certificate (RC) validated successfully.",
                    data,
                    0.95
            );
        }

        // 4. Mismatch Rejection
        return ValidationResult.mismatch(
                DocumentType.RC,
                "This doesn't look like a Vehicle Registration Certificate (RC). Please upload a clear photo of your vehicle RC."
        );
    }
}
