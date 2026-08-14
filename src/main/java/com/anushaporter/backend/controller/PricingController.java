package com.anushaporter.backend.controller;

import com.anushaporter.backend.dto.PricingRequest;
import com.anushaporter.backend.dto.PricingResponse;
import com.anushaporter.backend.model.PricingVehicle;
import com.anushaporter.backend.repository.PricingVehicleRepository;
import com.anushaporter.backend.service.PricingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Pricing & Estimation APIs
 *
 * Customer App:
 *   POST /api/pricing/calculate     – itemised fare for one vehicle type
 *   POST /api/pricing/estimate-all  – fare + ETA for all active vehicle types
 *   GET  /api/pricing/vehicles      – list active vehicle types
 *
 * Admin Panel:
 *   GET    /api/pricing/vehicle/{vehicleId}  – get vehicle pricing config
 *   POST   /api/pricing                      – add vehicle pricing
 *   PUT    /api/pricing/vehicle/{vehicleId}  – update vehicle pricing
 *   DELETE /api/pricing/{id}                 – delete vehicle pricing
 */
@RestController
@RequestMapping("/api/pricing")
public class PricingController {

    @Autowired
    private PricingService pricingService;

    @Autowired
    private PricingVehicleRepository vehicleRepo;

    // ─── Customer-facing ─────────────────────────────────────────────────────

    /**
     * GET /api/pricing/vehicles
     * Returns all active vehicle types with their pricing config.
     */
    @GetMapping("/vehicles")
    public ResponseEntity<?> getActiveVehicles() {
        List<PricingVehicle> vehicles = vehicleRepo.findByStatus(true);
        List<Map<String, Object>> items = vehicles.stream().map(v -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("vehicleId", v.getVehicleId());
            item.put("name", v.getName() != null ? v.getName() : "");
            item.put("baseFare", v.getBaseFare() != null ? v.getBaseFare() : 0.0);
            item.put("pricePerKm", v.getPricePerKm() != null ? v.getPricePerKm() : 0.0);
            item.put("capacityKg", v.getCapacityKg() != null ? v.getCapacityKg() : 0.0);
            item.put("iconUrl", v.getImageUrl() != null ? v.getImageUrl() : (v.getIcon() != null ? v.getIcon() : ""));
            item.put("minFare", v.getMinFare() != null ? v.getMinFare() : 0.0);
            return item;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(Map.of("success", true, "vehicles", items));
    }

    /**
     * POST /api/pricing/calculate
     * Calculates real-time itemised price for a given vehicle + distance + helpers.
     *
     * Request body:
     * {
     *   "vehicleId":   "tata-ace",
     *   "distanceKm":  5.2,
     *   "helperCount": 1,          // optional
     *   "weightKg":    200.0,      // optional
     *   "waitingMins": 0.0,        // optional
     *   "tollCharge":  0.0         // optional
     * }
     */
    @PostMapping("/calculate")
    public ResponseEntity<Map<String, Object>> calculatePricing(@RequestBody PricingRequest request) {
        try {
            PricingResponse calc = pricingService.calculatePricing(request);

            Map<String, Object> breakdown = new LinkedHashMap<>();
            breakdown.put("baseFare",         calc.getBaseFare());
            breakdown.put("distanceFare",     calc.getDistanceFare());
            breakdown.put("helperCharge",     calc.getHelperCharge());
            breakdown.put("helperChargePerHead", calc.getHelperChargePerHead());
            breakdown.put("helperCount",      calc.getHelperCount());
            breakdown.put("weightCharge",     calc.getWeightCharge());
            breakdown.put("waitingCharge",    calc.getWaitingCharge());
            breakdown.put("tollCharge",       calc.getTollCharge());
            breakdown.put("fuelCharge",       calc.getFuelCharge());
            breakdown.put("platformFee",      calc.getPlatformFee());
            breakdown.put("discount",         calc.getDiscount());
            breakdown.put("gst",              calc.getGst());
            breakdown.put("gstRate",          calc.getGstRate());
            breakdown.put("totalFare",        calc.getTotalFare());

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success",     true);
            response.put("vehicleId",   calc.getVehicleId());
            response.put("vehicleName", calc.getVehicleName());
            response.put("distanceKm",  calc.getDistanceKm());
            response.put("currency",    "INR");
            response.put("breakdown",   breakdown);
            response.put("totalFare",   calc.getTotalFare());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "Pricing calculation failed: " + e.getMessage()
            ));
        }
    }

    /**
     * POST /api/pricing/estimate-all
     * Returns price estimate + ETA for all active vehicle types simultaneously.
     *
     * Request body: { "distanceKm": 5.2, "helperCount": 1 }
     */
    @PostMapping("/estimate-all")
    public ResponseEntity<Map<String, Object>> estimateAll(@RequestBody PricingRequest request) {
        try {
            List<PricingVehicle> vehicles = vehicleRepo.findByStatus(true);

            // Fallback defaults if no vehicles seeded
            if (vehicles.isEmpty()) {
                vehicles = List.of(
                        mockVehicle("2-wheeler",  "2 Wheeler",    40.0,  10.0),
                        mockVehicle("mini-truck", "Mini Truck",  200.0,  15.0),
                        mockVehicle("full-truck", "Full Truck",  500.0,  20.0)
                );
            }

            List<Map<String, Object>> estimates = new ArrayList<>();
            int etaBase = 5; // base ETA in minutes, increases per vehicle size

            for (PricingVehicle v : vehicles) {
                PricingRequest req = cloneRequest(request, v.getVehicleId());
                PricingResponse calc = pricingService.calculatePricing(req);

                Map<String, Object> item = new LinkedHashMap<>();
                item.put("vehicleId",      v.getVehicleId());
                item.put("vehicleName",    v.getName() != null ? v.getName() : v.getVehicleId());
                item.put("estimatedPrice", calc.getTotalFare());
                item.put("baseFare",       calc.getBaseFare());
                item.put("distanceFare",   calc.getDistanceFare());
                item.put("helperCharge",   calc.getHelperCharge());
                item.put("gst",            calc.getGst());
                item.put("etaMinutes",     etaBase);
                item.put("currency",       "INR");
                item.put("iconUrl",
                        v.getImageUrl() != null ? v.getImageUrl() :
                        v.getIcon() != null ? v.getIcon() : "");
                item.put("capacityKg",     v.getCapacityKg() != null ? v.getCapacityKg() : 0.0);
                estimates.add(item);

                etaBase += 3; // each larger vehicle has slightly longer ETA
            }

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "distanceKm", request.getDistanceKm() != null ? request.getDistanceKm() : 0.0,
                    "estimates", estimates
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "Estimation failed: " + e.getMessage()
            ));
        }
    }

    // ─── Admin-facing ─────────────────────────────────────────────────────────

    @GetMapping({"/vehicle/{vehicleId}", "/vehicles/{vehicleId}"})
    public ResponseEntity<?> getVehiclePricing(@PathVariable String vehicleId) {
        PricingVehicle vehicle = findVehicleByIdOrVehicleId(vehicleId);
        if (vehicle != null) {
            return ResponseEntity.ok(vehicle);
        }
        return ResponseEntity.status(404).body(Map.of("success", false, "message", "Vehicle pricing not found for: " + vehicleId));
    }

    @PostMapping({"", "/", "/vehicle", "/vehicles"})
    public ResponseEntity<?> addVehiclePricing(@RequestBody Map<String, Object> payload) {
        String vId = parseString(payload, "vehicleId", "vehicle_id");
        if (vId == null || vId.isBlank()) {
            String name = parseString(payload, "name", "vehicleName");
            vId = name != null ? name.toLowerCase().replaceAll("[^a-z0-9]+", "-") : "vehicle-" + System.currentTimeMillis();
        }

        PricingVehicle vehicle = findVehicleByIdOrVehicleId(vId);
        if (vehicle == null) {
            vehicle = new PricingVehicle();
            vehicle.setVehicleId(vId);
        }

        mapPricingVehicleFromPayload(payload, vehicle);
        if (vehicle.getVehicleId() == null || vehicle.getVehicleId().isBlank()) {
            vehicle.setVehicleId(vId);
        }

        PricingVehicle saved = vehicleRepo.save(vehicle);
        return ResponseEntity.ok(Map.of("success", true, "vehicle", saved));
    }

    @PutMapping({"/vehicle/{vehicleId}", "/vehicles/{vehicleId}", "/{vehicleId}"})
    public ResponseEntity<?> updateVehiclePricing(
            @PathVariable String vehicleId, @RequestBody Map<String, Object> payload) {
        PricingVehicle vehicle = findVehicleByIdOrVehicleId(vehicleId);
        if (vehicle == null) {
            vehicle = new PricingVehicle();
            vehicle.setVehicleId(vehicleId);
        }

        mapPricingVehicleFromPayload(payload, vehicle);
        if (vehicle.getVehicleId() == null || vehicle.getVehicleId().isBlank()) {
            vehicle.setVehicleId(vehicleId);
        }

        PricingVehicle saved = vehicleRepo.save(vehicle);
        return ResponseEntity.ok(Map.of("success", true, "vehicle", saved));
    }

    @DeleteMapping({"/{vehicleId}", "/vehicle/{vehicleId}", "/vehicles/{vehicleId}"})
    public ResponseEntity<?> deleteVehiclePricing(@PathVariable String vehicleId) {
        PricingVehicle vehicle = findVehicleByIdOrVehicleId(vehicleId);
        if (vehicle == null) {
            return ResponseEntity.status(404).body(Map.of("success", false, "message", "Vehicle pricing not found for: " + vehicleId));
        }
        vehicleRepo.delete(vehicle);
        return ResponseEntity.ok(Map.of("success", true, "message", "Vehicle pricing deleted successfully"));
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private PricingVehicle findVehicleByIdOrVehicleId(String vehicleId) {
        if (vehicleId == null || vehicleId.isBlank()) return null;

        String clean = vehicleId.trim();
        PricingVehicle v = vehicleRepo.findFirstByVehicleIdIgnoreCase(clean).orElse(null);
        if (v != null) return v;

        v = vehicleRepo.findByVehicleId(clean);
        if (v != null) return v;

        if (clean.matches("^\\d+$")) {
            try {
                Long id = Long.parseLong(clean);
                return vehicleRepo.findById(id).orElse(null);
            } catch (Exception ignored) {}
        }

        for (PricingVehicle existing : vehicleRepo.findAll()) {
            if (existing.getVehicleId() != null && existing.getVehicleId().equalsIgnoreCase(clean)) return existing;
            if (existing.getName() != null && existing.getName().equalsIgnoreCase(clean)) return existing;
        }

        return null;
    }

    private void mapPricingVehicleFromPayload(Map<String, Object> payload, PricingVehicle target) {
        if (payload == null || target == null) return;

        String vehicleId = parseString(payload, "vehicleId", "vehicle_id");
        if (vehicleId != null && !vehicleId.isBlank()) target.setVehicleId(vehicleId);

        String name = parseString(payload, "name", "vehicleName", "vehicle_name");
        if (name != null) target.setName(name);

        Double baseFare = parseDouble(payload, "baseFare", "base_fare");
        if (baseFare != null) target.setBaseFare(baseFare);

        Double pricePerKm = parseDouble(payload, "pricePerKm", "price_per_km", "perKmPrice", "per_km_price");
        if (pricePerKm != null) target.setPricePerKm(pricePerKm);

        Double minFare = parseDouble(payload, "minFare", "min_fare");
        if (minFare != null) target.setMinFare(minFare);

        Double maxFare = parseDouble(payload, "maxFare", "max_fare");
        if (maxFare != null) target.setMaxFare(maxFare);

        Double freeDistance = parseDouble(payload, "freeDistance", "free_distance");
        if (freeDistance != null) target.setFreeDistance(freeDistance);

        Double minDistance = parseDouble(payload, "minDistance", "min_distance");
        if (minDistance != null) target.setMinDistance(minDistance);

        Double maxDistance = parseDouble(payload, "maxDistance", "max_distance");
        if (maxDistance != null) target.setMaxDistance(maxDistance);

        Double capacityKg = parseDouble(payload, "capacityKg", "capacity_kg", "capacity");
        if (capacityKg != null) target.setCapacityKg(capacityKg);

        Double volume = parseDouble(payload, "volume");
        if (volume != null) target.setVolume(volume);

        Boolean status = parseBoolean(payload, "status", "isActive", "is_active");
        if (status != null) target.setStatus(status);
        else if (target.getStatus() == null) target.setStatus(true);

        Integer priority = parseInteger(payload, "priority");
        if (priority != null) target.setPriority(priority);
        else if (target.getPriority() == null) target.setPriority(1);

        Double commission = parseDouble(payload, "commissionPercentage", "commission_percentage", "commission");
        if (commission != null) target.setCommissionPercentage(commission);

        Double gst = parseDouble(payload, "gstPercentage", "gst_percentage", "gst");
        if (gst != null) target.setGstPercentage(gst);

        String icon = parseString(payload, "icon", "iconUrl", "icon_url");
        if (icon != null) target.setIcon(icon);

        String imageUrl = parseString(payload, "imageUrl", "image_url", "image");
        if (imageUrl != null) target.setImageUrl(imageUrl);

        String description = parseString(payload, "description");
        if (description != null) target.setDescription(description);
    }

    private String parseString(Map<String, Object> map, String... keys) {
        for (String k : keys) {
            if (map.containsKey(k) && map.get(k) != null) {
                String s = String.valueOf(map.get(k)).trim();
                if (!s.isEmpty()) return s;
            }
        }
        return null;
    }

    private Double parseDouble(Map<String, Object> map, String... keys) {
        for (String k : keys) {
            if (map.containsKey(k) && map.get(k) != null) {
                try {
                    return Double.parseDouble(String.valueOf(map.get(k)).trim());
                } catch (Exception ignored) {}
            }
        }
        return null;
    }

    private Integer parseInteger(Map<String, Object> map, String... keys) {
        for (String k : keys) {
            if (map.containsKey(k) && map.get(k) != null) {
                try {
                    return Integer.parseInt(String.valueOf(map.get(k)).trim());
                } catch (Exception ignored) {}
            }
        }
        return null;
    }

    private Boolean parseBoolean(Map<String, Object> map, String... keys) {
        for (String k : keys) {
            if (map.containsKey(k) && map.get(k) != null) {
                Object v = map.get(k);
                if (v instanceof Boolean) return (Boolean) v;
                String s = String.valueOf(v).trim().toLowerCase();
                if (s.equals("true") || s.equals("1") || s.equals("yes") || s.equals("active")) return true;
                if (s.equals("false") || s.equals("0") || s.equals("no") || s.equals("inactive")) return false;
            }
        }
        return null;
    }

    private PricingRequest cloneRequest(PricingRequest src, String vehicleId) {
        PricingRequest r = new PricingRequest();
        r.setVehicleId(vehicleId);
        r.setDistanceKm(src.getDistanceKm());
        r.setWeightKg(src.getWeightKg());
        r.setHelperCount(src.getHelperCount());
        r.setPickupLat(src.getPickupLat());
        r.setPickupLng(src.getPickupLng());
        r.setDropLat(src.getDropLat());
        r.setDropLng(src.getDropLng());
        r.setWaitingMins(src.getWaitingMins());
        r.setTollCharge(src.getTollCharge());
        return r;
    }

    private PricingVehicle mockVehicle(String id, String name, double base, double perKm) {
        PricingVehicle v = new PricingVehicle();
        v.setVehicleId(id);
        v.setName(name);
        v.setBaseFare(base);
        v.setPricePerKm(perKm);
        v.setStatus(true);
        return v;
    }
}
