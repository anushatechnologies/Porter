package com.anushaporter.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/pricing")
public class PackerPricingController {
    @PostMapping("/packers")
    public ResponseEntity<Map<String, Object>> estimate(@RequestBody Map<String, Object> body) {
        double distance = number(body, "distanceKm", 0);
        int pickupFloor = (int) number(body, "pickupFloor", 0);
        int dropFloor = (int) number(body, "dropFloor", 0);
        boolean pickupLift = Boolean.TRUE.equals(body.get("hasElevatorPickup"));
        boolean dropLift = Boolean.TRUE.equals(body.get("hasElevatorDrop"));
        int itemCount = body.get("items") instanceof java.util.List<?> list ? list.size() : 0;
        double base = 2000, distanceFare = Math.max(0, distance) * 25;
        double labor = 500 + itemCount * 75, packing = itemCount * 125;
        double floor = Math.max(0, pickupFloor - (pickupLift ? 0 : 1)) * 100
                + Math.max(0, dropFloor - (dropLift ? 0 : 1)) * 100;
        double subtotal = base + distanceFare + labor + packing + floor;
        double gst = Math.round(subtotal * 0.18 * 100) / 100.0;
        Map<String, Object> result = new HashMap<>();
        result.put("baseFare", base); result.put("distanceFare", distanceFare);
        result.put("laborCharge", labor); result.put("packingCharge", packing);
        result.put("floorCharge", floor); result.put("gst", gst); result.put("totalFare", subtotal + gst);
        return ResponseEntity.ok(result);
    }
    private double number(Map<String, Object> body, String key, double fallback) {
        if (body == null || !body.containsKey(key) || body.get(key) == null) return fallback;
        Object value = body.get(key);
        if (value instanceof Number n) return n.doubleValue();
        if (value instanceof String s) {
            try {
                return Double.parseDouble(s.replaceAll("[^0-9.]", "").trim());
            } catch (Exception ignored) {}
        }
        return fallback;
    }
}
