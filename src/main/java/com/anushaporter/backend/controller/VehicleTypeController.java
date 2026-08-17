package com.anushaporter.backend.controller;

import com.anushaporter.backend.model.VehicleType;
import com.anushaporter.backend.repository.VehicleTypeRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Vehicle Type API — used by the Driver App registration screen.
 *
 * GET  /api/vehicle-types           — all vehicle types (admin view)
 * GET  /api/vehicle-types?status=active — active only (driver app)
 * POST /api/vehicle-types           — create/update a vehicle type (admin)
 * PUT  /api/vehicle-types/{id}      — update a vehicle type (admin)
 * DELETE /api/vehicle-types/{id}    — soft-delete (sets status=inactive)
 *
 * Driver Registration also uses this via DriverAPIController:
 *   vehicleId in POST /api/drivers/register is validated against this table.
 */
@RestController
@RequestMapping("/api/vehicle-types")
public class VehicleTypeController {

    @Autowired
    private VehicleTypeRepository vehicleTypeRepository;

    // ─────────────────────────────────────────────────────────────────────────
    // Seed default vehicle types on first startup (if table is empty)
    // Admin can override these via POST /api/vehicle-types
    // ─────────────────────────────────────────────────────────────────────────
    @PostConstruct
    public void seedDefaultVehicleTypes() {
        if (vehicleTypeRepository.count() == 0) {
            List<VehicleType> defaults = Arrays.asList(
                build("veh_bike_01",       "Bike",           "bike",             "Load: Up to 20kg",   1),
                build("veh_scooter_02",    "Scooter",        "scooter",          "Load: Up to 15kg",   2),
                build("veh_auto_03",       "Auto",           "rickshaw",         "Load: Up to 120kg",  3),
                build("veh_pickup_04",     "Pickup",         "pickup",           "Load: Up to 300kg",  4),
                build("veh_minitruck_05",  "Mini Truck",     "truck-delivery",   "Load: Up to 600kg",  5),
                build("veh_tataace_06",    "Tata Ace",       "tata-ace",         "Load: Up to 750kg",  6),
                build("veh_407_07",        "407 Truck",      "truck",            "Load: Up to 2000kg", 7),
                build("veh_lpt1109_08",    "LPT 1109",       "heavy-truck",      "Load: Up to 5000kg", 8)
            );
            vehicleTypeRepository.saveAll(defaults);
            System.out.println("[VehicleType] Seeded " + defaults.size() + " default vehicle types.");
        }
    }

    private VehicleType build(String id, String name, String type, String capacity, int priority) {
        VehicleType v = new VehicleType();
        v.setId(id);
        v.setName(name);
        v.setType(type);
        v.setCapacity(capacity);
        v.setStatus("active");
        v.setPriority(priority);
        return v;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/vehicle-types?status=active
    //
    // Driver App calls this on registration screen to populate the vehicle
    // type dropdown dynamically. Admin controls the list via the portal.
    // ─────────────────────────────────────────────────────────────────────────
    @GetMapping
    public ResponseEntity<Map<String, Object>> getVehicleTypes(
            @RequestParam(required = false) String status) {

        List<VehicleType> list;
        if ("active".equalsIgnoreCase(status)) {
            list = vehicleTypeRepository.findByStatusOrderByPriorityAsc("active");
        } else {
            list = vehicleTypeRepository.findAll()
                    .stream()
                    .sorted(Comparator.comparingInt(v -> v.getPriority() != null ? v.getPriority() : 99))
                    .collect(Collectors.toList());
        }

        List<Map<String, Object>> vehicles = list.stream().map(v -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id",       v.getId());
            map.put("name",     v.getName());
            map.put("type",     v.getType());
            map.put("capacity", v.getCapacity() != null ? v.getCapacity() : "");
            map.put("status",   v.getStatus());
            map.put("priority", v.getPriority() != null ? v.getPriority() : 1);
            if (v.getImageUrl() != null) map.put("imageUrl", v.getImageUrl());
            return map;
        }).collect(Collectors.toList());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("vehicles", vehicles);
        response.put("count", vehicles.size());
        return ResponseEntity.ok(response);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POST /api/vehicle-types
    // Admin creates or updates a vehicle type.
    // ─────────────────────────────────────────────────────────────────────────
    @PostMapping
    public ResponseEntity<Map<String, Object>> createOrUpdate(
            @RequestBody Map<String, Object> body) {
        try {
            String id = body.get("id") != null
                    ? String.valueOf(body.get("id"))
                    : "veh_" + System.currentTimeMillis();

            VehicleType v = vehicleTypeRepository.findById(id).orElse(new VehicleType());
            v.setId(id);
            if (body.get("name") != null)     v.setName(String.valueOf(body.get("name")));
            if (body.get("type") != null)     v.setType(String.valueOf(body.get("type")));
            if (body.get("capacity") != null) v.setCapacity(String.valueOf(body.get("capacity")));
            if (body.get("status") != null)   v.setStatus(String.valueOf(body.get("status")));
            if (body.get("imageUrl") != null) v.setImageUrl(String.valueOf(body.get("imageUrl")));
            if (body.get("priority") != null) v.setPriority(((Number) body.get("priority")).intValue());

            vehicleTypeRepository.save(v);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Vehicle type saved successfully",
                    "id", v.getId()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "Failed to save vehicle type: " + e.getMessage()
            ));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PUT /api/vehicle-types/{id}
    // Admin updates a vehicle type (e.g. enable/disable, rename).
    // ─────────────────────────────────────────────────────────────────────────
    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> update(
            @PathVariable String id,
            @RequestBody Map<String, Object> body) {
        Optional<VehicleType> opt = vehicleTypeRepository.findById(id);
        if (opt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of(
                    "success", false, "message", "Vehicle type not found: " + id));
        }

        VehicleType v = opt.get();
        if (body.get("name") != null)     v.setName(String.valueOf(body.get("name")));
        if (body.get("type") != null)     v.setType(String.valueOf(body.get("type")));
        if (body.get("capacity") != null) v.setCapacity(String.valueOf(body.get("capacity")));
        if (body.get("status") != null)   v.setStatus(String.valueOf(body.get("status")));
        if (body.get("imageUrl") != null) v.setImageUrl(String.valueOf(body.get("imageUrl")));
        if (body.get("priority") != null) v.setPriority(((Number) body.get("priority")).intValue());
        vehicleTypeRepository.save(v);

        return ResponseEntity.ok(Map.of("success", true, "message", "Vehicle type updated", "id", id));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DELETE /api/vehicle-types/{id}
    // Soft-delete: sets status=inactive (so existing driver profiles are intact)
    // ─────────────────────────────────────────────────────────────────────────
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable String id) {
        Optional<VehicleType> opt = vehicleTypeRepository.findById(id);
        if (opt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of(
                    "success", false, "message", "Vehicle type not found: " + id));
        }
        VehicleType v = opt.get();
        v.setStatus("inactive");
        vehicleTypeRepository.save(v);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Vehicle type disabled (soft-deleted)",
                "id", id
        ));
    }
}
