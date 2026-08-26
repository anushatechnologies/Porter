package com.anushaporter.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Slot availability endpoints.
 *
 * Customer App:
 *   GET /api/services/{serviceId}/slots?date=2026-08-27   — per-service slots (main)
 *   GET /api/slots?serviceId=xxx&date=2026-08-27          — legacy alias
 */
@RestController
public class SlotController {

    /**
     * GET /api/services/{serviceId}/slots?date=2026-08-27
     * Returns available time slots for a given service on the specified date.
     */
    @GetMapping("/api/services/{serviceId}/slots")
    public ResponseEntity<Map<String, Object>> getSlotsByService(
            @PathVariable String serviceId,
            @RequestParam("date") String date) {
        return ResponseEntity.ok(buildSlotsResponse(date));
    }

    /**
     * GET /api/slots?serviceId=xxx&date=2026-08-27
     * Legacy / generic slot endpoint kept for backward compatibility.
     */
    @GetMapping("/api/slots")
    public ResponseEntity<Map<String, Object>> getAvailabilitySlots(
            @RequestParam(value = "serviceId", required = false) String serviceId,
            @RequestParam("date") String date) {
        return ResponseEntity.ok(buildSlotsResponse(date));
    }

    private Map<String, Object> buildSlotsResponse(String date) {
        List<Map<String, Object>> slots = List.of(
            Map.of("id", "slot-1", "label", "07:00 AM - 09:00 AM", "available", true),
            Map.of("id", "slot-2", "label", "09:00 AM - 11:00 AM", "available", true),
            Map.of("id", "slot-3", "label", "02:00 PM - 04:00 PM", "available", true)
        );
        return Map.of(
            "success", true,
            "date", date,
            "slots", slots
        );
    }
}
