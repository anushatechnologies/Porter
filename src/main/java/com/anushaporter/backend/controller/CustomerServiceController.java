package com.anushaporter.backend.controller;

import com.anushaporter.backend.model.PorterService;
import com.anushaporter.backend.model.ServiceCategory;
import com.anushaporter.backend.repository.PorterServiceRepository;
import com.anushaporter.backend.repository.ServiceCategoryRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/customer/services")
public class CustomerServiceController {

    @Autowired
    private ServiceCategoryRepository categoryRepository;

    @Autowired
    private PorterServiceRepository serviceRepository;

    @PostConstruct
    public void initSeedData() {
        if (categoryRepository.count() == 0) {
            List<ServiceCategory> defaultCategories = getDefaultCategories();
            categoryRepository.saveAll(defaultCategories);
            System.out.println("Seeded " + defaultCategories.size() + " default Service Categories into the database.");
        }

        if (serviceRepository.count() == 0) {
            List<PorterService> defaultServices = PorterServiceController.getDefaultFallbackServices();
            serviceRepository.saveAll(defaultServices);
            System.out.println("Seeded " + defaultServices.size() + " default Porter Services into the database.");
        }
    }

    /**
     * GET /api/customer/services
     * Returns all active categories with their active, customer-visible services grouped together and sorted by displayOrder.
     */
    @GetMapping
    public ResponseEntity<?> getCustomerServices() {
        // 1. Fetch active categories
        List<ServiceCategory> categories = categoryRepository.findByIsActiveTrueOrderByDisplayOrderAsc();
        if (categories.isEmpty()) {
            categories = getDefaultCategories();
        }

        // 2. Fetch active and customer visible services
        List<PorterService> services = serviceRepository.findByIsActiveTrueAndCustomerAppVisibleTrueOrderByDisplayOrderAsc();
        if (services.isEmpty()) {
            services = PorterServiceController.getDefaultFallbackServices().stream()
                    .filter(s -> !Boolean.FALSE.equals(s.getCustomerAppVisible()) && !Boolean.FALSE.equals(s.getIsActive()))
                    .collect(Collectors.toList());
        }

        // Map categories and group services
        List<Map<String, Object>> resultCategories = new ArrayList<>();

        for (ServiceCategory cat : categories) {
            String catIdStr = cat.getId() != null ? String.valueOf(cat.getId()) : "1";
            String catSlug = cat.getSlug() != null ? cat.getSlug().toLowerCase() : "";

            // Find matching services
            List<Map<String, Object>> catServices = services.stream()
                    .filter(s -> matchesCategory(s, catIdStr, catSlug))
                    .sorted(Comparator.comparingInt(s -> s.getDisplayOrder() != null ? s.getDisplayOrder() : 1))
                    .map(s -> formatServiceForCustomer(s, catIdStr, cat.getName()))
                    .collect(Collectors.toList());

            // 3. Filter out any category with 0 visible services
            if (!catServices.isEmpty()) {
                Map<String, Object> catMap = new LinkedHashMap<>();
                catMap.put("id", catIdStr);
                catMap.put("name", cat.getName() != null ? cat.getName() : "");
                catMap.put("slug", cat.getSlug() != null ? cat.getSlug() : "");
                catMap.put("icon", cat.getIcon() != null ? cat.getIcon() : "truck-fast");
                catMap.put("displayOrder", cat.getDisplayOrder() != null ? cat.getDisplayOrder() : 1);
                catMap.put("services", catServices);
                resultCategories.add(catMap);
            }
        }

        // Return structured JSON response
        return ResponseEntity.ok(Map.of(
                "success", true,
                "categories", resultCategories
        ));
    }

    private boolean matchesCategory(PorterService s, String categoryId, String categorySlug) {
        // Direct categoryId match
        if (s.getCategoryId() != null && s.getCategoryId().equalsIgnoreCase(categoryId)) {
            return true;
        }
        if (s.getCategoryId() != null && s.getCategoryId().equalsIgnoreCase(categorySlug)) {
            return true;
        }

        // Slug / category string match fallback
        String svcCat = s.getCategory() != null ? s.getCategory().toLowerCase() : "";
        if (categorySlug.contains("truck") || categorySlug.contains("fleet") || "1".equals(categoryId)) {
            return svcCat.contains("vehicle") || svcCat.contains("truck") || svcCat.contains("fleet") || "1".equalsIgnoreCase(s.getCategoryId());
        } else if (categorySlug.contains("bike") || categorySlug.contains("2-wheeler") || "2".equals(categoryId)) {
            return svcCat.contains("two_wheeler") || svcCat.contains("bike") || svcCat.contains("2_wheeler") || "2".equalsIgnoreCase(s.getCategoryId());
        } else if (categorySlug.contains("packer") || "3".equals(categoryId)) {
            return svcCat.contains("packer") || "3".equalsIgnoreCase(s.getCategoryId());
        }

        return svcCat.equalsIgnoreCase(categorySlug);
    }

    private Map<String, Object> formatServiceForCustomer(PorterService s, String fallbackCatId, String fallbackCatName) {
        Map<String, Object> map = new LinkedHashMap<>();
        String effectiveCatId = (s.getCategoryId() != null && !s.getCategoryId().isBlank()) ? s.getCategoryId() : fallbackCatId;
        String effectiveCatName = (s.getCategoryName() != null && !s.getCategoryName().isBlank()) ? s.getCategoryName() : fallbackCatName;

        map.put("id", s.getServiceId() != null ? s.getServiceId() : ("service-" + s.getId()));
        map.put("name", s.getName() != null ? s.getName() : "");
        map.put("categoryId", effectiveCatId);
        map.put("categoryName", effectiveCatName);
        map.put("description", s.getDescription() != null ? s.getDescription() : (s.getSubtitle() != null ? s.getSubtitle() : ""));
        map.put("imageUrl", s.getIconUrl() != null ? s.getIconUrl() : "");
        map.put("capacity", s.getCapacityLabel() != null ? s.getCapacityLabel() : (s.getCapacityKg() != null ? s.getCapacityKg() + " kg" : ""));
        map.put("capacityKg", s.getCapacityKg() != null ? s.getCapacityKg() : 0);
        map.put("basePrice", s.getBaseFare() != null ? s.getBaseFare() : 0.0);
        map.put("perKmRate", s.getPerKmRate() != null ? s.getPerKmRate() : 0.0);
        map.put("eta", s.getEtaLabel() != null ? s.getEtaLabel() : "5 mins");
        map.put("displayOrder", s.getDisplayOrder() != null ? s.getDisplayOrder() : 1);
        map.put("customerAppVisible", !Boolean.FALSE.equals(s.getCustomerAppVisible()));
        map.put("isActive", !Boolean.FALSE.equals(s.getIsActive()));
        return map;
    }

    public static List<ServiceCategory> getDefaultCategories() {
        List<ServiceCategory> list = new ArrayList<>();

        ServiceCategory c1 = new ServiceCategory();
        c1.setName("Porter Trucks & Fleet");
        c1.setSlug("porter-trucks-fleet");
        c1.setIcon("truck-fast");
        c1.setDescription("Mini trucks, tempos, and commercial flatbeds for goods & house shifting");
        c1.setDisplayOrder(1);
        c1.setIsActive(true);
        list.add(c1);

        ServiceCategory c2 = new ServiceCategory();
        c2.setName("2 Wheeler / Bike");
        c2.setSlug("2-wheeler-bike");
        c2.setIcon("motorbike");
        c2.setDescription("Instant courier and parcel delivery up to 20 Kg");
        c2.setDisplayOrder(2);
        c2.setIsActive(true);
        list.add(c2);

        ServiceCategory c3 = new ServiceCategory();
        c3.setName("Packers & Movers");
        c3.setSlug("packers-movers");
        c3.setIcon("truck-moving");
        c3.setDescription("Full-service household and office shifting with packing & loading");
        c3.setDisplayOrder(3);
        c3.setIsActive(true);
        list.add(c3);

        return list;
    }
}
