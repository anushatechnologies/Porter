package com.anushaporter.backend.controller;

import com.anushaporter.backend.model.SupportTicket;
import com.anushaporter.backend.repository.SupportTicketRepository;
import com.anushaporter.backend.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController

public class SupportTicketController {

    @Autowired
    private SupportTicketRepository ticketRepository;

    @Autowired
    private JwtUtil jwtUtil;

    @GetMapping("/api/support/topics")
    public ResponseEntity<List<Map<String, String>>> getTopics() {
        return ResponseEntity.ok(List.of(
                Map.of("id", "topic_booking", "title", "Booking Help"),
                Map.of("id", "topic_payment", "title", "Payment Issue"),
                Map.of("id", "topic_driver", "title", "Driver Complaint"),
                Map.of("id", "topic_general", "title", "General Support")
        ));
    }

    /**
     * Create a support ticket.
     * POST /api/support/tickets
     */
    @PostMapping("/api/support/tickets")
    public ResponseEntity<Map<String, Object>> createTicket(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, String> body) {

        Map<String, Object> response = new HashMap<>();
        String email = extractEmail(authHeader);
        if (email == null) {
            response.put("success", false);
            response.put("message", "Unauthorized");
            return ResponseEntity.status(401).body(response);
        }

        SupportTicket ticket = new SupportTicket();
        ticket.setTicketId("ticket_" + System.currentTimeMillis());
        ticket.setUserEmail(email);
        ticket.setTopicId(body.getOrDefault("topicId", ""));
        ticket.setBookingId(body.getOrDefault("bookingId", null));
        ticket.setMessage(body.getOrDefault("message", ""));
        ticket.setStatus("open");

        ticketRepository.save(ticket);

        response.put("success", true);
        response.put("id", ticket.getTicketId());
        response.put("topicId", ticket.getTopicId());
        response.put("bookingId", ticket.getBookingId());
        response.put("message", ticket.getMessage());
        response.put("status", ticket.getStatus());
        return ResponseEntity.ok(response);
    }

    /**
     * List user's support tickets.
     * GET /api/support/tickets
     */
    @GetMapping("/api/support/tickets")
    public ResponseEntity<Map<String, Object>> getTickets(
            @RequestHeader("Authorization") String authHeader) {

        Map<String, Object> response = new HashMap<>();
        String email = extractEmail(authHeader);
        if (email == null) {
            response.put("success", false);
            response.put("message", "Unauthorized");
            return ResponseEntity.status(401).body(response);
        }

        List<SupportTicket> tickets = ticketRepository.findByUserEmailOrderByCreatedAtDesc(email);

        List<Map<String, Object>> items = tickets.stream().map(t -> {
            Map<String, Object> item = new HashMap<>();
            item.put("id", t.getTicketId());
            item.put("topicId", t.getTopicId());
            item.put("bookingId", t.getBookingId());
            item.put("message", t.getMessage());
            item.put("status", t.getStatus());
            return item;
        }).collect(Collectors.toList());

        response.put("success", true);
        response.put("tickets", items);
        return ResponseEntity.ok(response);
    }

    private String extractEmail(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) return null;
        try {
            return jwtUtil.getUsernameFromToken(authHeader.substring(7));
        } catch (Exception e) {
            return null;
        }
    }
}
