package com.anushaporter.backend.controller;

import com.anushaporter.backend.model.PorterService;
import com.anushaporter.backend.repository.PorterServiceRepository;
import jakarta.annotation.PostConstruct;
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

    @PostConstruct
    public void initSeedData() {
        if (serviceRepository.count() == 0) {
            List<PorterService> seeds = getDefaultFallbackServices();
            serviceRepository.saveAll(seeds);
            System.out.println("Seeded " + seeds.size() + " default Porter Services into the database.");
        }
    }

    /**
     * GET /api/services
     * Returns list of active services sorted by display_order for Customer App Home Screen.
     */
    @GetMapping
    public ResponseEntity<?> getActiveServices(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String city) {

        List<PorterService> services;
        if (category != null && !category.isBlank() && !"all".equalsIgnoreCase(category)) {
            services = serviceRepository.findByCategoryAndIsActiveTrueOrderByDisplayOrderAsc(category.toLowerCase());
            if (services.isEmpty()) {
                services = serviceRepository.findByCategoryIgnoreCaseAndIsActiveTrueOrderByDisplayOrderAsc(category);
            }
        } else {
            services = serviceRepository.findByIsActiveTrueOrderByDisplayOrderAsc();
        }

        // If table is empty, return default seeded services
        if (services.isEmpty()) {
            services = getDefaultFallbackServices();
            if (category != null && !category.isBlank() && !"all".equalsIgnoreCase(category)) {
                services = services.stream()
                        .filter(s -> s.getCategory() != null && s.getCategory().equalsIgnoreCase(category))
                        .collect(Collectors.toList());
            }
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
        map.put("description", s.getDescription() != null ? s.getDescription() : (s.getSubtitle() != null ? s.getSubtitle() : ""));
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

        try {
            Long numericId = Long.parseLong(clean);
            Optional<PorterService> idOpt = serviceRepository.findById(numericId);
            if (idOpt.isPresent()) return idOpt.get();
        } catch (NumberFormatException ignored) {}

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

        // 1. Two Wheeler
        PorterService s1 = new PorterService();
        s1.setServiceId("two-wheeler");
        s1.setName("2 Wheeler (Bike Courier)");
        s1.setLabel("2 Wheeler");
        s1.setCategory("two_wheeler");
        s1.setSubtitle("Fast delivery for small parcels, documents & food up to 20 Kg");
        s1.setDescription("Fast courier & parcel delivery");
        s1.setBaseFare(49.0);
        s1.setBaseKm(2.0);
        s1.setPerKmRate(12.0);
        s1.setHelperRate(0.0);
        s1.setCapacityKg(20);
        s1.setCapacityLabel("20 Kg");
        s1.setDimensions("{\"length\":\"1.5 ft\",\"width\":\"1.5 ft\",\"height\":\"1.5 ft\"}");
        s1.setEtaLabel("5-10 mins");
        s1.setIconUrl("https://images.unsplash.com/photo-1558981806-ec527fa84c39?w=500");
        s1.setBgTint("#EEF4FF");
        s1.setIsActive(true);
        s1.setDisplayOrder(1);
        s1.setAvailableCities("[\"ALL\"]");
        list.add(s1);

        // 2. Tata Ace 750kg
        PorterService s2 = new PorterService();
        s2.setServiceId("tata-ace-750kg");
        s2.setName("Porter Truck — Tata Ace (750kg)");
        s2.setLabel("Tata Ace");
        s2.setCategory("vehicle");
        s2.setSubtitle("Ideal for 1 BHK house shifting, furniture & commercial goods");
        s2.setDescription("Tata Ace Chota Hathi 750kg load");
        s2.setBaseFare(249.0);
        s2.setBaseKm(2.0);
        s2.setPerKmRate(22.5);
        s2.setHelperRate(300.0);
        s2.setCapacityKg(750);
        s2.setCapacityLabel("750 Kg");
        s2.setDimensions("{\"length\":\"7 ft\",\"width\":\"4.5 ft\",\"height\":\"5 ft\"}");
        s2.setEtaLabel("10-15 mins");
        s2.setIconUrl("https://images.unsplash.com/photo-1586191582056-a67b9e075052?w=500");
        s2.setBgTint("#EBF5FF");
        s2.setIsActive(true);
        s2.setDisplayOrder(2);
        s2.setAvailableCities("[\"Hyderabad\",\"Secunderabad\",\"Bangalore\"]");
        list.add(s2);

        // 3. Three Wheeler 500kg
        PorterService s3 = new PorterService();
        s3.setServiceId("three-wheeler");
        s3.setName("Porter Truck — 3 Wheeler (500kg)");
        s3.setLabel("3 Wheeler");
        s3.setCategory("vehicle");
        s3.setSubtitle("Economical choice for medium weight cargo up to 500 Kg");
        s3.setDescription("3 Wheeler Auto Load 500kg");
        s3.setBaseFare(199.0);
        s3.setBaseKm(2.0);
        s3.setPerKmRate(18.0);
        s3.setHelperRate(250.0);
        s3.setCapacityKg(500);
        s3.setCapacityLabel("500 Kg");
        s3.setDimensions("{\"length\":\"5.5 ft\",\"width\":\"4 ft\",\"height\":\"4.5 ft\"}");
        s3.setEtaLabel("8-12 mins");
        s3.setIconUrl("https://images.unsplash.com/photo-1592838064575-70ed626d3a0e?w=500");
        s3.setBgTint("#FEF3C7");
        s3.setIsActive(true);
        s3.setDisplayOrder(3);
        s3.setAvailableCities("[\"ALL\"]");
        list.add(s3);

        // 4. Pickup 8ft 1200kg
        PorterService s4 = new PorterService();
        s4.setServiceId("pickup-8ft");
        s4.setName("Porter Truck — Pickup 8ft (1200kg)");
        s4.setLabel("Pickup 8ft");
        s4.setCategory("vehicle");
        s4.setSubtitle("Spacious flatbed truck for heavy commercial & industrial loads");
        s4.setDescription("Bolero Pickup 8ft 1200kg");
        s4.setBaseFare(399.0);
        s4.setBaseKm(3.0);
        s4.setPerKmRate(26.0);
        s4.setHelperRate(350.0);
        s4.setCapacityKg(1200);
        s4.setCapacityLabel("1200 Kg");
        s4.setDimensions("{\"length\":\"8 ft\",\"width\":\"4.8 ft\",\"height\":\"5.5 ft\"}");
        s4.setEtaLabel("12-18 mins");
        s4.setIconUrl("https://images.unsplash.com/photo-1519003722824-194d4455a60c?w=500");
        s4.setBgTint("#F3E8FF");
        s4.setIsActive(true);
        s4.setDisplayOrder(4);
        s4.setAvailableCities("[\"ALL\"]");
        list.add(s4);

        // 5. Tata 407 14ft 2500kg
        PorterService s5 = new PorterService();
        s5.setServiceId("tata-407-14ft");
        s5.setName("Porter Truck — Tata 407 (2500kg)");
        s5.setLabel("Tata 407");
        s5.setCategory("vehicle");
        s5.setSubtitle("Heavy duty container truck for 2-3 BHK complete house shifting");
        s5.setDescription("Tata 407 14ft container 2500kg");
        s5.setBaseFare(699.0);
        s5.setBaseKm(3.0);
        s5.setPerKmRate(35.0);
        s5.setHelperRate(500.0);
        s5.setCapacityKg(2500);
        s5.setCapacityLabel("2500 Kg");
        s5.setDimensions("{\"length\":\"14 ft\",\"width\":\"6 ft\",\"height\":\"6.5 ft\"}");
        s5.setEtaLabel("15-25 mins");
        s5.setIconUrl("https://images.unsplash.com/photo-1601584115197-04ecc0da31d7?w=500");
        s5.setBgTint("#DCFCE7");
        s5.setIsActive(true);
        s5.setDisplayOrder(5);
        s5.setAvailableCities("[\"ALL\"]");
        list.add(s5);

        // 6. Packers & Movers
        PorterService s6 = new PorterService();
        s6.setServiceId("packers-movers");
        s6.setName("Packers & Movers (Relocation)");
        s6.setLabel("Packers & Movers");
        s6.setCategory("packers");
        s6.setSubtitle("Hassle-free complete house & office relocation with packing & loading");
        s6.setDescription("Full house shifting service");
        s6.setBaseFare(1499.0);
        s6.setBaseKm(5.0);
        s6.setPerKmRate(40.0);
        s6.setHelperRate(600.0);
        s6.setCapacityKg(3500);
        s6.setCapacityLabel("Complete House Shifting");
        s6.setDimensions("{\"length\":\"14 ft\",\"width\":\"6.5 ft\",\"height\":\"7 ft\"}");
        s6.setEtaLabel("Scheduled Slot");
        s6.setIconUrl("https://images.unsplash.com/photo-1560518883-ce09059eeffa?w=500");
        s6.setBgTint("#F1F5F9");
        s6.setIsActive(true);
        s6.setDisplayOrder(6);
        s6.setAvailableCities("[\"Hyderabad\",\"Bangalore\",\"Chennai\"]");
        list.add(s6);

        // 7. How Porter Works Guide
        PorterService s7 = new PorterService();
        s7.setServiceId("how-porter-works-guide");
        s7.setName("How Porter Works — Customer App Guide");
        s7.setLabel("How It Works");
        s7.setCategory("how_it_works");
        s7.setSubtitle("Step 1: Set Locations ➔ Step 2: Pick Truck/Bike ➔ Step 3: Live GPS Tracking & OTP Delivery");
        s7.setDescription("Customer App Guide Step");
        s7.setBaseFare(0.0);
        s7.setBaseKm(0.0);
        s7.setPerKmRate(0.0);
        s7.setHelperRate(0.0);
        s7.setCapacityKg(0);
        s7.setCapacityLabel("Instant On-Demand Booking");
        s7.setDimensions("{\"length\":\"N/A\",\"width\":\"N/A\",\"height\":\"N/A\"}");
        s7.setEtaLabel("24/7 Available");
        s7.setIconUrl("https://images.unsplash.com/photo-1526374965328-7f61d4dc18c5?w=500");
        s7.setBgTint("#FEF2F2");
        s7.setIsActive(true);
        s7.setDisplayOrder(7);
        s7.setAvailableCities("[\"ALL\"]");
        list.add(s7);

        return list;
    }
}
