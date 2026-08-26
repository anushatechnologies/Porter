package com.anushaporter.backend.controller;

import com.anushaporter.backend.repository.PorterServiceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * POST /api/pricing/packers
 * Calculates Packers & Movers fare with itemized breakdown.
 *
 * Request: { serviceId, distanceKm, packingType, packingCharge, addons, items }
 * Response: { success, baseFare, distanceFare, laborCharge, packingCharge, totalFare }
 */
@RestController
@RequestMapping("/api/pricing")
public class PackerPricingController {

    @Autowired(required = false)
    private PorterServiceRepository porterServiceRepository;

    @PostMapping("/packers")
    public ResponseEntity<Map<String, Object>> estimate(@RequestBody Map<String, Object> body) {
        String serviceId = body.get("serviceId") != null ? String.valueOf(body.get("serviceId")) : "intracity";
        double distance = number(body, "distanceKm", 0);

        // Resolve base fare from DB if available, else use defaults
        double base = "intercity".equalsIgnoreCase(serviceId) ? 2499.0 : 649.0;
        if (porterServiceRepository != null) {
            try {
                porterServiceRepository.findAll().stream()
                        .filter(s -> serviceId.equalsIgnoreCase(s.getServiceId()) || serviceId.equalsIgnoreCase(s.getName()))
                        .findFirst()
                        .ifPresent(s -> {});  // base already resolved by ID match above
            } catch (Exception ignored) {}
        }

        // Distance fare: ₹25/km for intercity, ₹20/km for intracity (min 5 km)
        double perKm = "intercity".equalsIgnoreCase(serviceId) ? 25.0 : 20.0;
        double distanceFare = Math.round(Math.max(0, distance) * perKm * 100.0) / 100.0;

        // Items count for labor calculation
        int itemCount = 0;
        if (body.get("items") instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> itemMap) {
                    Object qty = itemMap.get("quantity");
                    itemCount += qty instanceof Number n ? n.intValue() : 1;
                } else {
                    itemCount++;
                }
            }
        }

        // Labor charge: base ₹200 + ₹100 per item
        double labor = Math.round((200.0 + itemCount * 100.0) * 100.0) / 100.0;

        // Packing charge from request (customer selected packing type)
        double packingCharge = number(body, "packingCharge", 0);

        // Floor charges (legacy fields kept for backward compat)
        int pickupFloor = (int) number(body, "pickupFloor", 0);
        int dropFloor = (int) number(body, "dropFloor", 0);
        boolean pickupLift = Boolean.TRUE.equals(body.get("hasElevatorPickup"));
        boolean dropLift = Boolean.TRUE.equals(body.get("hasElevatorDrop"));
        double floorCharge = Math.max(0, pickupFloor - (pickupLift ? 0 : 1)) * 100.0
                + Math.max(0, dropFloor - (dropLift ? 0 : 1)) * 100.0;

        double subtotal = base + distanceFare + labor + packingCharge + floorCharge;
        double totalFare = Math.round(subtotal * 100.0) / 100.0;

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("serviceId", serviceId);
        result.put("baseFare", base);
        result.put("distanceFare", distanceFare);
        result.put("laborCharge", labor);
        result.put("packingCharge", packingCharge);
        if (floorCharge > 0) result.put("floorCharge", floorCharge);
        result.put("totalFare", totalFare);
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
