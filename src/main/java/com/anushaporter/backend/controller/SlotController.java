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
            Map.of("id", "slot_0900", "label", "9:00 AM", "available", true),
            Map.of("id", "slot_1230", "label", "12:30 PM", "available", true),
            Map.of("id", "slot_1600", "label", "4:00 PM", "available", true),
            Map.of("id", "slot_1800", "label", "6:00 PM", "available", true)
        );

        return ResponseEntity.ok(Map.of(
            "date", date,
            "slots", slots
        ));
    }
}
