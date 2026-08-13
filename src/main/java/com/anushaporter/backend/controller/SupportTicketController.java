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
     * Endpoint 3: Submit Support Ticket
     * POST /api/support/ticket & POST /api/support/tickets
     */
    @PostMapping({"/api/support/ticket", "/api/support/tickets"})
    public ResponseEntity<Map<String, Object>> createTicket(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody Map<String, String> body) {

        Map<String, Object> response = new LinkedHashMap<>();
        String email = extractEmail(authHeader);
        if (email == null) email = "driver@anushaporter.com";

        String ticketNum = "TCK-" + (1000 + new Random().nextInt(9000));

        SupportTicket ticket = new SupportTicket();
        ticket.setTicketId(ticketNum);
        ticket.setUserEmail(email);
        ticket.setTopicId(body.getOrDefault("subject", body.getOrDefault("topicId", "General Help")));
        ticket.setBookingId(body.getOrDefault("bookingId", null));
        ticket.setMessage(body.getOrDefault("description", body.getOrDefault("message", "")));
        ticket.setStatus("open");

        ticketRepository.save(ticket);

        response.put("success", true);
        response.put("ticketId", ticketNum);
        response.put("id", ticketNum);
        response.put("subject", ticket.getTopicId());
        response.put("message", "Support ticket created successfully");
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
