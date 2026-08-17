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

    /**
     * GET /api/services/grouped
     * Returns active services grouped by category for mobile app category tabs.
     */
    @GetMapping("/grouped")
    public ResponseEntity<?> getGroupedServices() {
        List<PorterService> services = serviceRepository.findByIsActiveTrueOrderByDisplayOrderAsc();
        if (services.isEmpty()) {
            services = getDefaultFallbackServices();
        }

        Map<String, List<Map<String, Object>>> grouped = new LinkedHashMap<>();
        for (PorterService s : services) {
            String cat = s.getCategory() != null ? s.getCategory().toLowerCase() : "vehicle";
            grouped.computeIfAbsent(cat, k -> new ArrayList<>()).add(formatServiceForApp(s));
        }

        return ResponseEntity.ok(Map.of(
                "success", true,
                "grouped", grouped
        ));
    }

    public Map<String, Object> formatServiceForApp(PorterService s) {
        Map<String, Object> map = new LinkedHashMap<>();
        String categoryId = (s.getCategoryId() != null && !s.getCategoryId().isBlank()) ? s.getCategoryId() : inferCategoryId(s.getCategory());
        String categoryName = (s.getCategoryName() != null && !s.getCategoryName().isBlank()) ? s.getCategoryName() : inferCategoryName(s.getCategory());

        map.put("id", s.getServiceId() != null ? s.getServiceId() : "service-" + s.getId());
        map.put("serviceId", s.getServiceId() != null ? s.getServiceId() : "service-" + s.getId());
        map.put("numericId", s.getId());
        map.put("name", s.getName() != null ? s.getName() : "");
        map.put("label", s.getLabel() != null ? s.getLabel() : (s.getName() != null ? s.getName() : ""));
        map.put("category", s.getCategory() != null ? s.getCategory() : "vehicle");
        map.put("categoryId", categoryId);
        map.put("categoryName", categoryName);
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
        map.put("eta", s.getEtaLabel() != null ? s.getEtaLabel() : "5-10 mins");
        map.put("etaLabel", s.getEtaLabel() != null ? s.getEtaLabel() : "5-10 mins");
        map.put("iconUrl", s.getIconUrl() != null ? s.getIconUrl() : "");
        map.put("imageUrl", s.getIconUrl() != null ? s.getIconUrl() : "");
        map.put("bgTint", s.getBgTint() != null ? s.getBgTint() : "#EEF4FF");
        map.put("customerAppVisible", !Boolean.FALSE.equals(s.getCustomerAppVisible()));
        map.put("isActive", Boolean.TRUE.equals(s.getIsActive()));
        map.put("order", s.getDisplayOrder() != null ? s.getDisplayOrder() : 1);
        map.put("displayOrder", s.getDisplayOrder() != null ? s.getDisplayOrder() : 1);
        map.put("availableCities", s.getAvailableCities() != null ? s.getAvailableCities() : "[\"ALL\"]");
        return map;
    }

    private static String inferCategoryId(String cat) {
        if (cat == null) return "1";
        String lower = cat.toLowerCase();
        if (lower.contains("bike") || lower.contains("two_wheeler")) return "2";
        if (lower.contains("packer")) return "3";
        return "1";
    }

    private static String inferCategoryName(String cat) {
        if (cat == null) return "Porter Trucks & Fleet";
        String lower = cat.toLowerCase();
        if (lower.contains("bike") || lower.contains("two_wheeler")) return "2 Wheeler / Bike";
        if (lower.contains("packer")) return "Packers & Movers";
        return "Porter Trucks & Fleet";
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
        s1.setName("2 Wheeler – Bike Courier");
        s1.setLabel("2 Wheeler");
        s1.setCategory("two_wheeler");
        s1.setCategoryId("2");
        s1.setCategoryName("2 Wheeler / Bike");
        s1.setCustomerAppVisible(true);
        s1.setSubtitle("Instant delivery for small packages & documents up to 20 Kg");
        s1.setDescription("Instant delivery for small packages & documents");
        s1.setBaseFare(50.0);
        s1.setBaseKm(2.0);
        s1.setPerKmRate(8.0);
        s1.setHelperRate(0.0);
        s1.setCapacityKg(20);
        s1.setCapacityLabel("20 kg");
        s1.setDimensions("{\"length\":\"1.5 ft\",\"width\":\"1.5 ft\",\"height\":\"1.5 ft\"}");
        s1.setEtaLabel("3 mins");
        s1.setIconUrl("https://api.anushaporter.com/assets/bikes/2-wheeler.png");
        s1.setBgTint("#EEF4FF");
        s1.setIsActive(true);
        s1.setDisplayOrder(1);
        s1.setAvailableCities("[\"ALL\"]");
        list.add(s1);

        // 2. Tata Ace 750kg
        PorterService s2 = new PorterService();
        s2.setServiceId("tata-ace");
        s2.setName("Tata Ace – Mini Truck (750 Kg)");
        s2.setLabel("Tata Ace");
        s2.setCategory("vehicle");
        s2.setCategoryId("1");
        s2.setCategoryName("Porter Trucks & Fleet");
        s2.setCustomerAppVisible(true);
        s2.setSubtitle("Most popular mini-truck for home shifting & commercial loads");
        s2.setDescription("Most popular mini-truck for home shifting & commercial loads");
        s2.setBaseFare(210.0);
        s2.setBaseKm(2.0);
        s2.setPerKmRate(18.0);
        s2.setHelperRate(300.0);
        s2.setCapacityKg(750);
        s2.setCapacityLabel("750 kg");
        s2.setDimensions("{\"length\":\"7 ft\",\"width\":\"4.5 ft\",\"height\":\"5 ft\"}");
        s2.setEtaLabel("5 mins");
        s2.setIconUrl("https://api.anushaporter.com/assets/trucks/tata-ace.png");
        s2.setBgTint("#EBF5FF");
        s2.setIsActive(true);
        s2.setDisplayOrder(1);
        s2.setAvailableCities("[\"Hyderabad\",\"Secunderabad\",\"Bangalore\"]");
        list.add(s2);

        // 3. Three Wheeler 500kg
        PorterService s3 = new PorterService();
        s3.setServiceId("3-wheeler");
        s3.setName("3 Wheeler Cargo");
        s3.setLabel("3 Wheeler");
        s3.setCategory("vehicle");
        s3.setCategoryId("1");
        s3.setCategoryName("Porter Trucks & Fleet");
        s3.setCustomerAppVisible(true);
        s3.setSubtitle("Ideal for small items, light furniture & local moving");
        s3.setDescription("Ideal for small items, light furniture & local moving");
        s3.setBaseFare(160.0);
        s3.setBaseKm(2.0);
        s3.setPerKmRate(15.0);
        s3.setHelperRate(250.0);
        s3.setCapacityKg(500);
        s3.setCapacityLabel("500 kg");
        s3.setDimensions("{\"length\":\"5.5 ft\",\"width\":\"4 ft\",\"height\":\"4.5 ft\"}");
        s3.setEtaLabel("5 mins");
        s3.setIconUrl("https://api.anushaporter.com/assets/trucks/3-wheeler.png");
        s3.setBgTint("#FEF3C7");
        s3.setIsActive(true);
        s3.setDisplayOrder(2);
        s3.setAvailableCities("[\"ALL\"]");
        list.add(s3);

        // 4. Pickup 8ft 1200kg
        PorterService s4 = new PorterService();
        s4.setServiceId("pickup-8ft");
        s4.setName("Pickup 8ft (1200 Kg)");
        s4.setLabel("Pickup 8ft");
        s4.setCategory("vehicle");
        s4.setCategoryId("1");
        s4.setCategoryName("Porter Trucks & Fleet");
        s4.setCustomerAppVisible(true);
        s4.setSubtitle("Spacious flatbed truck for heavy commercial & industrial loads");
        s4.setDescription("Spacious flatbed truck for heavy commercial & industrial loads");
        s4.setBaseFare(399.0);
        s4.setBaseKm(3.0);
        s4.setPerKmRate(26.0);
        s4.setHelperRate(350.0);
        s4.setCapacityKg(1200);
        s4.setCapacityLabel("1200 kg");
        s4.setDimensions("{\"length\":\"8 ft\",\"width\":\"4.8 ft\",\"height\":\"5.5 ft\"}");
        s4.setEtaLabel("12 mins");
        s4.setIconUrl("https://api.anushaporter.com/assets/trucks/pickup-8ft.png");
        s4.setBgTint("#F3E8FF");
        s4.setIsActive(true);
        s4.setDisplayOrder(3);
        s4.setAvailableCities("[\"ALL\"]");
        list.add(s4);

        // 5. Tata 407 14ft 2500kg
        PorterService s5 = new PorterService();
        s5.setServiceId("tata-407-14ft");
        s5.setName("Tata 407 (2500 Kg)");
        s5.setLabel("Tata 407");
        s5.setCategory("vehicle");
        s5.setCategoryId("1");
        s5.setCategoryName("Porter Trucks & Fleet");
        s5.setCustomerAppVisible(true);
        s5.setSubtitle("Heavy duty container truck for 2-3 BHK complete house shifting");
        s5.setDescription("Heavy duty container truck for 2-3 BHK complete house shifting");
        s5.setBaseFare(699.0);
        s5.setBaseKm(3.0);
        s5.setPerKmRate(35.0);
        s5.setHelperRate(500.0);
        s5.setCapacityKg(2500);
        s5.setCapacityLabel("2500 kg");
        s5.setDimensions("{\"length\":\"14 ft\",\"width\":\"6 ft\",\"height\":\"6.5 ft\"}");
        s5.setEtaLabel("15 mins");
        s5.setIconUrl("https://api.anushaporter.com/assets/trucks/tata-407.png");
        s5.setBgTint("#DCFCE7");
        s5.setIsActive(true);
        s5.setDisplayOrder(4);
        s5.setAvailableCities("[\"ALL\"]");
        list.add(s5);

        // 6. Packers & Movers
        PorterService s6 = new PorterService();
        s6.setServiceId("packers-movers");
        s6.setName("Packers & Movers (Relocation)");
        s6.setLabel("Packers & Movers");
        s6.setCategory("packers");
        s6.setCategoryId("3");
        s6.setCategoryName("Packers & Movers");
        s6.setCustomerAppVisible(true);
        s6.setSubtitle("Hassle-free complete house & office relocation with packing & loading");
        s6.setDescription("Hassle-free complete house & office relocation with packing & loading");
        s6.setBaseFare(1499.0);
        s6.setBaseKm(5.0);
        s6.setPerKmRate(40.0);
        s6.setHelperRate(600.0);
        s6.setCapacityKg(3500);
        s6.setCapacityLabel("House Shifting");
        s6.setDimensions("{\"length\":\"14 ft\",\"width\":\"6.5 ft\",\"height\":\"7 ft\"}");
        s6.setEtaLabel("Slot Booking");
        s6.setIconUrl("https://api.anushaporter.com/assets/packers/packers.png");
        s6.setBgTint("#F1F5F9");
        s6.setIsActive(true);
        s6.setDisplayOrder(1);
        s6.setAvailableCities("[\"Hyderabad\",\"Bangalore\",\"Chennai\"]");
        list.add(s6);

        return list;
    }
}
