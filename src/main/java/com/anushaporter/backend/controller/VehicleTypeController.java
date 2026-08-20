package com.anushaporter.backend.controller;

import com.anushaporter.backend.model.VehicleType;
import com.anushaporter.backend.repository.VehicleTypeRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Dynamic Vehicle Types Controller
 * Provides endpoints for Driver App (onboarding), Customer App (booking/pricing), and Admin Web Dashboard.
 *
 * Endpoints:
 * - GET    /api/vehicle-types?status=active      — Fetch active vehicles for Driver & User apps
 * - GET    /api/admin/vehicle-types              — Fetch all vehicles (active + inactive) for Admin
 * - POST   /api/admin/vehicle-types              — Create/update vehicle category
 * - PUT    /api/admin/vehicle-types/{id}         — Update vehicle category pricing/details
 * - PATCH  /api/admin/vehicle-types/{id}/status  — Toggle/set active / inactive status
 * - DELETE /api/admin/vehicle-types/{id}         — Soft-delete / deactivate vehicle category
 */
@RestController
@RequestMapping({
        "/api/vehicle-types",
        "/api/admin/vehicle-types",
        "/api/driver/vehicles",
        "/api/drivers/vehicles",
        "/api/vehicles/types"
})
public class VehicleTypeController {

    @Autowired
    private VehicleTypeRepository vehicleTypeRepository;

    @PostConstruct
    public void seedDefaultVehicleTypes() {
        if (vehicleTypeRepository.count() == 0) {
            List<VehicleType> defaults = Arrays.asList(
                    build("1", "2 Wheeler", "two_wheeler", "Best for documents & small packages",
                            "Load: Up to 20kg", 20, "Ideal for documents & food parcels",
                            "bike", "https://poteranusha.s3.amazonaws.com/vehicles/bike.png",
                            40.00, 1.0, 12.00, 1),

                    build("2", "3 Wheeler / Auto", "auto_rickshaw", "Best for mid-size goods and cartons",
                            "Load: Up to 500kg", 500, "5ft x 3.5ft x 3.5ft",
                            "rickshaw", "https://poteranusha.s3.amazonaws.com/vehicles/auto.png",
                            120.00, 1.0, 20.00, 2),

                    build("3", "Tata Ace", "tata_ace", "Best for large boxes and business deliveries",
                            "Load: Up to 750kg", 750, "7ft x 4ft x 5ft",
                            "truck-delivery", "https://poteranusha.s3.amazonaws.com/vehicles/tata_ace.png",
                            250.00, 1.0, 30.00, 3),

                    build("4", "Pickup 8ft", "pickup_8ft", "Heavy duty transport for bulky goods",
                            "Load: Up to 1200kg", 1200, "8ft x 4.8ft x 5ft",
                            "pickup", "https://poteranusha.s3.amazonaws.com/vehicles/pickup.png",
                            350.00, 1.0, 35.00, 4),

                    build("5", "Tata 407", "tata_407", "Commercial heavy goods and shifting transport",
                            "Load: Up to 2500kg", 2500, "10ft x 6ft x 6ft",
                            "truck", "https://poteranusha.s3.amazonaws.com/vehicles/truck.png",
                            600.00, 1.0, 50.00, 5)
            );
            vehicleTypeRepository.saveAll(defaults);
            System.out.println("[VehicleType] Seeded " + defaults.size() + " default vehicle types with dynamic pricing.");
        }
    }

    private VehicleType build(String id, String name, String type, String description,
                              String capacity, int capacityKg, String dimensions,
                              String iconName, String imageUrl,
                              double baseFare, double baseKm, double perKmRate, int priority) {
        VehicleType v = new VehicleType();
        v.setId(id);
        v.setName(name);
        v.setType(type);
        v.setDescription(description);
        v.setCapacity(capacity);
        v.setCapacityKg(capacityKg);
        v.setDimensions(dimensions);
        v.setIconName(iconName);
        v.setImageUrl(imageUrl);
        v.setBaseFare(baseFare);
        v.setBaseKm(baseKm);
        v.setPerKmRate(perKmRate);
        v.setStatus("active");
        v.setPriority(priority);
        return v;
    }

    /**
     * GET /api/vehicle-types
     * GET /api/vehicle-types?status=active
     * GET /api/admin/vehicle-types
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getVehicleTypes(
            @RequestParam(required = false) String status) {

        List<VehicleType> list;
        if ("active".equalsIgnoreCase(status)) {
            list = vehicleTypeRepository.findByStatusOrderByPriorityAsc("active");
        } else {
            list = vehicleTypeRepository.findAllByOrderByPriorityAsc();
        }

        List<Map<String, Object>> vehicles = list.stream()
                .map(this::formatVehicleType)
                .collect(Collectors.toList());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("vehicles", vehicles);
        response.put("count", vehicles.size());
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/vehicle-types/{id}
     * GET /api/admin/vehicle-types/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getVehicleTypeById(@PathVariable String id) {
        Optional<VehicleType> opt = vehicleTypeRepository.findById(id);
        if (opt.isEmpty()) {
            opt = vehicleTypeRepository.findByType(id);
        }
        if (opt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("success", false, "message", "Vehicle type not found: " + id));
        }
        return ResponseEntity.ok(Map.of("success", true, "vehicle", formatVehicleType(opt.get())));
    }

    /**
     * POST /api/vehicle-types
     * POST /api/admin/vehicle-types
     * Admin creates or updates a vehicle category.
     */
    @PostMapping
    public ResponseEntity<?> createOrUpdate(@RequestBody Map<String, Object> body) {
        try {
            String rawId = body.get("id") != null ? String.valueOf(body.get("id")).trim() : null;
            String id = (rawId != null && !rawId.isEmpty()) ? rawId : String.valueOf(System.currentTimeMillis());

            VehicleType v = vehicleTypeRepository.findById(id).orElse(new VehicleType());
            v.setId(id);

            populateVehicleTypeFields(v, body);
            VehicleType saved = vehicleTypeRepository.save(v);

            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("success", true);
            resp.put("message", "Vehicle category saved successfully");
            resp.put("id", saved.getId());
            resp.put("vehicle", formatVehicleType(saved));

            return ResponseEntity.status(HttpStatus.CREATED).body(resp);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "message", "Failed to save vehicle category: " + e.getMessage()
            ));
        }
    }

    /**
     * PUT /api/vehicle-types/{id}
     * PUT /api/admin/vehicle-types/{id}
     * Admin updates a vehicle category pricing/details.
     */
    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable String id, @RequestBody Map<String, Object> body) {
        Optional<VehicleType> opt = vehicleTypeRepository.findById(id);
        if (opt.isEmpty()) {
            opt = vehicleTypeRepository.findByType(id);
        }
        if (opt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "success", false,
                    "message", "Vehicle type not found: " + id
            ));
        }

        VehicleType v = opt.get();
        populateVehicleTypeFields(v, body);
        VehicleType saved = vehicleTypeRepository.save(v);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("success", true);
        resp.put("message", "Vehicle category updated successfully");
        resp.put("id", saved.getId());
        resp.put("vehicle", formatVehicleType(saved));

        return ResponseEntity.ok(resp);
    }

    /**
     * PATCH /api/vehicle-types/{id}/status
     * PATCH /api/admin/vehicle-types/{id}/status
     * Toggle or update active / inactive status.
     */
    @PatchMapping("/{id}/status")
    public ResponseEntity<?> updateStatus(@PathVariable String id, @RequestBody(required = false) Map<String, Object> body) {
        Optional<VehicleType> opt = vehicleTypeRepository.findById(id);
        if (opt.isEmpty()) {
            opt = vehicleTypeRepository.findByType(id);
        }
        if (opt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "success", false,
                    "message", "Vehicle type not found: " + id
            ));
        }

        VehicleType v = opt.get();
        if (body != null && body.containsKey("status")) {
            v.setStatus(String.valueOf(body.get("status")).trim().toLowerCase());
        } else {
            v.setStatus("active".equalsIgnoreCase(v.getStatus()) ? "inactive" : "active");
        }
        vehicleTypeRepository.save(v);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Vehicle category status updated",
                "id", v.getId(),
                "status", v.getStatus()
        ));
    }

    /**
     * DELETE /api/vehicle-types/{id}
     * DELETE /api/admin/vehicle-types/{id}
     * Soft-delete: sets status=inactive.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable String id) {
        Optional<VehicleType> opt = vehicleTypeRepository.findById(id);
        if (opt.isEmpty()) {
            opt = vehicleTypeRepository.findByType(id);
        }
        if (opt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "success", false,
                    "message", "Vehicle type not found: " + id
            ));
        }

        VehicleType v = opt.get();
        v.setStatus("inactive");
        vehicleTypeRepository.save(v);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Vehicle category disabled (soft-deleted)",
                "id", v.getId()
        ));
    }

    private void populateVehicleTypeFields(VehicleType v, Map<String, Object> body) {
        if (body.get("name") != null) v.setName(String.valueOf(body.get("name")).trim());
        if (body.get("type") != null) v.setType(String.valueOf(body.get("type")).trim());
        if (body.get("typeCode") != null) v.setType(String.valueOf(body.get("typeCode")).trim());
        if (body.get("type_code") != null) v.setType(String.valueOf(body.get("type_code")).trim());

        if (body.get("description") != null) v.setDescription(String.valueOf(body.get("description")).trim());
        if (body.get("capacity") != null) v.setCapacity(String.valueOf(body.get("capacity")).trim());

        if (body.get("capacityKg") != null) {
            v.setCapacityKg(parseInteger(body.get("capacityKg")));
        } else if (body.get("capacity_kg") != null) {
            v.setCapacityKg(parseInteger(body.get("capacity_kg")));
        }

        if (body.get("dimensions") != null) v.setDimensions(String.valueOf(body.get("dimensions")).trim());

        if (body.get("iconName") != null) {
            v.setIconName(String.valueOf(body.get("iconName")).trim());
        } else if (body.get("icon_name") != null) {
            v.setIconName(String.valueOf(body.get("icon_name")).trim());
        }

        if (body.get("imageUrl") != null) {
            v.setImageUrl(String.valueOf(body.get("imageUrl")).trim());
        } else if (body.get("image_url") != null) {
            v.setImageUrl(String.valueOf(body.get("image_url")).trim());
        }

        if (body.get("baseFare") != null) {
            v.setBaseFare(parseDouble(body.get("baseFare")));
        } else if (body.get("base_fare") != null) {
            v.setBaseFare(parseDouble(body.get("base_fare")));
        }

        if (body.get("baseKm") != null) {
            v.setBaseKm(parseDouble(body.get("baseKm")));
        } else if (body.get("base_km") != null) {
            v.setBaseKm(parseDouble(body.get("base_km")));
        }

        if (body.get("perKmRate") != null) {
            v.setPerKmRate(parseDouble(body.get("perKmRate")));
        } else if (body.get("per_km_rate") != null) {
            v.setPerKmRate(parseDouble(body.get("per_km_rate")));
        }

        if (body.get("status") != null) v.setStatus(String.valueOf(body.get("status")).trim().toLowerCase());
        if (body.get("priority") != null) v.setPriority(parseInteger(body.get("priority")));
    }

    private Map<String, Object> formatVehicleType(VehicleType v) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", v.getId());
        map.put("name", v.getName() != null ? v.getName() : "");
        map.put("type", v.getType() != null ? v.getType() : "");
        map.put("typeCode", v.getType() != null ? v.getType() : "");
        map.put("type_code", v.getType() != null ? v.getType() : "");
        map.put("description", v.getDescription() != null ? v.getDescription() : "");
        map.put("capacity", v.getCapacity() != null ? v.getCapacity() : "");
        map.put("capacityKg", v.getCapacityKg() != null ? v.getCapacityKg() : 20);
        map.put("dimensions", v.getDimensions() != null ? v.getDimensions() : "");
        map.put("iconName", v.getIconName() != null ? v.getIconName() : "bike");
        map.put("imageUrl", v.getImageUrl() != null ? v.getImageUrl() : "");
        map.put("baseFare", v.getBaseFare() != null ? v.getBaseFare() : 40.0);
        map.put("baseKm", v.getBaseKm() != null ? v.getBaseKm() : 1.0);
        map.put("perKmRate", v.getPerKmRate() != null ? v.getPerKmRate() : 12.0);
        map.put("status", v.getStatus() != null ? v.getStatus() : "active");
        map.put("priority", v.getPriority() != null ? v.getPriority() : 1);
        return map;
    }

    private Integer parseInteger(Object val) {
        if (val == null) return null;
        try {
            if (val instanceof Number) return ((Number) val).intValue();
            return Integer.parseInt(String.valueOf(val).replaceAll("[^0-9-]", "").trim());
        } catch (Exception e) {
            return 1;
        }
    }

    private Double parseDouble(Object val) {
        if (val == null) return null;
        try {
            if (val instanceof Number) return ((Number) val).doubleValue();
            return Double.parseDouble(String.valueOf(val).replaceAll("[^0-9.-]", "").trim());
        } catch (Exception e) {
            return 0.0;
        }
    }
}
