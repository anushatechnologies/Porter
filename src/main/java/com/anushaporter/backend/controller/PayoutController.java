package com.anushaporter.backend.controller;

import com.anushaporter.backend.model.Payout;
import com.anushaporter.backend.repository.PayoutRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/payouts")

public class PayoutController {
    @Autowired
    private PayoutRepository repository;

    @GetMapping
    public List<Payout> getAll() {
        return repository.findAll();
    }

    @PostMapping
    public Payout create(@RequestBody Payout entity) {
        return repository.save(entity);
    }

    @PostMapping("/{id}/release")
    public ResponseEntity<java.util.Map<String, Object>> releasePayout(@PathVariable Long id) {
        return repository.findById(id).map(payout -> {
            payout.setStatus("settled"); // Using "settled" based on spec
            repository.save(payout);
            return ResponseEntity.ok(java.util.Map.of("success", (Object) true, "status", (Object) "settled"));
        }).orElse(ResponseEntity.notFound().build());
    }
}
