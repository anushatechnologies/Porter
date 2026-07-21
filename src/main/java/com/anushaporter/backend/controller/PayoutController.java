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
    public ResponseEntity<Payout> releasePayout(@PathVariable Long id) {
        return repository.findById(id).map(payout -> {
            payout.setStatus("completed");
            return ResponseEntity.ok(repository.save(payout));
        }).orElse(ResponseEntity.notFound().build());
    }
}
