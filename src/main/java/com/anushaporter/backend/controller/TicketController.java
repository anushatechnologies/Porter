package com.anushaporter.backend.controller;

import com.anushaporter.backend.model.Ticket;
import com.anushaporter.backend.repository.TicketRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

import java.util.List;

@RestController
@RequestMapping("/api/tickets")

public class TicketController {
    @Autowired
    private TicketRepository repository;

    @GetMapping
    public List<Ticket> getAll() {
        return repository.findAll();
    }

    @PostMapping
    public Ticket create(@RequestBody Ticket entity) {
        return repository.save(entity);
    }

    @PostMapping("/{id}/message")
    public ResponseEntity<java.util.Map<String, Object>> addMessage(@PathVariable Long id, @RequestBody Map<String, String> payload) {
        // Just mock it by returning the ticket
        return repository.findById(id).map(ticket -> {
            return ResponseEntity.ok(java.util.Map.of("success", (Object) true, "message", (Object) ticket));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/status")
    public ResponseEntity<java.util.Map<String, Object>> updateStatus(@PathVariable Long id, @RequestBody Map<String, String> payload) {
        return repository.findById(id).map(ticket -> {
            ticket.setStatus(payload.get("status"));
            repository.save(ticket);
            return ResponseEntity.ok(java.util.Map.of("success", (Object) true));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/resolve")
    public ResponseEntity<java.util.Map<String, Object>> resolveTicket(@PathVariable Long id) {
        return repository.findById(id).map(ticket -> {
            ticket.setStatus("resolved");
            repository.save(ticket);
            return ResponseEntity.ok(java.util.Map.of("success", (Object) true));
        }).orElse(ResponseEntity.notFound().build());
    }
}
