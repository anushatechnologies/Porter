package com.anushaporter.backend.service.document.validators;

import com.anushaporter.backend.dto.ValidationReason;
import com.anushaporter.backend.dto.ValidationResult;
import com.anushaporter.backend.model.DocumentType;
import com.anushaporter.backend.service.document.DocumentValidator;
import com.anushaporter.backend.service.document.OcrExtractionService;
import com.anushaporter.backend.service.document.QrCodeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class AadhaarValidator implements DocumentValidator {

    private static final Logger log = LoggerFactory.getLogger(AadhaarValidator.class);

    private final OcrExtractionService ocrService;
    private final QrCodeService qrCodeService;

    // 12-digit patterns: 1234 5678 9012 or 123456789012 or masked XXXX XXXX 1234
    private static final Pattern AADHAAR_PATTERN = Pattern.compile("\\b(\\d{4}\\s?\\d{4}\\s?\\d{4})\\b");
    private static final Pattern MASKED_AADHAAR_PATTERN = Pattern.compile("\\b([X\\d]{4}\\s?[X\\d]{4}\\s?\\d{4})\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern VID_PATTERN = Pattern.compile("\\bVID\\s*[:\\s]?\\s*(\\d{4}\\s?\\d{4}\\s?\\d{4}\\s?\\d{4})\\b", Pattern.CASE_INSENSITIVE);

    private static final List<String> AADHAAR_KEYWORDS = Arrays.asList(
            "GOVERNMENT OF INDIA",
            "GOVT OF INDIA",
            "UNIQUE IDENTIFICATION AUTHORITY",
            "UNIQUE IDENTIFICATION AUTHORITY OF INDIA",
            "UIDAI",
            "आधार",
            "AADHAAR",
            "AADHAR",
            "MERA AADHAAR",
            "MERI PEHCHAAN",
            "ENROLMENT NO",
            "ENROLLMENT NO",
            "HELP@UIDAI.GOV.IN",
            "WWW.UIDAI.GOV.IN"
    );

    private static final List<String> SECONDARY_KEYWORDS = Arrays.asList(
            "DOB",
            "YEAR OF BIRTH",
            "YOB",
            "MALE",
            "FEMALE",
            "GENDER",
            "VID"
    );

    @Autowired
    public AadhaarValidator(OcrExtractionService ocrService, QrCodeService qrCodeService) {
        this.ocrService = ocrService;
        this.qrCodeService = qrCodeService;
    }

    @Override
    public DocumentType supports() {
        return DocumentType.AADHAAR;
    }

    @Override
    public ValidationResult validate(byte[] imageBytes) {
        if (imageBytes == null || imageBytes.length == 0) {
            return ValidationResult.reject(DocumentType.AADHAAR, ValidationReason.INVALID_FILE, "No image file provided.");
        }

        // 1. Scan for QR code (Aadhaar cards have secure QR codes)
        String qrText = qrCodeService.decodeQrCode(imageBytes);
        boolean qrFound = (qrText != null && !qrText.isBlank());
        boolean isUidaiQr = qrFound && (qrText.contains("uidai") || qrText.contains("xml") || qrText.matches(".*\\d{12}.*") || qrText.length() > 50);

        // 2. Perform OCR
        String extractedText = ocrService.extractText(imageBytes);
        log.info("Aadhaar Validator OCR Extracted Text: [{}]", extractedText.length() > 200 ? extractedText.substring(0, 200) + "..." : extractedText);

        if (!qrFound && !ocrService.isTextReadable(extractedText)) {
            return ValidationResult.reject(
                    DocumentType.AADHAAR,
                    ValidationReason.TEXT_NOT_READABLE,
                    "Text on the image is not clear or readable. Please upload a clear photo of your Aadhaar card with good lighting."
            );
        }

        // 3. Search for Aadhaar number pattern
        String matchedAadhaarNumber = null;
        Matcher matcher = AADHAAR_PATTERN.matcher(extractedText);
        if (matcher.find()) {
            matchedAadhaarNumber = matcher.group(1).replaceAll("\\s+", "");
        } else {
            Matcher maskedMatcher = MASKED_AADHAAR_PATTERN.matcher(extractedText);
            if (maskedMatcher.find()) {
                matchedAadhaarNumber = maskedMatcher.group(1);
            }
        }

        // 4. Keyword matching
        List<String> matchedKeywords = new ArrayList<>();
        for (String keyword : AADHAAR_KEYWORDS) {
            if (extractedText.contains(keyword)) {
                matchedKeywords.add(keyword);
            }
        }

        int secondaryMatches = 0;
        for (String keyword : SECONDARY_KEYWORDS) {
            if (extractedText.contains(keyword)) {
                secondaryMatches++;
                matchedKeywords.add(keyword);
            }
        }

        // 5. Decision evaluation
        boolean hasPrimaryKeyword = !matchedKeywords.isEmpty();
        boolean hasAadhaarNumber = matchedAadhaarNumber != null;

        // If UIDAI QR code is found or (Pattern + Keywords match)
        if (isUidaiQr || (hasAadhaarNumber && hasPrimaryKeyword) || (matchedKeywords.size() >= 2)) {
            Map<String, Object> data = new LinkedHashMap<>();
            if (matchedAadhaarNumber != null) {
                // Mask Aadhaar for privacy: XXXX XXXX 1234
                String masked = matchedAadhaarNumber.length() == 12 
                        ? "XXXX-XXXX-" + matchedAadhaarNumber.substring(8) 
                        : matchedAadhaarNumber;
                data.put("aadhaarNumberMasked", masked);
            }
            data.put("qrCodeDetected", qrFound);
            data.put("matchedKeywords", matchedKeywords);
            data.put("confidence", 0.95);

            return ValidationResult.success(
                    DocumentType.AADHAAR,
                    "Aadhaar card validated successfully.",
                    data,
                    0.95
            );
        }

        // 6. Rejected: Mismatch
        return ValidationResult.mismatch(
                DocumentType.AADHAAR,
                "This doesn't look like an Aadhaar card. Please upload a clear photo of the front of your Aadhaar card."
        );
    }
}
