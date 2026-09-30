package com.anushaporter.backend.util;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Utility to automatically resolve Indian Bank names from standard RBI IFSC codes.
 * The first 4 alphabetic characters of any 11-digit IFSC code uniquely identify the bank.
 */
public final class IfscBankResolver {

    private static final Map<String, String> PREFIX_TO_BANK;

    static {
        Map<String, String> map = new HashMap<>();

        // Major Public Sector Banks
        map.put("SBIN", "State Bank of India");
        map.put("UBIN", "Union Bank of India");
        map.put("PUNB", "Punjab National Bank");
        map.put("BARB", "Bank of Baroda");
        map.put("CNRB", "Canara Bank");
        map.put("BKID", "Bank of India");
        map.put("CBIN", "Central Bank of India");
        map.put("IDIB", "Indian Bank");
        map.put("IOBA", "Indian Overseas Bank");
        map.put("UCBA", "UCO Bank");
        map.put("MAHB", "Bank of Maharashtra");
        map.put("PSIB", "Punjab & Sind Bank");

        // Major Private Sector Banks
        map.put("HDFC", "HDFC Bank");
        map.put("ICIC", "ICICI Bank");
        map.put("UTIB", "Axis Bank");
        map.put("AXIS", "Axis Bank");
        map.put("KKBK", "Kotak Mahindra Bank");
        map.put("INDB", "IndusInd Bank");
        map.put("YESB", "Yes Bank");
        map.put("IDFB", "IDFC First Bank");
        map.put("FDRL", "Federal Bank");
        map.put("KVBL", "Karur Vysya Bank");
        map.put("SIBL", "South Indian Bank");
        map.put("RATN", "RBL Bank");
        map.put("BDBL", "Bandhan Bank");
        map.put("CSBK", "CSB Bank");
        map.put("CIUB", "City Union Bank");
        map.put("TMBL", "Tamilnad Mercantile Bank");
        map.put("DCBL", "DCB Bank");
        map.put("JAKA", "Jammu & Kashmir Bank");
        map.put("KBLA", "Karnataka Bank");
        map.put("DLXB", "Dhanlaxmi Bank");

        // Small Finance & Payments Banks
        map.put("AUBK", "AU Small Finance Bank");
        map.put("ESFB", "Equitas Small Finance Bank");
        map.put("UJVN", "Ujjivan Small Finance Bank");
        map.put("JSFB", "Jana Small Finance Bank");
        map.put("UTKS", "Utkarsh Small Finance Bank");
        map.put("FINO", "Fino Payments Bank");
        map.put("PYTM", "Paytm Payments Bank");
        map.put("AIRP", "Airtel Payments Bank");
        map.put("IPOS", "India Post Payments Bank");

        // Foreign Banks Operating in India
        map.put("SCBL", "Standard Chartered Bank");
        map.put("CITI", "Citibank");
        map.put("HSBC", "HSBC Bank");
        map.put("DBSS", "DBS Bank");
        map.put("DEUT", "Deutsche Bank");

        // Merged bank historical RBI codes
        map.put("ANDB", "Union Bank of India"); // Andhra Bank merged into UBI
        map.put("CORP", "Union Bank of India"); // Corporation Bank merged into UBI
        map.put("ALLA", "Indian Bank");         // Allahabad Bank merged into Indian Bank
        map.put("SYNB", "Canara Bank");         // Syndicate Bank merged into Canara Bank
        map.put("VIJB", "Bank of Baroda");      // Vijaya Bank merged into BOB
        map.put("DENA", "Bank of Baroda");      // Dena Bank merged into BOB
        map.put("ORBC", "Punjab National Bank");// OBC merged into PNB
        map.put("UTBI", "Punjab National Bank");// United Bank of India merged into PNB

        PREFIX_TO_BANK = Collections.unmodifiableMap(map);
    }

    private IfscBankResolver() {}

    /**
     * Resolves bank name from an Indian IFSC code (e.g. "UBIN6469494" -> "Union Bank of India").
     *
     * @param ifsc the full or partial IFSC code
     * @return recognized bank name, or null if unknown / empty
     */
    public static String resolveBankName(String ifsc) {
        if (ifsc == null) return null;
        String clean = ifsc.trim().toUpperCase().replaceAll("[^A-Z0-9]", "");
        if (clean.length() < 4) return null;
        String prefix = clean.substring(0, 4);
        return PREFIX_TO_BANK.get(prefix);
    }

    /**
     * Resolves bank name with fallback: if explicit bank name is provided and valid, returns it;
     * otherwise falls back to deriving from IFSC code.
     */
    public static String resolveWithFallback(String explicitBankName, String ifsc) {
        if (explicitBankName != null && !explicitBankName.trim().isEmpty() && !explicitBankName.trim().equalsIgnoreCase("N/A")) {
            return explicitBankName.trim();
        }
        return resolveBankName(ifsc);
    }
}
