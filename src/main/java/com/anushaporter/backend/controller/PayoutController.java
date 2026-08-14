package com.anushaporter.backend.controller;

import com.anushaporter.backend.model.Payout;
import com.anushaporter.backend.repository.PayoutRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
public class PayoutController {
    @Autowired
    private PayoutRepository repository;

    /**
     * GET /api/payouts
     * Returns driver payout requests for Admin Payouts module.
     */
    @GetMapping("/api/payouts")
    public ResponseEntity<List<Map<String, Object>>> getAll() {
        List<Payout> payouts = repository.findAll();

        List<Map<String, Object>> items = payouts.stream().map(p -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", p.getPayoutId() != null ? p.getPayoutId() : "PAY-" + p.getId());
            map.put("payoutId", p.getPayoutId() != null ? p.getPayoutId() : "PAY-" + p.getId());
            map.put("driverId", p.getDriverId() != null ? p.getDriverId() : "DRV-100");
            map.put("driverName", p.getDriverName() != null ? p.getDriverName() : "Partner Driver");
            map.put("bankAccount", p.getBankAccount() != null ? p.getBankAccount() : "XXXX-XXXX-4589");
            map.put("ifscCode", p.getIfscCode() != null ? p.getIfscCode() : "SBIN0001234");
            map.put("amount", p.getAmount() != null ? p.getAmount() : 1000.0);
            map.put("status", p.getStatus() != null ? p.getStatus() : "pending");
            map.put("createdAt", p.getCreatedAt() != null ? p.getCreatedAt().toString() : java.time.LocalDateTime.now().toString());
            return map;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(items);
    }

    @PostMapping("/api/payouts")
    public Payout create(@RequestBody Payout entity) {
        if (entity.getPayoutId() == null) {
            entity.setPayoutId("PAY-" + System.currentTimeMillis());
        }
        return repository.save(entity);
    }

    @PostMapping("/api/payouts/{id}/release")
    public ResponseEntity<Map<String, Object>> releasePayout(@PathVariable Long id) {
        return repository.findById(id).map(payout -> {
            payout.setStatus("settled");
            repository.save(payout);
            return ResponseEntity.ok(Map.of("success", (Object) true, "status", (Object) "settled"));
        }).orElse(ResponseEntity.notFound().build());
    }

    /**
     * Endpoint 1: Request Instant Driver Payout
     * POST /api/drivers/me/payouts/request
     */
    @PostMapping({"/api/drivers/payouts/request", "/api/payouts/request"})
    public ResponseEntity<Map<String, Object>> requestInstantPayout(@RequestBody(required = false) Map<String, Object> body) {
        double amount = 500.0;
        String accountNumber = "1234567890";
        String ifscCode = "SBIN0001234";

        if (body != null) {
            if (body.get("amount") != null) {
                try { amount = Double.parseDouble(body.get("amount").toString()); } catch (Exception ignored) {}
            }
            if (body.get("accountNumber") != null) accountNumber = String.valueOf(body.get("accountNumber"));
            if (body.get("ifscCode") != null) ifscCode = String.valueOf(body.get("ifscCode"));
        }

        String payoutId = "PO-" + (1000 + new Random().nextInt(9000));

        Payout p = new Payout();
        p.setPayoutId(payoutId);
        p.setAmount(amount);
        p.setBankAccount(accountNumber);
        p.setIfscCode(ifscCode);
        p.setStatus("processing");
        p.setDriverId("DRV-102");
        p.setDriverName("Partner Driver");
        repository.save(p);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("payoutId", payoutId);
        response.put("status", "processing");
        response.put("message", "Payout request submitted successfully");
        return ResponseEntity.ok(response);
    }
}
