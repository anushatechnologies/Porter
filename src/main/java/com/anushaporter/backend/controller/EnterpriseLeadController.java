package com.anushaporter.backend.controller;

import com.anushaporter.backend.model.EnterpriseLead;
import com.anushaporter.backend.repository.EnterpriseLeadRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/enterprise/leads")
public class EnterpriseLeadController {
    private final EnterpriseLeadRepository repository;
    public EnterpriseLeadController(EnterpriseLeadRepository repository) { this.repository = repository; }
    @PostMapping
    public ResponseEntity<?> create(@RequestBody EnterpriseLead lead) {
        if (lead.getCompanyName() == null || lead.getContactPerson() == null || lead.getPhone() == null)
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "companyName, contactPerson and phone are required"));
        EnterpriseLead saved = repository.save(lead);
        return ResponseEntity.ok(Map.of("success", true, "leadId", saved.getId(), "lead", saved));
    }
}
