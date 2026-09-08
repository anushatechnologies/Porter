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
        return ResponseEntity.ok(buildSlotsResponse(serviceId, date));
    }

    /**
     * GET /api/slots?serviceId=xxx&date=2026-08-27
     * Legacy / generic slot endpoint kept for backward compatibility.
     */
    @GetMapping("/api/slots")
    public ResponseEntity<Map<String, Object>> getAvailabilitySlots(
            @RequestParam(value = "serviceId", required = false) String serviceId,
            @RequestParam("date") String date) {
        return ResponseEntity.ok(buildSlotsResponse(serviceId != null ? serviceId : "default", date));
    }

    private Map<String, Object> buildSlotsResponse(String serviceId, String date) {
        List<Map<String, Object>> slots = List.of(
            Map.of("id", "slot-1", "slot", "07:00 AM - 09:00 AM", "label", "07:00 AM - 09:00 AM", "isAvailable", true, "available", true, "surgeFee", 0),
            Map.of("id", "slot-2", "slot", "09:00 AM - 11:00 AM", "label", "09:00 AM – 11:00 AM", "isAvailable", true, "available", true, "surgeFee", 200),
            Map.of("id", "slot-3", "slot", "11:00 AM - 01:00 PM", "label", "11:00 AM – 01:00 PM", "isAvailable", false, "available", false, "surgeFee", 0),
            Map.of("id", "slot-4", "slot", "02:00 PM - 04:00 PM", "label", "02:00 PM – 04:00 PM", "isAvailable", true, "available", true, "surgeFee", 0),
            Map.of("id", "slot-5", "slot", "04:00 PM - 06:00 PM", "label", "04:00 PM – 06:00 PM", "isAvailable", true, "available", true, "surgeFee", 0)
        );
        Map<String, Object> resp = new java.util.LinkedHashMap<>();
        resp.put("success", true);
        if (serviceId != null) {
            resp.put("serviceId", serviceId);
        }
        resp.put("date", date);
        resp.put("slots", slots);
        return resp;
    }
}
