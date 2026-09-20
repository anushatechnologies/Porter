package com.anushaporter.backend.controller;

import com.anushaporter.backend.model.PricingVehicle;
import com.anushaporter.backend.model.Vehicle;
import com.anushaporter.backend.repository.PricingVehicleRepository;
import com.anushaporter.backend.repository.VehicleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/vehicles")
public class VehicleController {

    @Autowired
    private VehicleRepository repository;

    @Autowired
    private PricingVehicleRepository pricingVehicleRepo;

    @Autowired(required = false)
    private com.anushaporter.backend.repository.VehicleTypeRepository vehicleTypeRepository;

    @Autowired(required = false)
    private com.anushaporter.backend.repository.PassengerVehicleCategoryRepository passengerVehicleCategoryRepository;

    /**
     * GET /api/vehicles
     * If ?status=active is passed, returns active vehicle types for Driver Registration: { success: true, vehicles: [...] }.
     * Otherwise returns fleet vehicles joined with pricing configuration for User App and Admin Panel: [...].
     */
    @GetMapping
    public ResponseEntity<?> getAll(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String serviceType) {
        if ("PASSENGER".equalsIgnoreCase(serviceType)) {
            List<com.anushaporter.backend.model.PassengerVehicleCategory> categories = passengerVehicleCategoryRepository != null
                    ? passengerVehicleCategoryRepository.findAllByOrderByDisplayOrderAsc()
                    : Collections.emptyList();
            List<Map<String, Object>> items = categories.stream().map(c -> {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("id", "PV-" + c.getId());
                map.put("vehicleId", c.getCategoryCode() != null ? c.getCategoryCode().toLowerCase() : String.valueOf(c.getId()));
                map.put("name", c.getDisplayName());
                map.put("model", c.getDisplayName());
                map.put("type", c.getCategoryCode());
                map.put("serviceType", "PASSENGER");
                map.put("plate", "COMMERCIAL");
                map.put("owner", "Fleet");
                map.put("trips", 0);
                map.put("baseFare", c.getBaseFare() != null ? c.getBaseFare().doubleValue() : 50.0);
                map.put("pricePerKm", c.getPerKmRate() != null ? c.getPerKmRate().doubleValue() : 12.0);
                map.put("minFare", c.getMinimumFare() != null ? c.getMinimumFare().doubleValue() : 50.0);
                map.put("capacity", c.getPassengerCapacity() + " Passengers");
                map.put("capacityKg", c.getLuggageCapacity() != null ? c.getLuggageCapacity() * 20 : 100);
                map.put("status", Boolean.TRUE.equals(c.getActive()));
                map.put("imageUrl", c.getImageUrl() != null ? c.getImageUrl() : "");
                return map;
            }).collect(Collectors.toList());
            return ResponseEntity.ok(items);
        }
        if ("active".equalsIgnoreCase(status)) {
            List<com.anushaporter.backend.model.VehicleType> list = vehicleTypeRepository != null
                    ? vehicleTypeRepository.findByStatusOrderByPriorityAsc("active")
                    : Collections.emptyList();

            List<Map<String, Object>> vehicles = list.stream().map(v -> {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("id",          v.getId());
                map.put("name",        v.getName());
                map.put("type",        v.getType());
                map.put("typeCode",    v.getType());
                map.put("type_code",   v.getType());
                map.put("description", v.getDescription() != null ? v.getDescription() : "");
                map.put("capacity",    v.getCapacity() != null ? v.getCapacity() : "");
                map.put("capacityKg",  v.getCapacityKg() != null ? v.getCapacityKg() : 20);
                map.put("dimensions",  v.getDimensions() != null ? v.getDimensions() : "");
                String img = v.getImageUrl() != null ? v.getImageUrl().trim() : "";
                if (img.isEmpty() || img.endsWith("/vehicles/bike.png") || img.endsWith("/vehicles/auto.png")) {
                    img = VehicleTypeController.resolveDefaultVehicleImageUrl(v.getType(), v.getName(), v.getId());
                }
                map.put("imageUrl",    img);
                map.put("baseFare",    v.getBaseFare() != null ? v.getBaseFare() : 40.0);
                map.put("baseKm",      v.getBaseKm() != null ? v.getBaseKm() : 1.0);
                map.put("perKmRate",   v.getPerKmRate() != null ? v.getPerKmRate() : 12.0);
                map.put("status",      v.getStatus());
                map.put("priority",    v.getPriority() != null ? v.getPriority() : 1);
                return map;
            }).collect(Collectors.toList());

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", true);
            response.put("vehicles", vehicles);
            response.put("count", vehicles.size());
            return ResponseEntity.ok(response);
        }
        List<Vehicle> vehicles = repository.findAll();
        List<PricingVehicle> pricingList = pricingVehicleRepo.findAll();

        Map<String, PricingVehicle> pricingBySlug = new LinkedHashMap<>();
        for (PricingVehicle pv : pricingList) {
            if (pv.getVehicleId() != null) {
                pricingBySlug.put(normalizeSlug(pv.getVehicleId()), pv);
            }
            if (pv.getName() != null) {
                pricingBySlug.put(normalizeSlug(pv.getName()), pv);
            }
        }

        Set<String> matchedPricingIds = new HashSet<>();
        List<Map<String, Object>> items = new ArrayList<>();

        for (Vehicle v : vehicles) {
            String slug = resolveSlug(v);
            PricingVehicle pv = findPricingForVehicle(slug, v.getModel(), v.getType(), pricingBySlug);

            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", v.getId() != null ? "V-" + v.getId() : "V-100");
            map.put("vehicleId", pv != null && pv.getVehicleId() != null ? pv.getVehicleId() : slug);
            map.put("name", pv != null && pv.getName() != null ? pv.getName() : (v.getModel() != null ? v.getModel() : "Vehicle"));
            map.put("model", v.getModel() != null ? v.getModel() : "Commercial Truck");
            map.put("type", v.getType() != null ? v.getType() : "Commercial Truck");
            map.put("plate", v.getPlate() != null ? v.getPlate() : "");
            map.put("owner", v.getOwner() != null ? v.getOwner() : "Unassigned");
            map.put("trips", v.getTrips() != null ? v.getTrips() : 0);

            // Pricing fields
            map.put("baseFare", pv != null && pv.getBaseFare() != null ? pv.getBaseFare() : 100.0);
            map.put("pricePerKm", pv != null && pv.getPricePerKm() != null ? pv.getPricePerKm() : 15.0);
            map.put("minFare", pv != null && pv.getMinFare() != null ? pv.getMinFare() : (pv != null && pv.getBaseFare() != null ? pv.getBaseFare() : 100.0));
            map.put("maxFare", pv != null && pv.getMaxFare() != null ? pv.getMaxFare() : 5000.0);
            map.put("freeDistance", pv != null && pv.getFreeDistance() != null ? pv.getFreeDistance() : 2.0);
            map.put("minDistance", pv != null && pv.getMinDistance() != null ? pv.getMinDistance() : 2.0);
            map.put("maxDistance", pv != null && pv.getMaxDistance() != null ? pv.getMaxDistance() : 500.0);
            map.put("capacityKg", pv != null && pv.getCapacityKg() != null ? pv.getCapacityKg() : parseCapacityKg(v.getCapacity()));
            map.put("capacity", v.getCapacity() != null ? v.getCapacity() : (pv != null && pv.getCapacityKg() != null ? pv.getCapacityKg().intValue() + " kg" : "500 kg"));
            map.put("volume", pv != null && pv.getVolume() != null ? pv.getVolume() : 1.0);
            map.put("status", pv != null && pv.getStatus() != null ? pv.getStatus() : true);
            map.put("priority", pv != null && pv.getPriority() != null ? pv.getPriority() : 1);
            String img = pv != null && pv.getImageUrl() != null ? pv.getImageUrl().trim() : "";
            if (img.isEmpty() || img.endsWith("/vehicles/bike.png") || img.endsWith("/vehicles/auto.png")) {
                img = VehicleTypeController.resolveDefaultVehicleImageUrl(v.getType(), v.getModel(), v.getId() != null ? String.valueOf(v.getId()) : "");
            }
            map.put("imageUrl", img);

            if (pv != null && pv.getVehicleId() != null) {
                matchedPricingIds.add(pv.getVehicleId().toLowerCase());
            }

            items.add(map);
        }

        // Include any pricing vehicles that didn't have a physical fleet vehicle entry
        for (PricingVehicle pv : pricingList) {
            String vId = pv.getVehicleId() != null ? pv.getVehicleId().toLowerCase() : "";
            if (!matchedPricingIds.contains(vId)) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("id", pv.getId() != null ? "V-" + pv.getId() : "V-" + vId);
                map.put("vehicleId", pv.getVehicleId());
                map.put("name", pv.getName() != null ? pv.getName() : vId);
                map.put("model", pv.getName() != null ? pv.getName() : vId);
                map.put("type", pv.getName() != null ? pv.getName() : vId);
                map.put("plate", "");
                map.put("owner", "Unassigned");
                map.put("trips", 0);

                map.put("baseFare", pv.getBaseFare() != null ? pv.getBaseFare() : 100.0);
                map.put("pricePerKm", pv.getPricePerKm() != null ? pv.getPricePerKm() : 15.0);
                map.put("minFare", pv.getMinFare() != null ? pv.getMinFare() : (pv.getBaseFare() != null ? pv.getBaseFare() : 100.0));
                map.put("maxFare", pv.getMaxFare() != null ? pv.getMaxFare() : 5000.0);
                map.put("freeDistance", pv.getFreeDistance() != null ? pv.getFreeDistance() : 2.0);
                map.put("minDistance", pv.getMinDistance() != null ? pv.getMinDistance() : 2.0);
                map.put("maxDistance", pv.getMaxDistance() != null ? pv.getMaxDistance() : 500.0);
                map.put("capacityKg", pv.getCapacityKg() != null ? pv.getCapacityKg() : 500.0);
                map.put("capacity", pv.getCapacityKg() != null ? pv.getCapacityKg().intValue() + " kg" : "500 kg");
                map.put("volume", pv.getVolume() != null ? pv.getVolume() : 1.0);
                map.put("status", pv.getStatus() != null ? pv.getStatus() : true);
                map.put("priority", pv.getPriority() != null ? pv.getPriority() : 1);
                String pImg = pv.getImageUrl() != null ? pv.getImageUrl().trim() : "";
                if (pImg.isEmpty() || pImg.endsWith("/vehicles/bike.png") || pImg.endsWith("/vehicles/auto.png")) {
                    pImg = VehicleTypeController.resolveDefaultVehicleImageUrl(pv.getVehicleId(), pv.getName(), pv.getId() != null ? String.valueOf(pv.getId()) : "");
                }
                map.put("imageUrl", pImg);

                items.add(map);
            }
        }

        return ResponseEntity.ok(items);
    }

    private PricingVehicle findPricingForVehicle(String slug, String model, String type, Map<String, PricingVehicle> pricingBySlug) {
        if (pricingBySlug.containsKey(slug)) return pricingBySlug.get(slug);
        if (model != null && pricingBySlug.containsKey(normalizeSlug(model))) return pricingBySlug.get(normalizeSlug(model));
        if (type != null && pricingBySlug.containsKey(normalizeSlug(type))) return pricingBySlug.get(normalizeSlug(type));

        for (Map.Entry<String, PricingVehicle> entry : pricingBySlug.entrySet()) {
            if (slug.contains(entry.getKey()) || entry.getKey().contains(slug)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private String resolveSlug(Vehicle v) {
        String raw = v.getModel() != null ? v.getModel() : (v.getType() != null ? v.getType() : "vehicle");
        return normalizeSlug(raw);
    }

    private String normalizeSlug(String str) {
        if (str == null) return "";
        return str.toLowerCase()
                .replace(" model", "")
                .replace(" ", "-")
                .replace("_", "-")
                .trim();
    }

    private double parseCapacityKg(String capacityStr) {
        if (capacityStr == null) return 500.0;
        try {
            String digits = capacityStr.replaceAll("[^0-9.]", "").trim();
            if (!digits.isEmpty()) {
                return Double.parseDouble(digits);
            }
        } catch (Exception ignored) {}
        return 500.0;
    }

    @PostMapping
    public Vehicle create(@RequestBody Vehicle entity) {
        return repository.save(entity);
    }
}

