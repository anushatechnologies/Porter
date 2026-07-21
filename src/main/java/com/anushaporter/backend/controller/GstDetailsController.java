package com.anushaporter.backend.controller;

import com.anushaporter.backend.model.GstDetails;
import com.anushaporter.backend.repository.GstDetailsRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/user/gst")
public class GstDetailsController {

    @Autowired
    private GstDetailsRepository repository;

    @GetMapping
    public ResponseEntity<GstDetails> getGstDetails() {
        List<GstDetails> all = repository.findAll();
        if (all.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(all.get(0)); // Returns the first one for the local app user
    }

    @PostMapping
    public ResponseEntity<GstDetails> createGstDetails(@RequestBody GstDetails payload) {
        if (payload.getId() == null) {
            payload.setId("gst_" + System.currentTimeMillis());
        }
        GstDetails saved = repository.save(payload);
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/{id}")
    public ResponseEntity<GstDetails> updateGstDetails(@PathVariable String id, @RequestBody GstDetails payload) {
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
}
