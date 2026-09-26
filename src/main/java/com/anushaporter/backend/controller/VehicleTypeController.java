package com.anushaporter.backend.controller;

import com.anushaporter.backend.model.VehicleType;
import com.anushaporter.backend.repository.VehicleTypeRepository;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Dynamic Vehicle Types Controller
 * Provides endpoints for Driver App (onboarding), Customer App
 * (booking/pricing), and Admin Web Dashboard.
 *
 * Endpoints:
 * - GET /api/vehicle-types?status=active — Fetch active vehicles for Driver &
 * User apps
 * - GET /api/admin/vehicle-types — Fetch all vehicles (active + inactive) for
 * Admin
 * - POST /api/admin/vehicle-types — Create/update vehicle category
 * - PUT /api/admin/vehicle-types/{id} — Update vehicle category pricing/details
 * - PATCH /api/admin/vehicle-types/{id}/status — Toggle/set active / inactive
 * status
 * - DELETE /api/admin/vehicle-types/{id} — Soft-delete / deactivate vehicle
 * category
 */
@RestController
@RequestMapping({
        "/api/vehicle-types",
        "/api/admin/vehicle-types",
        "/api/admin/vehicles",
        "/api/driver/vehicles",
        "/api/drivers/vehicles",
        "/api/driver/vehicle-types",
        "/api/drivers/vehicle-types",
        "/api/driver/vehicle-options",
        "/api/vehicles/types",
        "/api/vehicle/types"
})
public class VehicleTypeController {

    @Autowired
    private VehicleTypeRepository vehicleTypeRepository;

    @Autowired(required = false)
    private com.anushaporter.backend.repository.PassengerVehicleCategoryRepository passengerVehicleCategoryRepository;

    @Autowired(required = false)
    private com.anushaporter.backend.service.FleetSyncService fleetSyncService;

    @PostConstruct
    public void initVehicleTypes() {
        // Ensure baseline passenger vehicle types exist
        if (vehicleTypeRepository.findById("6").isEmpty()) {
            vehicleTypeRepository.save(build("6", "Cab", "cab",
                    "Comfortable 4-seater AC cab", "4 Passengers", 350, "4 Passengers + 2 Luggage",
                    "car", "https://poteranusha.s3.ap-south-2.amazonaws.com/vehicles/cab.png", 150.00, 2.0, 15.00, "PASSENGER", 6));
        }
        if (vehicleTypeRepository.findById("pass_bike").isEmpty() && vehicleTypeRepository.findByType("bike_taxi").isEmpty()) {
            vehicleTypeRepository.save(build("pass_bike", "Bike Taxi (Passenger)", "bike_taxi",
                    "Quick & affordable 1-passenger bike ride", "1 Passenger", 80, "1 Helmet Provided",
                    "bike", "https://images.unsplash.com/photo-1558981403-c5f9899a28bc?w=400&q=80", 30.00, 1.0, 10.00, "PASSENGER", 7));
        }
        if (vehicleTypeRepository.findById("pass_auto").isEmpty() && vehicleTypeRepository.findByType("auto_taxi").isEmpty()) {
            vehicleTypeRepository.save(build("pass_auto", "Auto Taxi (Passenger)", "auto_taxi",
                    "Convenient city auto ride for up to 3 passengers", "3 Passengers", 250, "Up to 3 Passengers",
                    "rickshaw", "https://images.unsplash.com/photo-1541899481282-d53bffe3c35d?w=400&q=80", 50.00, 1.5, 15.00, "PASSENGER", 8));
        }

        // Auto-sync delivery and freight services into vehicle_types
        syncFromPorterServices();

        // Purge non-vehicle artifacts (One-Way Ride, Porter Trucks & Fleet, Scooter Model, scooty, etc.)
        try {
            vehicleTypeRepository.findAll().forEach(vt -> {
                if (com.anushaporter.backend.service.FleetSyncService.isNonVehicleArtifact(vt.getName(), vt.getId())
                        || com.anushaporter.backend.service.FleetSyncService.isNonVehicleArtifact(vt.getDisplayName(), vt.getType())) {
                    vehicleTypeRepository.delete(vt);
                }
            });
        } catch (Exception ignored) {}

        // Heal legacy S3 image URLs if existing records are present.
        try {
            vehicleTypeRepository.findAll().forEach(vt -> {
                boolean changed = false;
                if (vt.getImageUrl() != null && vt.getImageUrl().contains("poteranusha.s3.amazonaws.com")) {
                    vt.setImageUrl(vt.getImageUrl().replace("poteranusha.s3.amazonaws.com",
                            "poteranusha.s3.ap-south-2.amazonaws.com"));
                    changed = true;
                }
                String typeKey = (vt.getType() != null ? vt.getType() : "").toLowerCase();
                String idKey = (vt.getId() != null ? vt.getId() : "").trim();
                if ((typeKey.contains("cab") || "6".equals(idKey)) && (vt.getImageUrl() == null
                        || vt.getImageUrl().isBlank() || vt.getImageUrl().contains("/vehicles/cab.png"))) {
                    vt.setImageUrl("https://poteranusha.s3.ap-south-2.amazonaws.com/vehicles/cab.png");
                    changed = true;
                }
                if (changed) {
                    vehicleTypeRepository.save(vt);
                }

                // Auto-sync passenger vehicles into passenger_vehicle_categories on startup
                String sType = vt.getServiceType() != null ? vt.getServiceType().toUpperCase() : "";
                if (sType.contains("PASSENGER") || sType.contains("BOTH") || sType.contains("CAB")
                        || typeKey.contains("cab") || typeKey.contains("taxi")) {
                    syncToPassengerCategory(vt);
                }
            });
        } catch (Exception ignored) {
        }
    }

    public void syncFromPorterServices() {
        if (fleetSyncService != null) {
            fleetSyncService.syncAll();
        }
    }

    private VehicleType build(String id, String name, String type, String description,
            String capacity, int capacityKg, String dimensions,
            String iconName, String imageUrl,
            double baseFare, double baseKm, double perKmRate,
            String serviceType, int priority) {
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
        v.setServiceType(serviceType);
        v.setStatus("active");
        v.setPriority(priority);
        return v;
    }

    /**
     * GET /api/vehicle-types
     * GET /api/vehicle-types/active
     * GET /api/vehicle-types?status=active
     * GET /api/vehicle-types?status=active&serviceType=OUR_SERVICES
     * GET /api/vehicle-types?status=active&serviceType=PASSENGER
     * GET /api/admin/vehicle-types
     */
    @GetMapping({ "", "/active" })
    public ResponseEntity<Map<String, Object>> getVehicleTypes(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String serviceType,
            @RequestParam(required = false) String service,
            HttpServletRequest request) {

        String effectiveStatus = status;
        if (request != null && request.getRequestURI() != null && request.getRequestURI().endsWith("/active")) {
            effectiveStatus = "active";
        }

        String requestedService = serviceType != null && !serviceType.isBlank() ? serviceType : service;
        String normalizedService = null;
        if (requestedService != null && !requestedService.isBlank()) {
            String s = requestedService.trim().toUpperCase();
            if (s.contains("PASSENGER") || s.contains("CAB") || s.contains("RIDE")) {
                normalizedService = "PASSENGER";
            } else if (s.contains("OUR") || s.contains("GOOD") || s.contains("TRUCK") || s.contains("LOGISTICS")) {
                normalizedService = "GOODS";
            } else if (s.contains("BOTH") || s.contains("ALL")) {
                normalizedService = "ALL";
            }
        }

        syncFromPorterServices();

        List<VehicleType> list = "active".equalsIgnoreCase(effectiveStatus)
                ? vehicleTypeRepository.findByStatusOrderByPriorityAsc("active")
                : vehicleTypeRepository.findAllByOrderByPriorityAsc();

        final String targetService = normalizedService;
        List<Map<String, Object>> vehicles = list.stream()
                .filter(vt -> !com.anushaporter.backend.service.FleetSyncService.isNonVehicleArtifact(vt.getName(), vt.getId())
                        && !com.anushaporter.backend.service.FleetSyncService.isNonVehicleArtifact(vt.getDisplayName(), vt.getType()))
                .map(this::formatVehicleType)
                .filter(m -> {
                    if (targetService == null || "ALL".equalsIgnoreCase(targetService))
                        return true;
                    String st = (String) m.get("serviceType");
                    if ("PASSENGER".equalsIgnoreCase(targetService)) {
                        return "PASSENGER".equalsIgnoreCase(st) || "BOTH".equalsIgnoreCase(st);
                    } else if ("GOODS".equalsIgnoreCase(targetService)
                            || "OUR_SERVICES".equalsIgnoreCase(targetService)) {
                        return "OUR_SERVICES".equalsIgnoreCase(st) || "GOODS".equalsIgnoreCase(st)
                                || "BOTH".equalsIgnoreCase(st);
                    }
                    return true;
                })
                .collect(Collectors.toList());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("serviceType",
                normalizedService != null ? ("GOODS".equals(normalizedService) ? "OUR_SERVICES" : normalizedService)
                        : "ALL");
        response.put("serviceCategory", normalizedService != null
                ? ("PASSENGER".equals(normalizedService) ? "Passenger Rides"
                        : ("GOODS".equals(normalizedService) ? "Our Services" : "All Services"))
                : "All Services");
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
        if (opt.isEmpty() && id != null) {
            String cleanId = id.toLowerCase().startsWith("veh_") ? id.substring(4)
                    : (id.toLowerCase().startsWith("veh-") ? id.substring(4) : id);
            opt = vehicleTypeRepository.findById(cleanId);
            if (opt.isEmpty()) {
                opt = vehicleTypeRepository.findByType(cleanId);
            }
            if (opt.isEmpty()) {
                String cClean = id.toLowerCase().replaceAll("[^a-z0-9]", "");
                opt = vehicleTypeRepository.findAll().stream().filter(v -> {
                    String vType = (v.getType() != null ? v.getType() : "").toLowerCase().replaceAll("[^a-z0-9]", "");
                    String vName = (v.getName() != null ? v.getName() : "").toLowerCase().replaceAll("[^a-z0-9]", "");
                    String vId = (v.getId() != null ? v.getId() : "").toLowerCase().replaceAll("[^a-z0-9]", "");
                    return cClean.equals(vType) || cClean.equals(vName) || cClean.equals(vId);
                }).findFirst();
            }
        }
        if (opt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("success", false, "message", "Vehicle type not found: " + id));
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
            syncToPassengerCategory(saved);

            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("success", true);
            resp.put("message", "Vehicle category saved successfully");
            resp.put("id", saved.getId());
            resp.put("vehicle", formatVehicleType(saved));

            return ResponseEntity.status(HttpStatus.CREATED).body(resp);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "message", "Failed to save vehicle category: " + e.getMessage()));
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

        VehicleType v;
        if (opt.isPresent()) {
            v = opt.get();
        } else {
            v = new VehicleType();
            v.setId(id);
            v.setType(id);
        }

        populateVehicleTypeFields(v, body);
        if (v.getType() == null || v.getType().isBlank()) {
            v.setType(id);
        }
        VehicleType saved = vehicleTypeRepository.save(v);
        syncToPassengerCategory(saved);

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
    public ResponseEntity<?> updateStatus(
            @PathVariable String id,
            @RequestParam(required = false) String status,
            @RequestBody(required = false) Map<String, Object> body) {
        Optional<VehicleType> opt = vehicleTypeRepository.findById(id);
        if (opt.isEmpty()) {
            opt = vehicleTypeRepository.findByType(id);
        }
        if (opt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "success", false,
                    "message", "Vehicle type not found: " + id));
        }

        VehicleType v = opt.get();
        String targetStatus = null;
        if (status != null && !status.isBlank()) {
            targetStatus = status.trim().toLowerCase();
        } else if (body != null && body.containsKey("status") && body.get("status") != null) {
            targetStatus = String.valueOf(body.get("status")).trim().toLowerCase();
        }

        if (targetStatus != null) {
            v.setStatus(targetStatus);
        } else {
            v.setStatus("active".equalsIgnoreCase(v.getStatus()) ? "inactive" : "active");
        }
        vehicleTypeRepository.save(v);
        syncToPassengerCategory(v);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Vehicle category status updated",
                "id", v.getId(),
                "status", v.getStatus()));
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
                    "message", "Vehicle type not found: " + id));
        }

        VehicleType v = opt.get();
        v.setStatus("inactive");
        vehicleTypeRepository.save(v);
        syncToPassengerCategory(v);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Vehicle category disabled (soft-deleted)",
                "id", v.getId()));
    }

    private void syncToPassengerCategory(VehicleType v) {
        if (passengerVehicleCategoryRepository == null || v == null)
            return;
        try {
            String sType = v.getServiceType() != null ? v.getServiceType().toUpperCase() : "";
            String t = (v.getType() != null ? v.getType() : "").toLowerCase();
            String n = (v.getName() != null ? v.getName() : "").toLowerCase();
            boolean isPassenger = sType.contains("PASSENGER") || sType.contains("BOTH") || sType.contains("CAB")
                    || t.contains("cab") || t.contains("taxi") || n.contains("cab") || n.contains("taxi")
                    || "6".equals(v.getId());
            String catCode = (v.getType() != null && !v.getType().isBlank() ? v.getType() : v.getId()).toUpperCase();

            Optional<com.anushaporter.backend.model.PassengerVehicleCategory> existingOpt = passengerVehicleCategoryRepository
                    .findByCategoryCode(catCode);
            if (existingOpt.isEmpty() && v.getName() != null && !v.getName().isBlank()) {
                existingOpt = passengerVehicleCategoryRepository.findByCategoryCode(v.getName().trim().toUpperCase());
            }

            if (isPassenger) {
                com.anushaporter.backend.model.PassengerVehicleCategory cat = existingOpt
                        .orElse(new com.anushaporter.backend.model.PassengerVehicleCategory());
                cat.setCategoryCode(catCode);
                cat.setDisplayName(
                        v.getDisplayName() != null && !v.getDisplayName().isBlank() ? v.getDisplayName() : v.getName());
                cat.setDescription(v.getDescription());
                int capacity = v.getMaxPassengers() != null && v.getMaxPassengers() > 0
                        ? v.getMaxPassengers()
                        : (catCode.contains("BIKE") ? 1 : (catCode.contains("AUTO") ? 3 : 4));
                cat.setPassengerCapacity(capacity);
                cat.setLuggageCapacity(v.getMaxLuggage() != null ? v.getMaxLuggage() : 2);
                cat.setBaseFare(java.math.BigDecimal.valueOf(v.getBaseFare() != null ? v.getBaseFare() : 50.0));
                cat.setPerKmRate(java.math.BigDecimal.valueOf(v.getPerKmRate() != null ? v.getPerKmRate() : 15.0));
                cat.setMinimumFare(java.math.BigDecimal.valueOf(
                        v.getMinFare() != null ? v.getMinFare() : (v.getBaseFare() != null ? v.getBaseFare() : 50.0)));
                cat.setMinimumKm(java.math.BigDecimal.valueOf(v.getBaseKm() != null ? v.getBaseKm() : 1.0));
                cat.setDriverAllowance(
                        java.math.BigDecimal.valueOf(v.getDriverAllowance() != null ? v.getDriverAllowance() : 0.0));
                cat.setImageUrl(v.getImageUrl());
                cat.setDisplayOrder(v.getPriority() != null ? v.getPriority() : 1);
                cat.setActive("active".equalsIgnoreCase(v.getStatus()));
                passengerVehicleCategoryRepository.save(cat);
            } else if (existingOpt.isPresent()) {
                var cat = existingOpt.get();
                cat.setActive(false);
                passengerVehicleCategoryRepository.save(cat);
            }
        } catch (Exception ignored) {
        }
    }

    private void populateVehicleTypeFields(VehicleType v, Map<String, Object> body) {
        if (body.get("name") != null)
            v.setName(String.valueOf(body.get("name")).trim());
        if (body.get("type") != null)
            v.setType(String.valueOf(body.get("type")).trim());
        if (body.get("typeCode") != null)
            v.setType(String.valueOf(body.get("typeCode")).trim());
        if (body.get("type_code") != null)
            v.setType(String.valueOf(body.get("type_code")).trim());

        if (body.get("description") != null)
            v.setDescription(String.valueOf(body.get("description")).trim());
        if (body.get("capacity") != null)
            v.setCapacity(String.valueOf(body.get("capacity")).trim());

        if (body.get("capacityKg") != null) {
            v.setCapacityKg(parseInteger(body.get("capacityKg")));
        } else if (body.get("capacity_kg") != null) {
            v.setCapacityKg(parseInteger(body.get("capacity_kg")));
        }

        if (body.get("dimensions") != null)
            v.setDimensions(String.valueOf(body.get("dimensions")).trim());

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

        if (body.get("status") != null)
            v.setStatus(String.valueOf(body.get("status")).trim().toLowerCase());
        if (body.get("priority") != null)
            v.setPriority(parseInteger(body.get("priority")));

        if (body.get("serviceType") != null) {
            v.setServiceType(String.valueOf(body.get("serviceType")).trim());
        } else if (body.get("service_type") != null) {
            v.setServiceType(String.valueOf(body.get("service_type")).trim());
        } else if (body.get("serviceCategory") != null) {
            v.setServiceType(String.valueOf(body.get("serviceCategory")).trim());
        }

        // Extended Governance Fields
        if (body.get("displayName") != null) {
            v.setDisplayName(String.valueOf(body.get("displayName")).trim());
        } else if (body.get("display_name") != null) {
            v.setDisplayName(String.valueOf(body.get("display_name")).trim());
        }

        if (body.get("maxPassengers") != null) {
            v.setMaxPassengers(parseInteger(body.get("maxPassengers")));
        } else if (body.get("max_passengers") != null) {
            v.setMaxPassengers(parseInteger(body.get("max_passengers")));
        }

        if (body.get("maxLuggage") != null) {
            v.setMaxLuggage(parseInteger(body.get("maxLuggage")));
        } else if (body.get("max_luggage") != null) {
            v.setMaxLuggage(parseInteger(body.get("max_luggage")));
        }

        if (body.get("perMinuteRate") != null) {
            v.setPerMinuteRate(parseDouble(body.get("perMinuteRate")));
        } else if (body.get("per_minute_rate") != null) {
            v.setPerMinuteRate(parseDouble(body.get("per_minute_rate")));
        } else if (body.get("perMinute") != null) {
            v.setPerMinuteRate(parseDouble(body.get("perMinute")));
        } else if (body.get("per_minute") != null) {
            v.setPerMinuteRate(parseDouble(body.get("per_minute")));
        }

        if (body.get("driverAllowance") != null) {
            v.setDriverAllowance(parseDouble(body.get("driverAllowance")));
        } else if (body.get("driver_allowance") != null) {
            v.setDriverAllowance(parseDouble(body.get("driver_allowance")));
        }

        if (body.get("helperRate") != null) {
            v.setHelperRate(parseDouble(body.get("helperRate")));
        } else if (body.get("helper_rate") != null) {
            v.setHelperRate(parseDouble(body.get("helper_rate")));
        }

        if (body.get("volume") != null) {
            v.setVolume(parseDouble(body.get("volume")));
        }

        if (body.get("minFare") != null) {
            v.setMinFare(parseDouble(body.get("minFare")));
        } else if (body.get("min_fare") != null) {
            v.setMinFare(parseDouble(body.get("min_fare")));
        }

        if (body.get("maxFare") != null) {
            v.setMaxFare(parseDouble(body.get("maxFare")));
        } else if (body.get("max_fare") != null) {
            v.setMaxFare(parseDouble(body.get("max_fare")));
        }

        if (body.get("minDistance") != null) {
            v.setMinDistance(parseDouble(body.get("minDistance")));
        } else if (body.get("min_distance") != null) {
            v.setMinDistance(parseDouble(body.get("min_distance")));
        }

        if (body.get("maxDistance") != null) {
            v.setMaxDistance(parseDouble(body.get("maxDistance")));
        } else if (body.get("max_distance") != null) {
            v.setMaxDistance(parseDouble(body.get("max_distance")));
        }

        if (body.get("commissionPercentage") != null) {
            v.setCommissionPercentage(parseDouble(body.get("commissionPercentage")));
        } else if (body.get("commission_percentage") != null) {
            v.setCommissionPercentage(parseDouble(body.get("commission_percentage")));
        } else if (body.get("commission") != null) {
            v.setCommissionPercentage(parseDouble(body.get("commission")));
        }

        if (body.get("gstPercentage") != null) {
            v.setGstPercentage(parseDouble(body.get("gstPercentage")));
        } else if (body.get("gst_percentage") != null) {
            v.setGstPercentage(parseDouble(body.get("gst_percentage")));
        } else if (body.get("gst") != null) {
            v.setGstPercentage(parseDouble(body.get("gst")));
        }

        if (body.get("customerAppVisible") != null) {
            v.setCustomerAppVisible(parseBoolean(body.get("customerAppVisible")));
        } else if (body.get("customer_app_visible") != null) {
            v.setCustomerAppVisible(parseBoolean(body.get("customer_app_visible")));
        }

        if (body.get("availableCities") != null) {
            v.setAvailableCities(String.valueOf(body.get("availableCities")).trim());
        } else if (body.get("available_cities") != null) {
            v.setAvailableCities(String.valueOf(body.get("available_cities")).trim());
        }
    }

    private Map<String, Object> formatVehicleType(VehicleType v) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", v.getId());
        map.put("name", v.getName() != null ? v.getName() : "");
        map.put("displayName",
                v.getDisplayName() != null ? v.getDisplayName() : (v.getName() != null ? v.getName() : ""));
        map.put("display_name",
                v.getDisplayName() != null ? v.getDisplayName() : (v.getName() != null ? v.getName() : ""));
        map.put("type", v.getType() != null ? v.getType() : "");
        map.put("typeCode", v.getType() != null ? v.getType() : "");
        map.put("type_code", v.getType() != null ? v.getType() : "");
        map.put("description", v.getDescription() != null ? v.getDescription() : "");
        map.put("capacity", v.getCapacity() != null ? v.getCapacity() : "");
        map.put("capacityKg", v.getCapacityKg() != null ? v.getCapacityKg() : 20);
        map.put("dimensions", v.getDimensions() != null ? v.getDimensions() : "");
        map.put("iconName", v.getIconName() != null ? v.getIconName() : "bike");

        String typeKey = (v.getType() != null ? v.getType() : "").toLowerCase();
        String nameKey = (v.getName() != null ? v.getName() : "").toLowerCase();
        String idKey = (v.getId() != null ? v.getId() : "").trim();
        String rawS = v.getServiceType() != null ? v.getServiceType().toUpperCase() : "";

        String rawImageUrl = v.getImageUrl() != null ? v.getImageUrl().trim() : "";
        if (rawImageUrl.contains("poteranusha.s3.amazonaws.com")) {
            rawImageUrl = rawImageUrl.replace("poteranusha.s3.amazonaws.com",
                    "poteranusha.s3.ap-south-2.amazonaws.com");
        }
        if (rawImageUrl.isEmpty() || rawImageUrl.endsWith("/vehicles/bike.png")
                || rawImageUrl.endsWith("/vehicles/auto.png")) {
            rawImageUrl = resolveDefaultVehicleImageUrl(typeKey, nameKey, idKey);
        }
        map.put("imageUrl", rawImageUrl);
        map.put("baseFare", v.getBaseFare() != null ? v.getBaseFare() : 40.0);
        map.put("baseKm", v.getBaseKm() != null ? v.getBaseKm() : 1.0);
        map.put("perKmRate", v.getPerKmRate() != null ? v.getPerKmRate() : 12.0);
        map.put("status", v.getStatus() != null ? v.getStatus() : "active");
        map.put("priority", v.getPriority() != null ? v.getPriority() : 1);

        // Extended Governance Fields
        map.put("maxPassengers", v.getMaxPassengers());
        map.put("max_passengers", v.getMaxPassengers());
        map.put("maxLuggage", v.getMaxLuggage());
        map.put("max_luggage", v.getMaxLuggage());
        map.put("perMinuteRate", v.getPerMinuteRate() != null ? v.getPerMinuteRate() : 0.0);
        map.put("per_minute_rate", v.getPerMinuteRate() != null ? v.getPerMinuteRate() : 0.0);
        map.put("driverAllowance", v.getDriverAllowance() != null ? v.getDriverAllowance() : 0.0);
        map.put("driver_allowance", v.getDriverAllowance() != null ? v.getDriverAllowance() : 0.0);
        map.put("helperRate", v.getHelperRate() != null ? v.getHelperRate() : 0.0);
        map.put("helper_rate", v.getHelperRate() != null ? v.getHelperRate() : 0.0);
        map.put("volume", v.getVolume());
        map.put("minFare", v.getMinFare() != null ? v.getMinFare() : v.getBaseFare());
        map.put("min_fare", v.getMinFare() != null ? v.getMinFare() : v.getBaseFare());
        map.put("maxFare", v.getMaxFare());
        map.put("max_fare", v.getMaxFare());
        map.put("minDistance", v.getMinDistance() != null ? v.getMinDistance() : 1.0);
        map.put("min_distance", v.getMinDistance() != null ? v.getMinDistance() : 1.0);
        map.put("maxDistance", v.getMaxDistance() != null ? v.getMaxDistance() : 500.0);
        map.put("max_distance", v.getMaxDistance() != null ? v.getMaxDistance() : 500.0);
        map.put("commissionPercentage", v.getCommissionPercentage() != null ? v.getCommissionPercentage() : 15.0);
        map.put("commission_percentage", v.getCommissionPercentage() != null ? v.getCommissionPercentage() : 15.0);
        map.put("gstPercentage", v.getGstPercentage() != null ? v.getGstPercentage() : 5.0);
        map.put("gst_percentage", v.getGstPercentage() != null ? v.getGstPercentage() : 5.0);
        map.put("customerAppVisible", v.getCustomerAppVisible() != null ? v.getCustomerAppVisible() : true);
        map.put("customer_app_visible", v.getCustomerAppVisible() != null ? v.getCustomerAppVisible() : true);
        map.put("availableCities", v.getAvailableCities() != null ? v.getAvailableCities() : "ALL");
        map.put("available_cities", v.getAvailableCities() != null ? v.getAvailableCities() : "ALL");

        String sType;
        String sCategory;
        List<String> supported;

        if ("BOTH".equalsIgnoreCase(rawS)) {
            sType = "BOTH";
            sCategory = "Both (Passenger & Courier)";
            supported = List.of("OUR_SERVICES", "GOODS", "PASSENGER");
        } else if ("PASSENGER".equalsIgnoreCase(rawS) || typeKey.contains("cab") || nameKey.contains("cab")
                || idKey.equals("6")
                || typeKey.contains("bike_taxi") || typeKey.contains("auto_taxi") || idKey.startsWith("pass_")) {
            sType = "PASSENGER";
            sCategory = "Passenger Rides";
            supported = List.of("PASSENGER");
        } else {
            sType = "OUR_SERVICES";
            sCategory = "Our Services";
            supported = List.of("OUR_SERVICES", "GOODS");
        }

        map.put("serviceType", sType);
        map.put("service_type", sType);
        map.put("serviceCategory", sCategory);
        map.put("service_category", sCategory);
        map.put("supportedServiceTypes", supported);
        return map;
    }

    private Integer parseInteger(Object val) {
        if (val == null)
            return null;
        try {
            if (val instanceof Number)
                return ((Number) val).intValue();
            return Integer.parseInt(String.valueOf(val).replaceAll("[^0-9-]", "").trim());
        } catch (Exception e) {
            return 1;
        }
    }

    private Double parseDouble(Object val) {
        if (val == null)
            return null;
        try {
            if (val instanceof Number)
                return ((Number) val).doubleValue();
            return Double.parseDouble(String.valueOf(val).replaceAll("[^0-9.-]", "").trim());
        } catch (Exception e) {
            return 0.0;
        }
    }

    private Boolean parseBoolean(Object val) {
        if (val == null)
            return null;
        if (val instanceof Boolean)
            return (Boolean) val;
        String s = String.valueOf(val).trim().toLowerCase();
        return "true".equals(s) || "1".equals(s) || "yes".equals(s);
    }

    public static String resolveDefaultVehicleImageUrl(String typeKey, String nameKey, String idKey) {
        String t = (typeKey != null ? typeKey : "").toLowerCase();
        String n = (nameKey != null ? nameKey : "").toLowerCase();
        String id = (idKey != null ? idKey : "").toLowerCase();

        if (t.contains("cab") || id.equals("6") || t.contains("car") || t.contains("hatchback") || n.contains("cab")
                || n.contains("car") || n.contains("hatchback")) {
            return "https://poteranusha.s3.ap-south-2.amazonaws.com/vehicles/cab.png";
        } else if (t.contains("bike") || t.contains("scooter") || n.contains("bike") || n.contains("scooter")
                || id.contains("bike") || id.contains("scooter")) {
            return "https://images.unsplash.com/photo-1558981403-c5f9899a28bc?w=400&q=80";
        } else if (t.contains("auto") || t.contains("rickshaw") || n.contains("auto") || id.contains("auto")) {
            return "https://images.unsplash.com/photo-1541899481282-d53bffe3c35d?w=400&q=80";
        } else if (t.contains("tata") || t.contains("ace") || t.contains("mini") || n.contains("tata")
                || n.contains("mini") || id.contains("tata") || id.contains("mini")) {
            return "https://images.unsplash.com/photo-1519003722824-194d4455a60c?w=400&q=80";
        } else if (t.contains("pickup") || n.contains("pickup") || id.contains("pickup")) {
            return "https://images.unsplash.com/photo-1559297434-fae8a1916a79?w=400&q=80";
        } else if (t.contains("407") || t.contains("truck") || t.contains("heavy") || t.contains("lpt")
                || n.contains("407") || n.contains("truck") || n.contains("lpt")) {
            return "https://images.unsplash.com/photo-1601584115197-04ecc0da31d7?w=400&q=80";
        }
        return "https://images.unsplash.com/photo-1519003722824-194d4455a60c?w=400&q=80";
    }
}
