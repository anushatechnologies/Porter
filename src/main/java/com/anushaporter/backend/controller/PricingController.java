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

    @GetMapping("/vehicle/{vehicleId}")
    public ResponseEntity<?> getVehiclePricing(@PathVariable String vehicleId) {
        PricingVehicle vehicle = vehicleRepo.findByVehicleId(vehicleId);
        if (vehicle != null) return ResponseEntity.ok(vehicle);
        return ResponseEntity.notFound().build();
    }

    @PostMapping
    public ResponseEntity<?> addVehiclePricing(@RequestBody PricingVehicle vehicle) {
        return ResponseEntity.ok(Map.of("success", true, "vehicle", vehicleRepo.save(vehicle)));
    }

    @PutMapping("/vehicle/{vehicleId}")
    public ResponseEntity<?> updateVehiclePricing(
            @PathVariable String vehicleId, @RequestBody PricingVehicle vehicle) {
        PricingVehicle existing = vehicleRepo.findByVehicleId(vehicleId);
        if (existing != null) {
            vehicle.setId(existing.getId());
            return ResponseEntity.ok(Map.of("success", true, "vehicle", vehicleRepo.save(vehicle)));
        }
        return ResponseEntity.notFound().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteVehiclePricing(@PathVariable Long id) {
        vehicleRepo.deleteById(id);
        return ResponseEntity.ok(Map.of("success", true));
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

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
