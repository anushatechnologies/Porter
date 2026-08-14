package com.anushaporter.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/content")
public class ContentController {

    @GetMapping("/{slug}")
    public ResponseEntity<Map<String, Object>> getContent(@PathVariable String slug) {
        String clean = slug != null ? slug.trim().toLowerCase() : "terms";
        Map<String, Object> response = new LinkedHashMap<>();

        switch (clean) {
            case "terms", "terms-and-conditions" -> {
                response.put("success", true);
                response.put("slug", "terms");
                response.put("title", "Terms & Conditions");
                response.put("content", "Welcome to Anusha Porter. By using our application, you agree to our standard terms of logistics, transport, and parcel delivery services.");
                response.put("lastUpdated", "2026-08-01");
                return ResponseEntity.ok(response);
            }
            case "privacy", "privacy-policy" -> {
                response.put("success", true);
                response.put("slug", "privacy");
                response.put("title", "Privacy Policy");
                response.put("content", "Anusha Porter values your privacy. We collect GPS location for live tracking, order history, and contact numbers for delivery coordination.");
                response.put("lastUpdated", "2026-08-01");
                return ResponseEntity.ok(response);
            }
            case "about", "about-us" -> {
                response.put("success", true);
                response.put("slug", "about");
                response.put("title", "About Anusha Porter");
                response.put("content", "Anusha Porter is India's leading intra-city logistics and packers & movers platform, providing reliable on-demand delivery solutions.");
                response.put("version", "2.4.0");
                return ResponseEntity.ok(response);
            }
            case "refund", "refund-policy", "cancellation" -> {
                response.put("success", true);
                response.put("slug", "refund-policy");
                response.put("title", "Cancellation & Refund Policy");
                response.put("content", "Cancellations made prior to driver dispatch incur zero penalty. Wallet and online refunds are processed instantly.");
                response.put("lastUpdated", "2026-08-01");
                return ResponseEntity.ok(response);
            }
            default -> {
                response.put("success", true);
                response.put("slug", clean);
                response.put("title", clean.substring(0, 1).toUpperCase() + clean.substring(1));
                response.put("content", "Information for " + clean + " is available on Anusha Porter.");
                return ResponseEntity.ok(response);
            }
        }
    }
}
