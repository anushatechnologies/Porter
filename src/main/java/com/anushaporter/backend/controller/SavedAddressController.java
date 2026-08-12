package com.anushaporter.backend.controller;

import com.anushaporter.backend.model.SavedAddress;
import com.anushaporter.backend.repository.SavedAddressRepository;
import com.anushaporter.backend.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController

public class SavedAddressController {

    @Autowired
    private SavedAddressRepository addressRepository;

    @Autowired
    private JwtUtil jwtUtil;

    /**
     * List user's saved addresses.
     * GET /api/addresses
     */
    @GetMapping("/api/addresses")
    public ResponseEntity<Map<String, Object>> getAddresses(
            @RequestHeader("Authorization") String authHeader) {

        Map<String, Object> response = new HashMap<>();
        String email = extractEmail(authHeader);
        if (email == null) {
            response.put("success", false);
            response.put("message", "Unauthorized");
            return ResponseEntity.status(401).body(response);
        }

        List<SavedAddress> addresses = addressRepository.findByUserEmailOrderByCreatedAtDesc(email);

        List<Map<String, Object>> items = addresses.stream().map(addr -> {
            Map<String, Object> item = new HashMap<>();
            item.put("id", "addr_" + addr.getId());
            item.put("label", addr.getLabel());
            item.put("tag", addr.getTag());
            item.put("addressLine", addr.getAddressLine());
            item.put("lat", addr.getLat());
            item.put("lng", addr.getLng());
            return item;
        }).collect(Collectors.toList());

        response.put("success", true);
        response.put("addresses", items);
        return ResponseEntity.ok(response);
    }

    /**
     * Create a saved address.
     * POST /api/addresses
     */
    @PostMapping("/api/addresses")
    public ResponseEntity<Map<String, Object>> createAddress(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, Object> body) {

        Map<String, Object> response = new HashMap<>();
        String email = extractEmail(authHeader);
        if (email == null) {
            response.put("success", false);
            response.put("message", "Unauthorized");
            return ResponseEntity.status(401).body(response);
        }

        SavedAddress address = new SavedAddress();
        address.setUserEmail(email);
        address.setLabel((String) body.getOrDefault("label", ""));
        address.setTag((String) body.getOrDefault("tag", "home"));
        address.setAddressLine((String) body.getOrDefault("addressLine", ""));

        if (body.get("lat") != null) {
            address.setLat(((Number) body.get("lat")).doubleValue());
        }
        if (body.get("lng") != null) {
            address.setLng(((Number) body.get("lng")).doubleValue());
        }

        addressRepository.save(address);

        response.put("success", true);
        response.put("id", "addr_" + address.getId());
        response.put("label", address.getLabel());
        response.put("tag", address.getTag());
        response.put("addressLine", address.getAddressLine());
        response.put("lat", address.getLat());
        response.put("lng", address.getLng());
        return ResponseEntity.ok(response);
    }

    /**
     * Update a saved address.
     * PUT /api/addresses/{id}
     */
    @PutMapping("/api/addresses/{id}")
    public ResponseEntity<Map<String, Object>> updateAddress(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {

        Map<String, Object> response = new HashMap<>();
        String email = extractEmail(authHeader);
        if (email == null) {
            response.put("success", false);
            response.put("message", "Unauthorized");
            return ResponseEntity.status(401).body(response);
        }

        Optional<SavedAddress> opt = addressRepository.findById(id);
        if (opt.isEmpty()) {
            response.put("success", false);
            response.put("message", "Address not found");
            return ResponseEntity.status(404).body(response);
        }

        SavedAddress address = opt.get();
        if (body.containsKey("label")) address.setLabel((String) body.get("label"));
        if (body.containsKey("tag")) address.setTag((String) body.get("tag"));
        if (body.containsKey("addressLine")) address.setAddressLine((String) body.get("addressLine"));
        if (body.get("lat") != null) address.setLat(((Number) body.get("lat")).doubleValue());
        if (body.get("lng") != null) address.setLng(((Number) body.get("lng")).doubleValue());

        addressRepository.save(address);

        response.put("success", true);
        response.put("id", "addr_" + address.getId());
        response.put("label", address.getLabel());
        response.put("tag", address.getTag());
        response.put("addressLine", address.getAddressLine());
        return ResponseEntity.ok(response);
    }

    /**
     * Delete a saved address.
     * DELETE /api/addresses/{id}
     */
    @DeleteMapping("/api/addresses/{id}")
    public ResponseEntity<Map<String, Object>> deleteAddress(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long id) {

        Map<String, Object> response = new HashMap<>();
        String email = extractEmail(authHeader);
        if (email == null) {
            response.put("success", false);
            response.put("message", "Unauthorized");
            return ResponseEntity.status(401).body(response);
        }

        if (!addressRepository.existsById(id)) {
            response.put("success", false);
            response.put("message", "Address not found");
            return ResponseEntity.status(404).body(response);
        }

        addressRepository.deleteById(id);
        response.put("success", true);
        return ResponseEntity.ok(response);
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
