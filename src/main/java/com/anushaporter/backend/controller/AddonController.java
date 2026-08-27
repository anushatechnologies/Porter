package com.anushaporter.backend.controller;

import com.anushaporter.backend.model.AddonService;
import com.anushaporter.backend.repository.AddonServiceRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * AddonController
 * ───────────────
 * Provides dynamic Add-on Services catalog for:
 *   1. Truck Booking / Load Assist Screen (Loading, Unloading, ₹7 per item rates, capacities)
 *   2. Packers & Movers Add-ons (Installation, Unpacking, Dismantling)
 *   3. Admin Configuration CRUD for all Add-on services.
 */
@RestController
public class AddonController {

    @Autowired
    private AddonServiceRepository addonServiceRepository;

    @PostConstruct
    public void seedDefaultAddons() {
        if (addonServiceRepository.count() == 0) {
            List<AddonService> defaults = new ArrayList<>();

            // ── 1. Truck Load Assist Add-ons ──────────────────────────────────
            AddonService loadAssist = new AddonService();
            loadAssist.setAddonId("addon_load_assist");
            loadAssist.setName("Loading & Unloading Service");
            loadAssist.setCategory("truck");
            loadAssist.setServiceType("load_assist");
            loadAssist.setDescription("Professional driver/helper assistance with loading and unloading items");
            loadAssist.setSubtitle("Starts @ ₹7 per item • Earliest pickup in 30 min");
            loadAssist.setIcon("truck-loading");
            loadAssist.setBasePrice(0.0);
            loadAssist.setPerItemRate(7.0);
            loadAssist.setPrice(7.0);
            loadAssist.setPricingUnit("per_item");
            loadAssist.setApplicableVehicles("[90, 500]");
            loadAssist.setApplicableGoodsTypes("commercial,personal");
            loadAssist.setDisplayOrder(1);
            loadAssist.setIsActive(true);
            loadAssist.setIsRecommended(true);
            defaults.add(loadAssist);

            AddonService loadingOnly = new AddonService();
            loadingOnly.setAddonId("addon_loading_only");
            loadingOnly.setName("Loading Only");
            loadingOnly.setCategory("truck");
            loadingOnly.setServiceType("load_assist");
            loadingOnly.setDescription("Assistance with loading items at pickup point");
            loadingOnly.setSubtitle("Starts @ ₹5 per item");
            loadingOnly.setIcon("upload");
            loadingOnly.setBasePrice(0.0);
            loadingOnly.setPerItemRate(5.0);
            loadingOnly.setPrice(5.0);
            loadingOnly.setPricingUnit("per_item");
            loadingOnly.setApplicableVehicles("[90, 500]");
            loadingOnly.setApplicableGoodsTypes("commercial,personal");
            loadingOnly.setDisplayOrder(2);
            loadingOnly.setIsActive(true);
            defaults.add(loadingOnly);

            AddonService unloadingOnly = new AddonService();
            unloadingOnly.setAddonId("addon_unloading_only");
            unloadingOnly.setName("Unloading Only");
            unloadingOnly.setCategory("truck");
            unloadingOnly.setServiceType("load_assist");
            unloadingOnly.setDescription("Assistance with unloading items at drop point");
            unloadingOnly.setSubtitle("Starts @ ₹5 per item");
            unloadingOnly.setIcon("download");
            unloadingOnly.setBasePrice(0.0);
            unloadingOnly.setPerItemRate(5.0);
            unloadingOnly.setPrice(5.0);
            unloadingOnly.setPricingUnit("per_item");
            unloadingOnly.setApplicableVehicles("[90, 500]");
            unloadingOnly.setApplicableGoodsTypes("commercial,personal");
            unloadingOnly.setDisplayOrder(3);
            unloadingOnly.setIsActive(true);
            defaults.add(unloadingOnly);

            // ── 2. Packers & Movers Add-ons ───────────────────────────────────
            AddonService installation = new AddonService();
            installation.setAddonId("addon_installation");
            installation.setName("Installation / Un-installation");
            installation.setCategory("packers");
            installation.setServiceType("assembly");
            installation.setDescription("TV, AC, Geyser, and appliance mounting / dismounting");
            installation.setSubtitle("Trained technicians");
            installation.setIcon("wrench");
            installation.setBasePrice(300.0);
            installation.setPrice(300.0);
            installation.setPricingUnit("flat");
            installation.setApplicableVehicles("ALL");
            installation.setDisplayOrder(4);
            installation.setIsActive(true);
            installation.setIsRecommended(true);
            defaults.add(installation);

            AddonService unpacking = new AddonService();
            unpacking.setAddonId("addon_unpacking");
            unpacking.setName("Unpacking all the packed items");
            unpacking.setCategory("packers");
            unpacking.setServiceType("packing");
            unpacking.setDescription("Complete unpacking and placement of items in your new home");
            unpacking.setSubtitle("Careful item placement");
            unpacking.setIcon("box-open");
            unpacking.setBasePrice(199.0);
            unpacking.setPrice(199.0);
            unpacking.setPricingUnit("flat");
            unpacking.setApplicableVehicles("ALL");
            unpacking.setDisplayOrder(5);
            unpacking.setIsActive(true);
            defaults.add(unpacking);

            AddonService dismantling = new AddonService();
            dismantling.setAddonId("addon_dismantling");
            dismantling.setName("Dismantling & Assembly");
            dismantling.setCategory("packers");
            dismantling.setServiceType("assembly");
            dismantling.setDescription("Cot, wardrobe, and furniture dismantling & reassembly");
            dismantling.setSubtitle("Includes hardware care");
            dismantling.setIcon("tools");
            dismantling.setBasePrice(249.0);
            dismantling.setPrice(249.0);
            dismantling.setPricingUnit("flat");
            dismantling.setApplicableVehicles("ALL");
            dismantling.setDisplayOrder(6);
            dismantling.setIsActive(true);
            defaults.add(dismantling);

            addonServiceRepository.saveAll(defaults);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CUSTOMER / APP FACING APIs
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * GET /api/addons
     * Fetches active add-on catalog with optional category & vehicle filters.
     * e.g. GET /api/addons?category=truck&capacityKg=500
     *      GET /api/addons?category=packers
     */
    @GetMapping({"/api/addons", "/api/customer/addons"})
    public ResponseEntity<Map<String, Object>> getAddons(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Integer capacityKg,
            @RequestParam(required = false) String serviceType) {

        List<AddonService> list = (category != null && !category.isBlank())
                ? addonServiceRepository.findByCategoryAndIsActiveTrueOrderByDisplayOrderAsc(category.toLowerCase())
                : addonServiceRepository.findByIsActiveTrueOrderByDisplayOrderAsc();

        if (serviceType != null && !serviceType.isBlank()) {
            list = list.stream()
                    .filter(a -> serviceType.equalsIgnoreCase(a.getServiceType()))
                    .collect(Collectors.toList());
        }

        if (capacityKg != null) {
            list = list.stream()
                    .filter(a -> a.getApplicableVehicles() == null
                            || a.getApplicableVehicles().equalsIgnoreCase("ALL")
                            || a.getApplicableVehicles().contains(String.valueOf(capacityKg)))
                    .collect(Collectors.toList());
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("total", list.size());
        response.put("addons", list);

        // Convenient groupings for frontend screens
        Map<String, List<AddonService>> grouped = list.stream()
                .collect(Collectors.groupingBy(a -> a.getCategory() != null ? a.getCategory() : "general"));
        response.put("grouped", grouped);

        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/addons/{id} or /api/addons/{addonId}
     */
    @GetMapping({"/api/addons/{id}", "/api/customer/addons/{id}"})
    public ResponseEntity<Map<String, Object>> getAddonById(@PathVariable String id) {
        Optional<AddonService> opt = findByIdOrAddonId(id);
        if (opt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("success", false, "message", "Add-on service not found"));
        }
        return ResponseEntity.ok(Map.of("success", true, "addon", opt.get()));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ADMIN CONFIGURATION APIs (CRUD)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * GET /api/admin/addons
     * Lists all add-on services (both active and inactive) for Admin panel.
     */
    @GetMapping("/api/admin/addons")
    public ResponseEntity<Map<String, Object>> adminListAddons(
            @RequestParam(required = false) String category) {
        List<AddonService> list = addonServiceRepository.findAllByOrderByDisplayOrderAsc();
        if (category != null && !category.isBlank()) {
            list = list.stream()
                    .filter(a -> category.equalsIgnoreCase(a.getCategory()))
                    .collect(Collectors.toList());
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("total", list.size());
        response.put("addons", list);
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/admin/addons
     * Creates a new Add-on service.
     */
    @PostMapping("/api/admin/addons")
    public ResponseEntity<Map<String, Object>> createAddon(@RequestBody AddonService body) {
        if (body.getAddonId() == null || body.getAddonId().isBlank()) {
            body.setAddonId("addon_" + UUID.randomUUID().toString().substring(0, 8));
        }

        AddonService saved = addonServiceRepository.save(body);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("message", "Add-on service created successfully");
        response.put("addon", saved);
        return ResponseEntity.status(201).body(response);
    }

    /**
     * PUT /api/admin/addons/{id}
     * Updates an existing Add-on service.
     */
    @PutMapping("/api/admin/addons/{id}")
    public ResponseEntity<Map<String, Object>> updateAddon(
            @PathVariable String id,
            @RequestBody AddonService body) {

        Optional<AddonService> opt = findByIdOrAddonId(id);
        if (opt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("success", false, "message", "Add-on service not found"));
        }

        AddonService existing = opt.get();
        if (body.getName() != null) existing.setName(body.getName());
        if (body.getCategory() != null) existing.setCategory(body.getCategory());
        if (body.getServiceType() != null) existing.setServiceType(body.getServiceType());
        if (body.getDescription() != null) existing.setDescription(body.getDescription());
        if (body.getSubtitle() != null) existing.setSubtitle(body.getSubtitle());
        if (body.getIcon() != null) existing.setIcon(body.getIcon());
        if (body.getBasePrice() != null) existing.setBasePrice(body.getBasePrice());
        if (body.getPerItemRate() != null) existing.setPerItemRate(body.getPerItemRate());
        if (body.getPrice() != null) existing.setPrice(body.getPrice());
        if (body.getPricingUnit() != null) existing.setPricingUnit(body.getPricingUnit());
        if (body.getApplicableVehicles() != null) existing.setApplicableVehicles(body.getApplicableVehicles());
        if (body.getApplicableGoodsTypes() != null) existing.setApplicableGoodsTypes(body.getApplicableGoodsTypes());
        if (body.getDisplayOrder() != null) existing.setDisplayOrder(body.getDisplayOrder());
        if (body.getIsActive() != null) existing.setIsActive(body.getIsActive());
        if (body.getIsRecommended() != null) existing.setIsRecommended(body.getIsRecommended());

        AddonService saved = addonServiceRepository.save(existing);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("message", "Add-on service updated successfully");
        response.put("addon", saved);
        return ResponseEntity.ok(response);
    }

    /**
     * PATCH /api/admin/addons/{id}/status
     * Toggles or sets active/inactive status.
     */
    @PatchMapping("/api/admin/addons/{id}/status")
    public ResponseEntity<Map<String, Object>> toggleStatus(
            @PathVariable String id,
            @RequestBody(required = false) Map<String, Object> body) {

        Optional<AddonService> opt = findByIdOrAddonId(id);
        if (opt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("success", false, "message", "Add-on service not found"));
        }

        AddonService existing = opt.get();
        if (body != null && body.containsKey("isActive")) {
            existing.setIsActive(Boolean.TRUE.equals(body.get("isActive")));
        } else {
            existing.setIsActive(!Boolean.TRUE.equals(existing.getIsActive()));
        }

        AddonService saved = addonServiceRepository.save(existing);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("message", "Status updated successfully");
        response.put("addonId", saved.getAddonId());
        response.put("isActive", saved.getIsActive());
        return ResponseEntity.ok(response);
    }

    /**
     * DELETE /api/admin/addons/{id}
     */
    @DeleteMapping("/api/admin/addons/{id}")
    public ResponseEntity<Map<String, Object>> deleteAddon(@PathVariable String id) {
        Optional<AddonService> opt = findByIdOrAddonId(id);
        if (opt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("success", false, "message", "Add-on service not found"));
        }

        addonServiceRepository.delete(opt.get());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("message", "Add-on service deleted successfully");
        return ResponseEntity.ok(response);
    }

    private Optional<AddonService> findByIdOrAddonId(String identifier) {
        try {
            Long numId = Long.parseLong(identifier);
            Optional<AddonService> byId = addonServiceRepository.findById(numId);
            if (byId.isPresent()) return byId;
        } catch (NumberFormatException ignored) {}
        return addonServiceRepository.findByAddonId(identifier);
    }
}
