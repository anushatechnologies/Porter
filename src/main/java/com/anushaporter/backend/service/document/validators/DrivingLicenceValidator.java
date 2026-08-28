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
public class DrivingLicenceValidator implements DocumentValidator {

    private static final Logger log = LoggerFactory.getLogger(DrivingLicenceValidator.class);

    private final OcrExtractionService ocrService;

    // Standard DL regex: 2-letter state code + numbers (e.g. DL-0420110012345, MH12 20180012345, KA01 19990001234, RJ1420150001234)
    private static final Pattern DL_PATTERN = Pattern.compile("\\b([A-Z]{2}[-\\s]?[0-9]{2}[-\\s]?(?:19|20)?[0-9]{2}[-\\s]?[0-9]{7})\\b");
    private static final Pattern DL_ALT_PATTERN = Pattern.compile("\\b([A-Z]{2}[0-9]{13,15})\\b");
    private static final Pattern DL_SLASH_PATTERN = Pattern.compile("\\b([A-Z]{2}[0-9]{2}/[A-Z0-9/]{4,15})\\b");

    private static final List<String> DL_KEYWORDS = Arrays.asList(
            "DRIVING LICENCE",
            "DRIVING LICENSE",
            "INDIAN UNION DRIVING LICENCE",
            "UNION OF INDIA",
            "TRANSPORT DEPARTMENT",
            "MOTOR VEHICLES",
            "FORM 7",
            "FORM-7",
            "AUTHORISATION TO DRIVE",
            "LICENCE TO DRIVE",
            "LICENSE TO DRIVE"
    );

    private static final List<String> DL_VEHICLE_CLASSES = Arrays.asList(
            "LMV",
            "MCWG",
            "MCWOG",
            "TRANS",
            "HMV",
            "HGMV",
            "3W-CAB",
            "LMV-NT",
            "LMV-TR"
    );

    private static final List<String> DL_VALIDITY_KEYWORDS = Arrays.asList(
            "VALID TILL",
            "VALIDITY",
            "VALID UPTO",
            "DATE OF ISSUE",
            "DOI",
            "EXPIRY",
            "NON-TRANSPORT",
            "TRANSPORT"
    );

    @Autowired
    public DrivingLicenceValidator(OcrExtractionService ocrService) {
        this.ocrService = ocrService;
    }

    @Override
    public DocumentType supports() {
        return DocumentType.DRIVING_LICENCE;
    }

    @Override
    public ValidationResult validate(byte[] imageBytes) {
        if (imageBytes == null || imageBytes.length == 0) {
            return ValidationResult.reject(DocumentType.DRIVING_LICENCE, ValidationReason.INVALID_FILE, "No image file provided.");
        }

        String extractedText = ocrService.extractText(imageBytes);
        log.info("DL Validator OCR Extracted Text: [{}]", extractedText.length() > 200 ? extractedText.substring(0, 200) + "..." : extractedText);

        if (!ocrService.isTextReadable(extractedText)) {
            return ValidationResult.reject(
                    DocumentType.DRIVING_LICENCE,
                    ValidationReason.TEXT_NOT_READABLE,
                    "Text on the image is not clear or readable. Please upload a clear photo of your Driving Licence with good lighting."
            );
        }

        // 1. Check DL Number pattern
        String matchedDlNumber = null;
        Matcher m1 = DL_PATTERN.matcher(extractedText);
        if (m1.find()) {
            matchedDlNumber = m1.group(1);
        } else {
            Matcher m2 = DL_ALT_PATTERN.matcher(extractedText);
            if (m2.find()) {
                matchedDlNumber = m2.group(1);
            } else {
                Matcher m3 = DL_SLASH_PATTERN.matcher(extractedText);
                if (m3.find()) {
                    matchedDlNumber = m3.group(1);
                }
            }
        }

        // 2. Check for DL Keywords
        List<String> matchedKeywords = new ArrayList<>();
        for (String kw : DL_KEYWORDS) {
            if (extractedText.contains(kw)) {
                matchedKeywords.add(kw);
            }
        }

        List<String> matchedClasses = new ArrayList<>();
        for (String vc : DL_VEHICLE_CLASSES) {
            if (extractedText.contains(vc)) {
                matchedClasses.add(vc);
            }
        }

        boolean hasValidityIndicator = false;
        for (String vk : DL_VALIDITY_KEYWORDS) {
            if (extractedText.contains(vk)) {
                hasValidityIndicator = true;
                matchedKeywords.add(vk);
            }
        }

        boolean hasDlKeyword = !matchedKeywords.isEmpty();
        boolean hasDlPattern = (matchedDlNumber != null);
        boolean hasVehicleClass = !matchedClasses.isEmpty();
        boolean hasPartialClues = extractedText.contains("DRIV") || extractedText.contains("LICEN")
                || extractedText.contains("TRANSPORT") || extractedText.contains("MOTOR")
                || extractedText.contains("LMV") || extractedText.contains("MCWG") || extractedText.contains("VALID");

        // 3. Validation Logic: 50% / Flexible matching
        if (hasDlPattern || hasDlKeyword || hasVehicleClass || hasPartialClues) {
            Map<String, Object> data = new LinkedHashMap<>();
            if (matchedDlNumber != null) data.put("licenseNumber", matchedDlNumber);
            if (!matchedClasses.isEmpty()) data.put("vehicleClasses", matchedClasses);
            data.put("hasValidityIndicator", hasValidityIndicator);
            data.put("matchedKeywords", matchedKeywords);
            data.put("confidence", hasDlPattern ? 0.95 : 0.75);

            return ValidationResult.success(
                    DocumentType.DRIVING_LICENCE,
                    "Driving Licence validated successfully.",
                    data,
                    hasDlPattern ? 0.95 : 0.75
            );
        }

        // 4. Mismatch Rejection
        return ValidationResult.mismatch(
                DocumentType.DRIVING_LICENCE,
                "This doesn't look like a Driving Licence. Please upload a clear photo of your Driving Licence."
        );
    }
}
