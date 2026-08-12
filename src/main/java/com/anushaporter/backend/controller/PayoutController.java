package com.anushaporter.backend.controller;

import com.anushaporter.backend.model.Payout;
import com.anushaporter.backend.repository.PayoutRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/payouts")
public class PayoutController {
    @Autowired
    private PayoutRepository repository;

    /**
     * GET /api/payouts
     * Returns driver payout requests for Admin Payouts module.
     */
    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getAll() {
        List<Payout> payouts = repository.findAll();

        List<Map<String, Object>> items = payouts.stream().map(p -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", p.getPayoutId() != null ? p.getPayoutId() : "PAY-" + p.getId());
            map.put("payoutId", p.getPayoutId() != null ? p.getPayoutId() : "PAY-" + p.getId());
            map.put("driverId", p.getDriverId() != null ? p.getDriverId() : "DRV-100");
            map.put("driverName", p.getDriverName() != null ? p.getDriverName() : (p.getDriver() != null ? p.getDriver() : "Partner Driver"));
            map.put("bankAccount", p.getBankAccount() != null ? p.getBankAccount() : "XXXX-XXXX-4589");
            map.put("ifscCode", p.getIfscCode() != null ? p.getIfscCode() : "SBIN0001234");
            map.put("amount", p.getAmount() != null ? p.getAmount() : 1000.0);
            map.put("status", p.getStatus() != null ? p.getStatus() : "pending");
            map.put("createdAt", p.getCreatedAt() != null ? p.getCreatedAt().toString() : java.time.LocalDateTime.now().toString());
            return map;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(items);
    }

    @PostMapping
    public Payout create(@RequestBody Payout entity) {
        if (entity.getPayoutId() == null) {
            entity.setPayoutId("PAY-" + System.currentTimeMillis());
        }
        return repository.save(entity);
    }

    @PostMapping("/{id}/release")
    public ResponseEntity<Map<String, Object>> releasePayout(@PathVariable Long id) {
        return repository.findById(id).map(payout -> {
            payout.setStatus("settled");
            repository.save(payout);
            return ResponseEntity.ok(Map.of("success", (Object) true, "status", (Object) "settled"));
        }).orElse(ResponseEntity.notFound().build());
    }
}
