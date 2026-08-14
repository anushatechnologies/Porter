package com.anushaporter.backend.controller;

import com.anushaporter.backend.model.PorterService;
import com.anushaporter.backend.repository.PorterServiceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/services")
public class AdminPorterServiceController {

    @Autowired
    private PorterServiceRepository serviceRepository;

    /**
     * GET /api/admin/services
     * Returns list of all services (Active & Inactive) with search, category filter, and sort.
     */
    @GetMapping
    public ResponseEntity<?> getAllServices(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search) {

        List<PorterService> services = serviceRepository.findAllByOrderByDisplayOrderAsc();

        // 1. Filter by category if provided
        if (category != null && !category.isBlank() && !"all".equalsIgnoreCase(category)) {
            services = services.stream()
                    .filter(s -> s.getCategory() != null && s.getCategory().equalsIgnoreCase(category))
                    .collect(Collectors.toList());
        }

        // 2. Filter by status if provided
        if (status != null && !status.isBlank() && !"all".equalsIgnoreCase(status)) {
            boolean activeFilter = "active".equalsIgnoreCase(status) || "true".equalsIgnoreCase(status);
            services = services.stream()
                    .filter(s -> Boolean.TRUE.equals(s.getIsActive()) == activeFilter)
                    .collect(Collectors.toList());
        }

        // 3. Search by name, slug, subtitle or description
        if (search != null && !search.isBlank()) {
            String query = search.trim().toLowerCase();
            services = services.stream()
                    .filter(s -> (s.getName() != null && s.getName().toLowerCase().contains(query))
                            || (s.getServiceId() != null && s.getServiceId().toLowerCase().contains(query))
                            || (s.getSubtitle() != null && s.getSubtitle().toLowerCase().contains(query))
                            || (s.getDescription() != null && s.getDescription().toLowerCase().contains(query)))
                    .collect(Collectors.toList());
        }

        List<Map<String, Object>> formatted = services.stream()
                .map(this::formatServiceForAdmin)
                .collect(Collectors.toList());

        return ResponseEntity.ok(Map.of(
                "success", true,
                "count", formatted.size(),
                "services", formatted
        ));
    }

    /**
     * GET /api/admin/services/{id}
     * Retrieves single service by numeric ID or string slug.
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getServiceById(@PathVariable String id) {
        PorterService service = findServiceByIdOrSlug(id);
        if (service == null) {
            return ResponseEntity.status(404).body(Map.of(
                    "success", false,
                    "message", "Service not found with identifier: " + id
            ));
        }
        return ResponseEntity.ok(Map.of("success", true, "service", formatServiceForAdmin(service)));
    }

    /**
     * POST /api/admin/services
     * Creates a new service.
     */
    @PostMapping
    public ResponseEntity<?> createService(@RequestBody Map<String, Object> payload) {
        PorterService service = new PorterService();
        mapServiceFromPayload(payload, service);

        if (service.getServiceId() == null || service.getServiceId().isBlank()) {
            String name = service.getName() != null ? service.getName() : "service";
            String slug = name.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
            service.setServiceId(slug);
        }

        // Check if slug already exists
        if (serviceRepository.findByServiceId(service.getServiceId()).isPresent()) {
            service.setServiceId(service.getServiceId() + "-" + (System.currentTimeMillis() % 10000));
        }

        if (service.getDisplayOrder() == null || service.getDisplayOrder() <= 0) {
            long count = serviceRepository.count();
            service.setDisplayOrder((int) count + 1);
        }

        PorterService saved = serviceRepository.save(service);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Service created successfully",
                "service", formatServiceForAdmin(saved)
        ));
    }

    /**
     * PUT /api/admin/services/{id}
     * Updates an existing service.
     */
    @PutMapping("/{id}")
    public ResponseEntity<?> updateService(@PathVariable String id, @RequestBody Map<String, Object> payload) {
        PorterService service = findServiceByIdOrSlug(id);
        if (service == null) {
            return ResponseEntity.status(404).body(Map.of(
                    "success", false,
                    "message", "Service not found with identifier: " + id
            ));
        }

        mapServiceFromPayload(payload, service);
        PorterService saved = serviceRepository.save(service);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Service updated successfully",
                "service", formatServiceForAdmin(saved)
        ));
    }

    /**
     * PATCH /api/admin/services/{id}/toggle-status
     * One-click Toggle Active/Inactive status.
     */
    @PatchMapping("/{id}/toggle-status")
    public ResponseEntity<?> toggleStatus(
            @PathVariable String id,
            @RequestBody(required = false) Map<String, Object> payload) {

        PorterService service = findServiceByIdOrSlug(id);
        if (service == null) {
            return ResponseEntity.status(404).body(Map.of(
                    "success", false,
                    "message", "Service not found with identifier: " + id
            ));
        }

        if (payload != null && payload.containsKey("isActive")) {
            Object raw = payload.get("isActive");
            if (raw instanceof Boolean) {
                service.setIsActive((Boolean) raw);
            } else if (raw != null) {
                service.setIsActive(Boolean.parseBoolean(raw.toString()));
            }
        } else if (payload != null && payload.containsKey("status")) {
            Object raw = payload.get("status");
            if (raw instanceof Boolean) {
                service.setIsActive((Boolean) raw);
            } else if (raw != null) {
                service.setIsActive("active".equalsIgnoreCase(raw.toString()) || "true".equalsIgnoreCase(raw.toString()));
            }
        } else {
            // Toggle boolean
            service.setIsActive(!Boolean.TRUE.equals(service.getIsActive()));
        }

        PorterService saved = serviceRepository.save(service);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Service status updated to " + (Boolean.TRUE.equals(saved.getIsActive()) ? "Active" : "Inactive"),
                "isActive", saved.getIsActive(),
                "service", formatServiceForAdmin(saved)
        ));
    }

    /**
     * PATCH /api/admin/services/reorder
     * Updates display order via drag-and-drop list of IDs.
     * Request body: { "serviceIds": ["two-wheeler", "mini-truck", "packers-movers"] }
     */
    @PatchMapping("/reorder")
    public ResponseEntity<?> reorderServices(@RequestBody Map<String, Object> payload) {
        @SuppressWarnings("unchecked")
        List<?> serviceIds = (List<?>) payload.get("serviceIds");
        if (serviceIds == null || serviceIds.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "serviceIds array is required"
            ));
        }

        int order = 1;
        for (Object item : serviceIds) {
            if (item != null) {
                PorterService service = findServiceByIdOrSlug(item.toString());
                if (service != null) {
                    service.setDisplayOrder(order++);
                    serviceRepository.save(service);
                }
            }
        }

        List<PorterService> updated = serviceRepository.findAllByOrderByDisplayOrderAsc();
        List<Map<String, Object>> formatted = updated.stream()
                .map(this::formatServiceForAdmin)
                .collect(Collectors.toList());

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Services reordered successfully",
                "services", formatted
        ));
    }

    /**
     * DELETE /api/admin/services/{id}
     * Deletes a service.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteService(@PathVariable String id) {
        PorterService service = findServiceByIdOrSlug(id);
        if (service == null) {
            return ResponseEntity.status(404).body(Map.of(
                    "success", false,
                    "message", "Service not found with identifier: " + id
            ));
        }

        serviceRepository.delete(service);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Service deleted successfully"
        ));
    }

    public Map<String, Object> formatServiceForAdmin(PorterService s) {
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
        map.put("createdAt", s.getCreatedAt());
        map.put("updatedAt", s.getUpdatedAt());
        return map;
    }

    private PorterService findServiceByIdOrSlug(String identifier) {
        if (identifier == null || identifier.isBlank()) return null;
        String clean = identifier.trim();

        // 1. Try numeric ID
        if (clean.matches("^\\d+$")) {
            try {
                Long numId = Long.parseLong(clean);
                Optional<PorterService> opt = serviceRepository.findById(numId);
                if (opt.isPresent()) return opt.get();
            } catch (Exception ignored) {}
        }

        // 2. Try serviceId / slug
        Optional<PorterService> slugOpt = serviceRepository.findFirstByServiceIdIgnoreCase(clean);
        if (slugOpt.isPresent()) return slugOpt.get();

        // 3. Try name match
        for (PorterService s : serviceRepository.findAll()) {
            if (s.getName() != null && s.getName().equalsIgnoreCase(clean)) {
                return s;
            }
        }

        return null;
    }

    private void mapServiceFromPayload(Map<String, Object> payload, PorterService target) {
        if (payload.containsKey("serviceId") && payload.get("serviceId") != null) {
            target.setServiceId(payload.get("serviceId").toString().trim());
        } else if (payload.containsKey("id") && payload.get("id") != null && !payload.get("id").toString().matches("^\\d+$")) {
            target.setServiceId(payload.get("id").toString().trim());
        }
        if (payload.containsKey("name") && payload.get("name") != null) {
            target.setName(payload.get("name").toString().trim());
        }
        if (payload.containsKey("label") && payload.get("label") != null) {
            target.setLabel(payload.get("label").toString().trim());
        } else if (target.getLabel() == null && target.getName() != null) {
            target.setLabel(target.getName());
        }
        if (payload.containsKey("category") && payload.get("category") != null) {
            target.setCategory(payload.get("category").toString().trim().toLowerCase());
        }
        if (payload.containsKey("subtitle")) {
            target.setSubtitle(payload.get("subtitle") != null ? payload.get("subtitle").toString().trim() : null);
        }
        if (payload.containsKey("description")) {
            target.setDescription(payload.get("description") != null ? payload.get("description").toString().trim() : null);
        }
        if (payload.containsKey("baseFare") && payload.get("baseFare") != null) {
            target.setBaseFare(parseDouble(payload.get("baseFare")));
        } else if (payload.containsKey("basePrice") && payload.get("basePrice") != null) {
            target.setBaseFare(parseDouble(payload.get("basePrice")));
        }
        if (payload.containsKey("baseKm") && payload.get("baseKm") != null) {
            target.setBaseKm(parseDouble(payload.get("baseKm")));
        }
        if (payload.containsKey("perKmRate") && payload.get("perKmRate") != null) {
            target.setPerKmRate(parseDouble(payload.get("perKmRate")));
        } else if (payload.containsKey("pricePerKm") && payload.get("pricePerKm") != null) {
            target.setPerKmRate(parseDouble(payload.get("pricePerKm")));
        }
        if (payload.containsKey("helperRate") && payload.get("helperRate") != null) {
            target.setHelperRate(parseDouble(payload.get("helperRate")));
        }
        if (payload.containsKey("capacityKg") && payload.get("capacityKg") != null) {
            target.setCapacityKg(parseInt(payload.get("capacityKg")));
        }
        if (payload.containsKey("capacityLabel")) {
            target.setCapacityLabel(payload.get("capacityLabel") != null ? payload.get("capacityLabel").toString() : null);
        } else if (payload.containsKey("capacity")) {
            target.setCapacityLabel(payload.get("capacity") != null ? payload.get("capacity").toString() : null);
        }
        if (payload.containsKey("dimensions")) {
            target.setDimensions(payload.get("dimensions") != null ? payload.get("dimensions").toString() : null);
        }
        if (payload.containsKey("etaLabel")) {
            target.setEtaLabel(payload.get("etaLabel") != null ? payload.get("etaLabel").toString() : null);
        }
        if (payload.containsKey("iconUrl") && payload.get("iconUrl") != null) {
            target.setIconUrl(payload.get("iconUrl").toString().trim());
        } else if (payload.containsKey("imageUrl") && payload.get("imageUrl") != null) {
            target.setIconUrl(payload.get("imageUrl").toString().trim());
        }
        if (payload.containsKey("bgTint") && payload.get("bgTint") != null) {
            target.setBgTint(payload.get("bgTint").toString().trim());
        }
        if (payload.containsKey("isActive") && payload.get("isActive") != null) {
            target.setIsActive(Boolean.parseBoolean(payload.get("isActive").toString()));
        } else if (payload.containsKey("status") && payload.get("status") != null) {
            target.setIsActive("active".equalsIgnoreCase(payload.get("status").toString()) || "true".equalsIgnoreCase(payload.get("status").toString()));
        }
        if (payload.containsKey("displayOrder") && payload.get("displayOrder") != null) {
            target.setDisplayOrder(parseInt(payload.get("displayOrder")));
        } else if (payload.containsKey("order") && payload.get("order") != null) {
            target.setDisplayOrder(parseInt(payload.get("order")));
        }
        if (payload.containsKey("availableCities") && payload.get("availableCities") != null) {
            target.setAvailableCities(payload.get("availableCities").toString());
        }
    }

    private Double parseDouble(Object val) {
        if (val == null) return null;
        try {
            return Double.parseDouble(val.toString().replaceAll("[^0-9.]", "").trim());
        } catch (Exception e) {
            return null;
        }
    }

    private Integer parseInt(Object val) {
        if (val == null) return null;
        try {
            return Integer.parseInt(val.toString().replaceAll("[^0-9]", "").trim());
        } catch (Exception e) {
            return null;
        }
    }
}
