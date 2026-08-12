package com.anushaporter.backend.controller;

import com.anushaporter.backend.model.Franchise;
import com.anushaporter.backend.repository.FranchiseRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/franchises")
public class FranchiseController {
    @Autowired
    private FranchiseRepository repository;

    /**
     * GET /api/franchises
     * Returns regional franchise hubs list for Admin Franchises module.
     */
    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getAll() {
        List<Franchise> franchises = repository.findAll();

        List<Map<String, Object>> items = franchises.stream().map(f -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", f.getId() != null ? "FRN-" + f.getId() : "FRN-101");
            map.put("franchiseId", f.getId());
            map.put("name", f.getName() != null ? f.getName() : "Regional Depot");
            map.put("city", f.getCity() != null ? f.getCity() : "Hyderabad");
            map.put("head", f.getHead() != null ? f.getHead() : "Hub Manager");
            map.put("address", f.getAddress() != null ? f.getAddress() : "");
            map.put("driversCount", f.getDriversCount() != null ? f.getDriversCount() : 25);
            map.put("dailyOrders", f.getDailyOrders() != null ? f.getDailyOrders() : 150);
            map.put("revenue", f.getRevenue() != null ? f.getRevenue() : "₹2,50,000");
            map.put("status", f.getStatus() != null ? f.getStatus() : "Active");
            map.put("createdAt", f.getCreatedAt() != null ? f.getCreatedAt().toString() : java.time.LocalDateTime.now().toString());
            return map;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(items);
    }

    /**
     * POST /api/franchises
     * Creates new regional partner franchise depot.
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@RequestBody Franchise entity) {
        Franchise saved = repository.save(entity);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("id", "FRN-" + saved.getId());
        response.put("franchise", saved);
        return ResponseEntity.ok(response);
    }
}
