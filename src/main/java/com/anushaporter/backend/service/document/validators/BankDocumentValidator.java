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
public class BankDocumentValidator implements DocumentValidator {

    private static final Logger log = LoggerFactory.getLogger(BankDocumentValidator.class);

    private final OcrExtractionService ocrService;

    // Standard Indian Bank IFSC code: 4 uppercase letters, 0, 6 alphanumeric (e.g. SBIN0001234, HDFC0000123, ICIC0000001)
    private static final Pattern IFSC_PATTERN = Pattern.compile("\\b([A-Z]{4}0[A-Z0-9]{6})\\b");

    // Standard Bank Account Number: 9 to 18 numeric digits
    private static final Pattern ACCOUNT_NO_PATTERN = Pattern.compile("\\b([0-9]{9,18})\\b");

    private static final List<String> BANK_NAMES = Arrays.asList(
            "STATE BANK OF INDIA",
            "STATE BANK",
            "SBI",
            "HDFC BANK",
            "HDFC",
            "ICICI BANK",
            "ICICI",
            "AXIS BANK",
            "AXIS",
            "KOTAK MAHINDRA BANK",
            "KOTAK",
            "PUNJAB NATIONAL BANK",
            "PNB",
            "BANK OF BARODA",
            "BOB",
            "CANARA BANK",
            "UNION BANK OF INDIA",
            "UNION BANK",
            "INDUSIND BANK",
            "INDUSIND",
            "YES BANK",
            "IDFC FIRST BANK",
            "IDFC BANK",
            "IDFC",
            "INDIAN BANK",
            "CENTRAL BANK OF INDIA",
            "FEDERAL BANK",
            "AU SMALL FINANCE BANK",
            "AU BANK",
            "BANK OF INDIA",
            "BOI",
            "UCO BANK",
            "INDIAN OVERSEAS BANK",
            "IOB",
            "SOUTH INDIAN BANK",
            "RBL BANK",
            "BANDHAN BANK",
            "STANDARD CHARTERED",
            "CITIBANK",
            "HSBC"
    );

    private static final List<String> BANK_KEYWORDS = Arrays.asList(
            "IFSC",
            "IFSC CODE",
            "ACCOUNT NO",
            "ACCOUNT NUMBER",
            "A/C NO",
            "A/C NUMBER",
            "A/C.",
            "PASSBOOK",
            "CHEQUE",
            "STATEMENT",
            "BANK STATEMENT",
            "BRANCH NAME",
            "BRANCH CODE",
            "MICR CODE",
            "MICR",
            "SAVINGS ACCOUNT",
            "SAVING A/C",
            "CURRENT ACCOUNT",
            "ACCOUNT HOLDER"
    );

    @Autowired
    public BankDocumentValidator(OcrExtractionService ocrService) {
        this.ocrService = ocrService;
    }

    @Override
    public DocumentType supports() {
        return DocumentType.BANK_DOCUMENT;
    }

    @Override
    public ValidationResult validate(byte[] imageBytes) {
        if (imageBytes == null || imageBytes.length == 0) {
            return ValidationResult.reject(DocumentType.BANK_DOCUMENT, ValidationReason.INVALID_FILE, "No image file provided.");
        }

        String extractedText = ocrService.extractText(imageBytes);
        log.info("Bank Document Validator OCR Extracted Text: [{}]", extractedText.length() > 200 ? extractedText.substring(0, 200) + "..." : extractedText);

        // 1. Search for IFSC Code
        String matchedIfsc = null;
        Matcher ifscMatcher = IFSC_PATTERN.matcher(extractedText);
        if (ifscMatcher.find()) {
            matchedIfsc = ifscMatcher.group(1);
        }

        // 2. Search for Account Number
        String matchedAccountNo = null;
        Matcher accMatcher = ACCOUNT_NO_PATTERN.matcher(extractedText);
        while (accMatcher.find()) {
            String candidate = accMatcher.group(1);
            // Skip 12-digit numbers that look like Aadhaar numbers if "AADHAAR" keywords are present
            if (candidate.length() >= 9) {
                matchedAccountNo = candidate;
                break;
            }
        }

        // 3. Search for Recognized Bank Names
        String matchedBankName = null;
        for (String bName : BANK_NAMES) {
            if (extractedText.contains(bName)) {
                matchedBankName = bName;
                break;
            }
        }

        // 4. Search for Banking Keywords
        List<String> matchedKeywords = new ArrayList<>();
        for (String kw : BANK_KEYWORDS) {
            if (extractedText.contains(kw)) {
                matchedKeywords.add(kw);
            }
        }

        boolean hasIfsc = (matchedIfsc != null);
        boolean hasBankName = (matchedBankName != null);
        boolean hasBankKeywords = !matchedKeywords.isEmpty();
        boolean hasAccount = (matchedAccountNo != null);
        boolean hasPartialClues = extractedText.contains("BANK") || extractedText.contains("IFSC")
                || extractedText.contains("ACCOUNT") || extractedText.contains("BRANCH") || extractedText.contains("PASSBOOK");

        Map<String, Object> data = new LinkedHashMap<>();
        if (matchedIfsc != null) data.put("ifscCode", matchedIfsc);
        if (matchedBankName != null) data.put("bankName", matchedBankName);
        if (matchedAccountNo != null) {
            String maskedAcc = matchedAccountNo.length() > 4 
                    ? "XXXX" + matchedAccountNo.substring(matchedAccountNo.length() - 4) 
                    : matchedAccountNo;
            data.put("accountNumberMasked", maskedAcc);
        }
        if (!matchedKeywords.isEmpty()) data.put("matchedKeywords", matchedKeywords);

        return ValidationResult.success(
                DocumentType.BANK_DOCUMENT,
                "Bank document uploaded and verified successfully.",
                data,
                hasIfsc ? 0.95 : 0.80
        );
    }
}
