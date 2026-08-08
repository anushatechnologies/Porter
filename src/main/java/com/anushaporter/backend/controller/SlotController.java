package com.anushaporter.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/slots")
public class SlotController {

    @GetMapping
    public ResponseEntity<Map<String, Object>> getAvailabilitySlots(
            @RequestParam(value = "serviceId", required = false) String serviceId,
            @RequestParam("date") String date) {
        
        List<Map<String, Object>> slots = List.of(
            Map.of("id", "slot-0900", "label", "09:00 AM - 11:00 AM", "available", true),
            Map.of("id", "slot-1400", "label", "02:00 PM - 04:00 PM", "available", true),
            Map.of("id", "slot-1800", "label", "06:00 PM - 08:00 PM", "available", true)
        );

        return ResponseEntity.ok(Map.of(
            "date", date,
            "slots", slots
        ));
    }
}
