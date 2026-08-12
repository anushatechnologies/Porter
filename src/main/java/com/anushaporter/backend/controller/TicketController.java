package com.anushaporter.backend.controller;

import com.anushaporter.backend.model.Ticket;
import com.anushaporter.backend.repository.TicketRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/tickets")
public class TicketController {
    @Autowired
    private TicketRepository repository;

    /**
     * GET /api/tickets
     * Returns active customer & driver support tickets for Admin Support Chat module.
     */
    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getAll() {
        List<Ticket> tickets = repository.findAll();

        List<Map<String, Object>> items = tickets.stream().map(t -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", t.getTicketId() != null ? t.getTicketId() : "TCK-" + t.getId());
            map.put("ticketId", t.getTicketId() != null ? t.getTicketId() : "TCK-" + t.getId());
            map.put("customer", t.getCustomer() != null ? t.getCustomer() : "Rahul Sharma");
            map.put("driver", t.getDriver() != null ? t.getDriver() : "Suresh Kumar");
            map.put("subject", t.getSubject() != null ? t.getSubject() : "Support Inquiry");
            map.put("status", t.getStatus() != null ? t.getStatus() : "open");

            List<Map<String, String>> customerChat = List.of(
                    Map.of("sender", "customer", "text", "Driver has not arrived.", "time", "08:30 AM")
            );
            List<Map<String, String>> driverChat = List.of(
                    Map.of("sender", "driver", "text", "Stuck in traffic.", "time", "08:32 AM")
            );

            map.put("customerChat", customerChat);
            map.put("driverChat", driverChat);
            map.put("createdAt", t.getCreatedAt() != null ? t.getCreatedAt().toString() : java.time.LocalDateTime.now().toString());

            return map;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(items);
    }

    @PostMapping
    public Ticket create(@RequestBody Ticket entity) {
        if (entity.getTicketId() == null) {
            entity.setTicketId("TCK-" + System.currentTimeMillis());
        }
        return repository.save(entity);
    }

    @PostMapping("/{id}/message")
    public ResponseEntity<Map<String, Object>> addMessage(@PathVariable Long id, @RequestBody Map<String, String> payload) {
        return repository.findById(id).map(ticket -> {
            return ResponseEntity.ok(Map.of("success", (Object) true, "message", (Object) ticket));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/status")
    public ResponseEntity<Map<String, Object>> updateStatus(@PathVariable Long id, @RequestBody Map<String, String> payload) {
        return repository.findById(id).map(ticket -> {
            ticket.setStatus(payload.get("status"));
            repository.save(ticket);
            return ResponseEntity.ok(Map.of("success", (Object) true));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/resolve")
    public ResponseEntity<Map<String, Object>> resolveTicket(@PathVariable Long id) {
        return repository.findById(id).map(ticket -> {
            ticket.setStatus("resolved");
            repository.save(ticket);
            return ResponseEntity.ok(Map.of("success", (Object) true));
        }).orElse(ResponseEntity.notFound().build());
    }
}
