package com.anushaporter.backend.controller;

import com.anushaporter.backend.model.GstDetails;
import com.anushaporter.backend.repository.GstDetailsRepository;
import com.anushaporter.backend.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * GST invoicing details for business users.
 *
 * GET  /api/user/gst  – fetch the logged-in user's GST details
 * POST /api/user/gst  – create or update GST details
 * PUT  /api/user/gst/{id} – update existing GST record
 */
@RestController
@RequestMapping("/api/user/gst")
public class GstDetailsController {

    @Autowired
    private GstDetailsRepository repository;

    @Autowired
    private JwtUtil jwtUtil;

    /**
     * GET /api/user/gst
     * Returns normalized GST details for the authenticated user.
     */
    @GetMapping
    public ResponseEntity<?> getGstDetails(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        String email = extractEmail(authHeader);
        GstDetails gst = null;
        if (email != null) {
            gst = repository.findFirstByUserEmail(email).orElse(null);
        } else if (!repository.findAll().isEmpty()) {
            gst = repository.findAll().get(0);
        }

        if (gst == null) {
            return ResponseEntity.ok(Map.of("success", true, "data", (Object) null));
        }

        return ResponseEntity.ok(Map.of("success", true, "data", toNormalizedMap(gst)));
    }

    /**
     * POST /api/user/gst
     * Create or update GST details (accepts companyName/businessName & registeredAddress/billingAddress).
     */
    @PostMapping
    public ResponseEntity<?> createOrUpdateGstDetails(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody Map<String, Object> payload) {

        String email = extractEmail(authHeader);

        GstDetails record;
        if (email != null) {
            record = repository.findFirstByUserEmail(email).orElseGet(() -> {
                GstDetails newRecord = new GstDetails();
                newRecord.setId("gst_" + System.currentTimeMillis());
                newRecord.setUserEmail(email);
                return newRecord;
            });
        } else {
            String payloadId = (String) payload.get("id");
            if (payloadId != null && repository.existsById(payloadId)) {
                record = repository.findById(payloadId).get();
            } else {
                record = new GstDetails();
                record.setId(payloadId != null ? payloadId : "gst_" + System.currentTimeMillis());
            }
        }

        String gstin = text(payload, "gstin");
        String company = text(payload, "companyName");
        if (company == null) company = text(payload, "businessName");
        String address = text(payload, "registeredAddress");
        if (address == null) address = text(payload, "billingAddress");

        if (gstin != null) record.setGstin(gstin);
        if (company != null) record.setBusinessName(company);
        if (address != null) record.setBillingAddress(address);

        GstDetails saved = repository.save(record);
        return ResponseEntity.ok(Map.of("success", true, "data", toNormalizedMap(saved)));
    }

    /**
     * PUT /api/user/gst/{id}
     * Update a specific GST record by ID.
     */
    @PutMapping("/{id}")
    public ResponseEntity<?> updateGstDetails(
            @PathVariable String id,
            @RequestBody Map<String, Object> payload) {

        Optional<GstDetails> existingOpt = repository.findById(id);
        if (existingOpt.isPresent()) {
            GstDetails record = existingOpt.get();
            String gstin = text(payload, "gstin");
            String company = text(payload, "companyName");
            if (company == null) company = text(payload, "businessName");
            String address = text(payload, "registeredAddress");
            if (address == null) address = text(payload, "billingAddress");

            if (gstin != null) record.setGstin(gstin);
            if (company != null) record.setBusinessName(company);
            if (address != null) record.setBillingAddress(address);

            GstDetails saved = repository.save(record);
            return ResponseEntity.ok(Map.of("success", true, "data", toNormalizedMap(saved)));
        }
        return ResponseEntity.notFound().build();
    }

    private String text(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val == null) return null;
        String s = String.valueOf(val).trim();
        return s.isEmpty() ? null : s;
    }

    private Map<String, Object> toNormalizedMap(GstDetails gst) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", gst.getId());
        map.put("gstin", gst.getGstin() != null ? gst.getGstin() : "");
        map.put("businessName", gst.getBusinessName() != null ? gst.getBusinessName() : "");
        map.put("companyName", gst.getBusinessName() != null ? gst.getBusinessName() : "");
        map.put("billingAddress", gst.getBillingAddress() != null ? gst.getBillingAddress() : "");
        map.put("registeredAddress", gst.getBillingAddress() != null ? gst.getBillingAddress() : "");
        return map;
    }

    private String extractEmail(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) return null;
        try {
            return jwtUtil.getUsernameFromToken(authHeader.substring(7));
        } catch (Exception e) {
            return null;
        }
    }
}
