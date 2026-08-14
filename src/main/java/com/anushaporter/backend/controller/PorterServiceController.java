package com.anushaporter.backend.controller;

import com.anushaporter.backend.model.PorterService;
import com.anushaporter.backend.repository.PorterServiceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/services")
public class PorterServiceController {

    @Autowired
    private PorterServiceRepository serviceRepository;

    /**
     * GET /api/services
     * Returns list of active services sorted by display_order for Customer App Home Screen.
     */
    @GetMapping
    public ResponseEntity<?> getActiveServices(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String city) {

        List<PorterService> services = serviceRepository.findByIsActiveTrueOrderByDisplayOrderAsc();

        // If table is empty, return default seeded services
        if (services.isEmpty()) {
            services = getDefaultFallbackServices();
        }

        // Filter by category if provided
        if (category != null && !category.isBlank() && !"all".equalsIgnoreCase(category)) {
            services = services.stream()
                    .filter(s -> s.getCategory() != null && s.getCategory().equalsIgnoreCase(category))
                    .collect(Collectors.toList());
        }

        // Filter by city availability if provided
        if (city != null && !city.isBlank()) {
            String cityClean = city.trim().toLowerCase();
            services = services.stream()
                    .filter(s -> isAvailableInCity(s.getAvailableCities(), cityClean))
                    .collect(Collectors.toList());
        }

        List<Map<String, Object>> formatted = services.stream()
                .map(this::formatServiceForApp)
                .collect(Collectors.toList());

        return ResponseEntity.ok(Map.of(
                "success", true,
                "count", formatted.size(),
                "featuredServices", formatted,
                "services", formatted
        ));
    }

    /**
     * GET /api/services/{id}
     * Returns details of a specific service by ID or slug.
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getServiceById(@PathVariable String id) {
        PorterService service = findServiceByIdOrSlug(id);
        if (service == null || !Boolean.TRUE.equals(service.getIsActive())) {
            return ResponseEntity.status(404).body(Map.of(
                    "success", false,
                    "message", "Service not found or unavailable"
            ));
        }

        return ResponseEntity.ok(Map.of(
                "success", true,
                "service", formatServiceForApp(service)
        ));
    }

    /**
     * GET /api/services/trucks OR GET /api/services/vehicles
     * Returns list of truck and vehicle options for TruckVehicleSelectionScreen.
     */
    @GetMapping({"/trucks", "/vehicles"})
    public ResponseEntity<?> getVehicleServices() {
        List<PorterService> services = serviceRepository.findByIsActiveTrueOrderByDisplayOrderAsc();
        if (services.isEmpty()) {
            services = getDefaultFallbackServices();
        }

        List<Map<String, Object>> vehicles = services.stream()
                .filter(s -> s.getCategory() == null || "vehicle".equalsIgnoreCase(s.getCategory()) || "two_wheeler".equalsIgnoreCase(s.getCategory()))
                .map(this::formatServiceForApp)
                .collect(Collectors.toList());

        return ResponseEntity.ok(Map.of(
                "success", true,
                "count", vehicles.size(),
                "vehicles", vehicles
        ));
    }

    /**
     * GET /api/services/packers
     * Returns list of packers & movers options.
     */
    @GetMapping("/packers")
    public ResponseEntity<?> getPackersServices() {
        List<PorterService> services = serviceRepository.findByCategoryIgnoreCaseAndIsActiveTrueOrderByDisplayOrderAsc("packers");
        if (services.isEmpty()) {
            services = getDefaultFallbackServices().stream()
                    .filter(s -> "packers".equalsIgnoreCase(s.getCategory()))
                    .collect(Collectors.toList());
        }

        List<Map<String, Object>> packers = services.stream()
                .map(this::formatServiceForApp)
                .collect(Collectors.toList());

        return ResponseEntity.ok(Map.of(
                "success", true,
                "count", packers.size(),
                "services", packers
        ));
    }

    public Map<String, Object> formatServiceForApp(PorterService s) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", s.getServiceId() != null ? s.getServiceId() : "service-" + s.getId());
        map.put("serviceId", s.getServiceId() != null ? s.getServiceId() : "service-" + s.getId());
        map.put("numericId", s.getId());
        map.put("name", s.getName() != null ? s.getName() : "");
        map.put("label", s.getLabel() != null ? s.getLabel() : (s.getName() != null ? s.getName() : ""));
        map.put("category", s.getCategory() != null ? s.getCategory() : "vehicle");
        map.put("subtitle", s.getSubtitle() != null ? s.getSubtitle() : "");
        map.put("description", s.getSubtitle() != null ? s.getSubtitle() : "");
        map.put("baseFare", s.getBaseFare() != null ? s.getBaseFare() : 0.0);
        map.put("basePrice", s.getBaseFare() != null ? s.getBaseFare() : 0.0);
        map.put("baseKm", s.getBaseKm() != null ? s.getBaseKm() : 2.0);
        map.put("perKmRate", s.getPerKmRate() != null ? s.getPerKmRate() : 0.0);
        map.put("pricePerKm", s.getPerKmRate() != null ? s.getPerKmRate() : 0.0);
        map.put("helperRate", s.getHelperRate() != null ? s.getHelperRate() : 0.0);
        map.put("capacityKg", s.getCapacityKg() != null ? s.getCapacityKg() : 0);
        map.put("capacity", s.getCapacityLabel() != null ? s.getCapacityLabel() : (s.getCapacityKg() != null ? s.getCapacityKg() + " Kg" : ""));
        map.put("capacityLabel", s.getCapacityLabel() != null ? s.getCapacityLabel() : "");
        map.put("dimensions", s.getDimensions());
        map.put("etaLabel", s.getEtaLabel() != null ? s.getEtaLabel() : "10-15 mins");
        map.put("iconUrl", s.getIconUrl() != null ? s.getIconUrl() : "");
        map.put("imageUrl", s.getIconUrl() != null ? s.getIconUrl() : "");
        map.put("bgTint", s.getBgTint() != null ? s.getBgTint() : "#EEF4FF");
        map.put("isActive", Boolean.TRUE.equals(s.getIsActive()));
        map.put("order", s.getDisplayOrder() != null ? s.getDisplayOrder() : 1);
        map.put("displayOrder", s.getDisplayOrder() != null ? s.getDisplayOrder() : 1);
        map.put("availableCities", s.getAvailableCities() != null ? s.getAvailableCities() : "[\"ALL\"]");
        return map;
    }

    private boolean isAvailableInCity(String availableCitiesJson, String targetCity) {
        if (availableCitiesJson == null || availableCitiesJson.isBlank() || availableCitiesJson.contains("\"ALL\"") || availableCitiesJson.contains("ALL")) {
            return true;
        }
        return availableCitiesJson.toLowerCase().contains(targetCity);
    }

    private PorterService findServiceByIdOrSlug(String identifier) {
        if (identifier == null || identifier.isBlank()) return null;
        String clean = identifier.trim();

        if (clean.matches("^\\d+$")) {
            try {
                Long numId = Long.parseLong(clean);
                Optional<PorterService> opt = serviceRepository.findById(numId);
                if (opt.isPresent()) return opt.get();
            } catch (Exception ignored) {}
        }

        Optional<PorterService> slugOpt = serviceRepository.findFirstByServiceIdIgnoreCase(clean);
        if (slugOpt.isPresent()) return slugOpt.get();

        for (PorterService s : serviceRepository.findAll()) {
            if (s.getName() != null && s.getName().equalsIgnoreCase(clean)) {
                return s;
            }
        }
        return null;
    }

    public static List<PorterService> getDefaultFallbackServices() {
        List<PorterService> list = new ArrayList<>();

        PorterService s1 = new PorterService();
        s1.setId(1L);
        s1.setServiceId("two-wheeler");
        s1.setName("2 Wheeler");
        s1.setLabel("2 Wheeler");
        s1.setCategory("two_wheeler");
        s1.setSubtitle("Fast courier & small parcel delivery");
        s1.setBaseFare(49.0);
        s1.setBaseKm(2.0);
        s1.setPerKmRate(12.0);
        s1.setCapacityKg(20);
        s1.setCapacityLabel("20 Kg");
        s1.setIconUrl("https://cdn.anushaporter.com/services/bike.png");
        s1.setBgTint("#EEF4FF");
        s1.setIsActive(true);
        s1.setDisplayOrder(1);
        list.add(s1);

        PorterService s2 = new PorterService();
        s2.setId(2L);
        s2.setServiceId("mini-truck");
        s2.setName("Mini Truck (Ace)");
        s2.setLabel("Mini Truck (Ace)");
        s2.setCategory("vehicle");
        s2.setSubtitle("Ideal for 1-2 BHK house shifting or small businesses");
        s2.setBaseFare(249.0);
        s2.setBaseKm(2.0);
        s2.setPerKmRate(22.0);
        s2.setCapacityKg(750);
        s2.setCapacityLabel("750 Kg");
        s2.setIconUrl("https://cdn.anushaporter.com/services/tata-ace.png");
        s2.setBgTint("#F0FDF4");
        s2.setIsActive(true);
        s2.setDisplayOrder(2);
        list.add(s2);

        PorterService s3 = new PorterService();
        s3.setId(3L);
        s3.setServiceId("pickup-8ft");
        s3.setName("Pickup 8ft");
        s3.setLabel("Pickup 8ft");
        s3.setCategory("vehicle");
        s3.setSubtitle("Spacious open cargo bed for furniture and commercial items");
        s3.setBaseFare(399.0);
        s3.setBaseKm(2.0);
        s3.setPerKmRate(25.0);
        s3.setCapacityKg(1200);
        s3.setCapacityLabel("1200 Kg");
        s3.setIconUrl("https://cdn.anushaporter.com/services/pickup.png");
        s3.setBgTint("#FFFBEB");
        s3.setIsActive(true);
        s3.setDisplayOrder(3);
        list.add(s3);

        PorterService s4 = new PorterService();
        s4.setId(4L);
        s4.setServiceId("packers-movers");
        s4.setName("Packers & Movers");
        s4.setLabel("Packers & Movers");
        s4.setCategory("packers");
        s4.setSubtitle("Complete house shifting with professional packing & moving");
        s4.setBaseFare(1499.0);
        s4.setBaseKm(5.0);
        s4.setPerKmRate(35.0);
        s4.setCapacityKg(2500);
        s4.setCapacityLabel("Complete House Shifting");
        s4.setIconUrl("https://cdn.anushaporter.com/services/packers.png");
        s4.setBgTint("#FAF5FF");
        s4.setIsActive(true);
        s4.setDisplayOrder(4);
        list.add(s4);

        return list;
    }
}
