package com.anushaporter.backend.controller;

import com.anushaporter.backend.repository.PorterServiceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * POST /api/pricing/packers
 * Calculates Packers & Movers dynamic quotation with comprehensive itemized breakdown.
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

        // 1. Base Transportation Fare
        double base = "intercity".equalsIgnoreCase(serviceId) ? 2499.0 : 649.0;
        double perKm = "intercity".equalsIgnoreCase(serviceId) ? 25.0 : 20.0;
        double distanceFare = Math.round(Math.max(0, distance) * perKm * 100.0) / 100.0;
        double transportationFare = Math.round((base + distanceFare) * 100.0) / 100.0;

        // 2. Items & Inventory count
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
        } else if (body.get("items") instanceof Map<?, ?> map) {
            for (Object val : map.values()) {
                if (val instanceof Number n) {
                    itemCount += n.intValue();
                } else {
                    itemCount++;
                }
            }
        }

        // 3. Labor / Worker Fare
        int workerCount = (int) number(body, "workerCount", 0);
        double laborFare;
        if (workerCount > 0) {
            laborFare = workerCount * 400.0;
        } else {
            laborFare = Math.round((200.0 + itemCount * 100.0) * 100.0) / 100.0;
        }

        // 4. Packing Fare
        String packingTier = body.get("packingTier") != null ? String.valueOf(body.get("packingTier")).toLowerCase() : "";
        double packingChargeInput = number(body, "packingCharge", -1);
        double packingFare;
        if (packingChargeInput >= 0) {
            packingFare = packingChargeInput;
        } else if (packingTier.contains("multi") || packingTier.contains("premium")) {
            packingFare = 399.0;
        } else if (packingTier.contains("single") || packingTier.contains("standard")) {
            packingFare = 199.0;
        } else {
            packingFare = 0.0;
        }

        // 5. Addons (Dismantling, Reassembly, Unpacking, etc.)
        List<String> addons = new ArrayList<>();
        if (body.get("addons") instanceof List<?> list) {
            for (Object a : list) {
                if (a != null) addons.add(String.valueOf(a).toLowerCase());
            }
        }
        boolean dismantling = addons.contains("dismantling") || addons.contains("addon_dismantling") || Boolean.TRUE.equals(body.get("dismantling"));
        boolean reassembly = addons.contains("reassembly") || addons.contains("addon_reassembly") || addons.contains("installation") || addons.contains("addon_installation") || Boolean.TRUE.equals(body.get("reassembly"));
        boolean unpacking = addons.contains("unpacking") || addons.contains("addon_unpacking") || Boolean.TRUE.equals(body.get("unpacking"));

        double dismantlingFare = dismantling ? 249.0 : 0.0;
        double reassemblyFare = reassembly ? 249.0 : 0.0;
        if (unpacking && !dismantling && !reassembly) {
            reassemblyFare = 199.0;
        }

        // 6. Floor & Handling Charges
        int pickupFloor = (int) number(body, "pickupFloor", 0);
        int dropFloor = (int) number(body, "dropFloor", 0);
        boolean pickupLift = Boolean.TRUE.equals(body.get("pickupLift")) || Boolean.TRUE.equals(body.get("hasElevatorPickup"));
        boolean dropLift = Boolean.TRUE.equals(body.get("dropLift")) || Boolean.TRUE.equals(body.get("hasElevatorDrop"));

        double handlingFare = 0.0;
        if (pickupFloor > 1 && !pickupLift) handlingFare += (pickupFloor - 1) * 100.0;
        if (dropFloor > 1 && !dropLift) handlingFare += (dropFloor - 1) * 100.0;
        if (handlingFare == 0.0 && (pickupFloor > 0 || dropFloor > 0)) {
            handlingFare = 150.0;
        }

        // 7. Subtotal
        double subtotal = transportationFare + packingFare + laborFare + dismantlingFare + reassemblyFare + handlingFare;
        subtotal = Math.round(subtotal * 100.0) / 100.0;

        // 8. Discount & Coupon code
        String couponCode = body.get("couponCode") != null ? String.valueOf(body.get("couponCode")).trim().toUpperCase() : "";
        double discount = 0.0;
        if (!couponCode.isEmpty()) {
            if ("FIRSTMOVE".equals(couponCode) || "PORTER500".equals(couponCode) || "WELCOME500".equals(couponCode)) {
                discount = Math.min(500.0, subtotal * 0.20);
            } else if ("DISCOUNT10".equals(couponCode)) {
                discount = Math.round(subtotal * 0.10 * 100.0) / 100.0;
            } else {
                discount = 200.0;
            }
        }

        double totalFare = Math.max(0.0, Math.round((subtotal - discount) * 100.0) / 100.0);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("serviceId", serviceId);
        result.put("transportationFare", transportationFare);
        result.put("packingFare", packingFare);
        result.put("laborFare", laborFare);
        result.put("dismantlingFare", dismantlingFare);
        result.put("reassemblyFare", reassemblyFare);
        result.put("handlingFare", handlingFare);
        result.put("subtotal", subtotal);
        result.put("discount", discount);
        result.put("totalFare", totalFare);

        // Backward-compatible alias fields
        result.put("baseFare", base);
        result.put("distanceFare", distanceFare);
        result.put("laborCharge", laborFare);
        result.put("packingCharge", packingFare);
        if (handlingFare > 0) result.put("floorCharge", handlingFare);

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

