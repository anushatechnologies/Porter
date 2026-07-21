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
    public ResponseEntity<Ticket> addMessage(@PathVariable Long id, @RequestBody Map<String, String> payload) {
        // Just mock it by returning the ticket
        return repository.findById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<Ticket> updateStatus(@PathVariable Long id, @RequestBody Map<String, String> payload) {
        return repository.findById(id).map(ticket -> {
            ticket.setStatus(payload.get("status"));
            return ResponseEntity.ok(repository.save(ticket));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/resolve")
    public ResponseEntity<Ticket> resolveTicket(@PathVariable Long id) {
        return repository.findById(id).map(ticket -> {
            ticket.setStatus("resolved");
            return ResponseEntity.ok(repository.save(ticket));
        }).orElse(ResponseEntity.notFound().build());
    }
}
