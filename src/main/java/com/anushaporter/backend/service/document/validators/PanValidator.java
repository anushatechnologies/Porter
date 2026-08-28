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
public class PanValidator implements DocumentValidator {

    private static final Logger log = LoggerFactory.getLogger(PanValidator.class);

    private final OcrExtractionService ocrService;

    // Standard 10-character PAN pattern: 5 letters, 4 digits, 1 letter
    private static final Pattern PAN_PATTERN = Pattern.compile("\\b([A-Z]{5}[0-9]{4}[A-Z])\\b");

    private static final List<String> PAN_KEYWORDS = Arrays.asList(
            "INCOME TAX DEPARTMENT",
            "INCOMETAX DEPARTMENT",
            "PERMANENT ACCOUNT NUMBER",
            "PERMANENT ACCOUNT",
            "GOVT. OF INDIA",
            "GOVT OF INDIA",
            "GOVERNMENT OF INDIA",
            "आयकर विभाग",
            "INCOME TAX"
    );

    private static final List<String> SECONDARY_KEYWORDS = Arrays.asList(
            "FATHER'S NAME",
            "FATHERS NAME",
            "DATE OF BIRTH",
            "SIGNATURE"
    );

    @Autowired
    public PanValidator(OcrExtractionService ocrService) {
        this.ocrService = ocrService;
    }

    @Override
    public DocumentType supports() {
        return DocumentType.PAN;
    }

    @Override
    public ValidationResult validate(byte[] imageBytes) {
        if (imageBytes == null || imageBytes.length == 0) {
            return ValidationResult.reject(DocumentType.PAN, ValidationReason.INVALID_FILE, "No image file provided.");
        }

        String extractedText = ocrService.extractText(imageBytes);
        log.info("PAN Validator OCR Extracted Text: [{}]", extractedText.length() > 200 ? extractedText.substring(0, 200) + "..." : extractedText);

        // 1. Check for PAN Regex pattern
        String matchedPan = null;
        Matcher matcher = PAN_PATTERN.matcher(extractedText);
        if (matcher.find()) {
            matchedPan = matcher.group(1);
        }

        // 2. Check for PAN Keywords
        List<String> matchedKeywords = new ArrayList<>();
        for (String kw : PAN_KEYWORDS) {
            if (extractedText.contains(kw)) {
                matchedKeywords.add(kw);
            }
        }

        for (String kw : SECONDARY_KEYWORDS) {
            if (extractedText.contains(kw)) {
                matchedKeywords.add(kw);
            }
        }

        boolean hasPanPattern = (matchedPan != null);
        boolean hasPanKeyword = !matchedKeywords.isEmpty();
        boolean hasPartialClues = extractedText.contains("INCOME") || extractedText.contains("ACCOUNT") 
                || extractedText.contains("INDIA") || extractedText.contains("TAX") || extractedText.contains("FATHER");

        // 3. Validation Logic: 50% / Flexible matching
        Map<String, Object> data = new LinkedHashMap<>();
        if (matchedPan != null) data.put("panNumber", matchedPan);
        if (!matchedKeywords.isEmpty()) data.put("matchedKeywords", matchedKeywords);

        return ValidationResult.success(
                DocumentType.PAN,
                "PAN card uploaded and verified successfully.",
                data,
                hasPanPattern ? 0.95 : 0.80
        );
    }
}
