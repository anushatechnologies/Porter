package com.anushaporter.backend.controller;

import com.anushaporter.backend.model.GstDetails;
import com.anushaporter.backend.repository.GstDetailsRepository;
import com.anushaporter.backend.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
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
     * Returns the GST details for the authenticated user.
     */
    @GetMapping
    public ResponseEntity<?> getGstDetails(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        String email = extractEmail(authHeader);
        if (email != null) {
            Optional<GstDetails> userRecord = repository.findFirstByUserEmail(email);
            if (userRecord.isPresent()) {
                return ResponseEntity.ok(Map.of("success", true, "data", userRecord.get()));
            }
        } else {
            // Legacy: fall back to returning the first record (backward compat)
            if (!repository.findAll().isEmpty()) {
                return ResponseEntity.ok(Map.of("success", true, "data", repository.findAll().get(0)));
            }
        }

        return ResponseEntity.ok(Map.of("success", true, "data", (Object) null));
    }

    /**
     * POST /api/user/gst
     * Create or update GST details for the authenticated user.
     */
    @PostMapping
    public ResponseEntity<?> createOrUpdateGstDetails(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody GstDetails payload) {

        String email = extractEmail(authHeader);

        // Try to find existing record for this user
        GstDetails record;
        if (email != null) {
            record = repository.findFirstByUserEmail(email).orElseGet(() -> {
                GstDetails newRecord = new GstDetails();
                newRecord.setId("gst_" + System.currentTimeMillis());
                newRecord.setUserEmail(email);
                return newRecord;
            });
        } else {
            // Legacy path: use ID from payload or create new
            if (payload.getId() != null && repository.existsById(payload.getId())) {
                record = repository.findById(payload.getId()).get();
            } else {
                record = new GstDetails();
                record.setId(payload.getId() != null ? payload.getId() : "gst_" + System.currentTimeMillis());
            }
        }

        // Update fields
        if (payload.getGstin() != null) record.setGstin(payload.getGstin());
        if (payload.getBusinessName() != null) record.setBusinessName(payload.getBusinessName());
        if (payload.getBillingAddress() != null) record.setBillingAddress(payload.getBillingAddress());

        GstDetails saved = repository.save(record);
        return ResponseEntity.ok(Map.of("success", true, "data", saved));
    }

    /**
     * PUT /api/user/gst/{id}
     * Update a specific GST record by ID.
     */
    @PutMapping("/{id}")
    public ResponseEntity<?> updateGstDetails(
            @PathVariable String id,
            @RequestBody GstDetails payload) {

        Optional<GstDetails> existingOpt = repository.findById(id);
        if (existingOpt.isPresent()) {
            GstDetails existing = existingOpt.get();
            if (payload.getGstin() != null) existing.setGstin(payload.getGstin());
            if (payload.getBusinessName() != null) existing.setBusinessName(payload.getBusinessName());
            if (payload.getBillingAddress() != null) existing.setBillingAddress(payload.getBillingAddress());
            return ResponseEntity.ok(repository.save(existing));
        }
        return ResponseEntity.notFound().build();
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
