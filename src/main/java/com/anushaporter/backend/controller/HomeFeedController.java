package com.anushaporter.backend.controller;

import com.anushaporter.backend.model.AppUser;
import com.anushaporter.backend.model.Banner;
import com.anushaporter.backend.model.PricingVehicle;
import com.anushaporter.backend.model.SavedAddress;
import com.anushaporter.backend.repository.AppUserRepository;
import com.anushaporter.backend.repository.BannerRepository;
import com.anushaporter.backend.repository.PricingVehicleRepository;
import com.anushaporter.backend.repository.SavedAddressRepository;
import com.anushaporter.backend.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Powers the main Home screen of the customer app.
 *
 * GET /api/home  – returns:
 *   - defaultPickupAddress  (last used / first saved address)
 *   - services              (active vehicle types with pricing info)
 *   - announcements         (active banners / promo content)
 *   - user                  (quick profile summary)
 */
@RestController
@RequestMapping("/api/home")
public class HomeFeedController {

    @Autowired private AppUserRepository userRepository;
    @Autowired private SavedAddressRepository addressRepository;
    @Autowired private PricingVehicleRepository vehicleRepository;
    @Autowired private com.anushaporter.backend.repository.PorterServiceRepository porterServiceRepository;
    @Autowired private BannerRepository bannerRepository;
    @Autowired private JwtUtil jwtUtil;

    @GetMapping
    public ResponseEntity<Map<String, Object>> getHomeFeed(HttpServletRequest request) {
        Map<String, Object> response = new LinkedHashMap<>();

        // ── Auth ──────────────────────────────────────────────────────────────
        String authHeader = request.getHeader("Authorization");
        AppUser user = null;
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            try {
                String email = jwtUtil.getUsernameFromToken(authHeader.substring(7));
                if (email != null) {
                    user = userRepository.findFirstByEmailOrderByIdDesc(email).orElse(null);
                }
            } catch (Exception ignored) { }
        }

        // ── Default Pickup Address ────────────────────────────────────────────
        Map<String, Object> defaultPickup = null;
        if (user != null) {
            List<SavedAddress> addresses = addressRepository.findByUserEmailOrderByCreatedAtDesc(user.getEmail());
            Optional<SavedAddress> homeAddr = addresses.stream()
                    .filter(a -> "home".equalsIgnoreCase(a.getTag()))
                    .findFirst();
            SavedAddress addr = homeAddr.orElse(addresses.isEmpty() ? null : addresses.get(0));
            if (addr != null) {
                defaultPickup = new LinkedHashMap<>();
                defaultPickup.put("id", "addr_" + addr.getId());
                defaultPickup.put("label", addr.getLabel());
                defaultPickup.put("tag", addr.getTag());
                defaultPickup.put("addressLine", addr.getAddressLine());
                defaultPickup.put("lat", addr.getLat());
                defaultPickup.put("lng", addr.getLng());
            }
        }

        // ── Dynamic "Our Services" & Fleet ─────────────────────────────────────
        List<com.anushaporter.backend.model.PorterService> dynamicServices = porterServiceRepository.findByIsActiveTrueOrderByDisplayOrderAsc();
        List<Map<String, Object>> featuredServices = new ArrayList<>();

        if (!dynamicServices.isEmpty()) {
            for (com.anushaporter.backend.model.PorterService s : dynamicServices) {
                String catId = s.getCategoryId() != null && !s.getCategoryId().isBlank() ? s.getCategoryId() : inferCategoryId(s.getCategory());
                String catName = s.getCategoryName() != null && !s.getCategoryName().isBlank() ? s.getCategoryName() : inferCategoryName(s.getCategory());

                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", s.getServiceId() != null ? s.getServiceId() : "service-" + s.getId());
                item.put("serviceId", s.getServiceId() != null ? s.getServiceId() : "service-" + s.getId());
                item.put("name", s.getName() != null ? s.getName() : "");
                item.put("label", s.getLabel() != null ? s.getLabel() : (s.getName() != null ? s.getName() : ""));
                item.put("category", s.getCategory() != null ? s.getCategory() : "vehicle");
                item.put("categoryId", catId);
                item.put("categoryName", catName);
                item.put("subtitle", s.getSubtitle() != null ? s.getSubtitle() : "");
                item.put("description", s.getDescription() != null ? s.getDescription() : (s.getSubtitle() != null ? s.getSubtitle() : ""));
                item.put("baseFare", s.getBaseFare() != null ? s.getBaseFare() : 0.0);
                item.put("basePrice", s.getBaseFare() != null ? s.getBaseFare() : 0.0);
                item.put("baseKm", s.getBaseKm() != null ? s.getBaseKm() : 2.0);
                item.put("perKmRate", s.getPerKmRate() != null ? s.getPerKmRate() : 0.0);
                item.put("pricePerKm", s.getPerKmRate() != null ? s.getPerKmRate() : 0.0);
                item.put("helperRate", s.getHelperRate() != null ? s.getHelperRate() : 0.0);
                item.put("capacityKg", s.getCapacityKg() != null ? s.getCapacityKg() : 0);
                item.put("capacity", s.getCapacityLabel() != null ? s.getCapacityLabel() : (s.getCapacityKg() != null ? s.getCapacityKg() + " Kg" : ""));
                item.put("capacityLabel", s.getCapacityLabel() != null ? s.getCapacityLabel() : "");
                item.put("dimensions", s.getDimensions());
                item.put("eta", s.getEtaLabel() != null ? s.getEtaLabel() : "5-10 mins");
                item.put("etaLabel", s.getEtaLabel() != null ? s.getEtaLabel() : "5-10 mins");
                item.put("imageUrl", s.getIconUrl() != null ? s.getIconUrl() : "");
                item.put("iconUrl", s.getIconUrl() != null ? s.getIconUrl() : "");
                item.put("bgTint", s.getBgTint() != null ? s.getBgTint() : "#EEF4FF");
                item.put("customerAppVisible", !Boolean.FALSE.equals(s.getCustomerAppVisible()));
                item.put("isActive", Boolean.TRUE.equals(s.getIsActive()));
                item.put("order", s.getDisplayOrder() != null ? s.getDisplayOrder() : 1);
                item.put("displayOrder", s.getDisplayOrder() != null ? s.getDisplayOrder() : 1);
                featuredServices.add(item);
            }
        } else {
            // Fallback to PricingVehicle or default list
            List<PricingVehicle> vehicles = vehicleRepository.findByStatus(true);
            if (!vehicles.isEmpty()) {
                int order = 1;
                for (PricingVehicle v : vehicles) {
                    Map<String, Object> svc = new LinkedHashMap<>();
                    svc.put("id", v.getVehicleId());
                    svc.put("serviceId", v.getVehicleId());
                    svc.put("name", v.getName());
                    svc.put("label", v.getName());
                    svc.put("category", "vehicle");
                    svc.put("categoryId", "1");
                    svc.put("categoryName", "Porter Trucks & Fleet");
                    svc.put("subtitle", v.getCapacityKg() != null ? "Up to " + v.getCapacityKg().intValue() + " kg" : "");
                    svc.put("description", v.getCapacityKg() != null ? "Up to " + v.getCapacityKg().intValue() + " kg" : "");
                    svc.put("baseFare", v.getBaseFare() != null ? v.getBaseFare() : 0.0);
                    svc.put("basePrice", v.getBaseFare() != null ? v.getBaseFare() : 0.0);
                    svc.put("perKmRate", v.getPricePerKm() != null ? v.getPricePerKm() : 0.0);
                    svc.put("pricePerKm", v.getPricePerKm() != null ? v.getPricePerKm() : 0.0);
                    svc.put("capacityKg", v.getCapacityKg() != null ? v.getCapacityKg().intValue() : 500);
                    svc.put("capacity", v.getCapacityKg() != null ? v.getCapacityKg().intValue() + " kg" : "500 kg");
                    svc.put("imageUrl", v.getImageUrl() != null ? v.getImageUrl() : (v.getIcon() != null ? v.getIcon() : ""));
                    svc.put("iconUrl", v.getImageUrl() != null ? v.getImageUrl() : (v.getIcon() != null ? v.getIcon() : ""));
                    svc.put("eta", "5 mins");
                    svc.put("customerAppVisible", true);
                    svc.put("isActive", true);
                    svc.put("order", order);
                    svc.put("displayOrder", order);
                    order++;
                    featuredServices.add(svc);
                }
            } else {
                featuredServices = PorterServiceController.getDefaultFallbackServices().stream()
                        .map(s -> {
                            Map<String, Object> item = new LinkedHashMap<>();
                            item.put("id", s.getServiceId());
                            item.put("serviceId", s.getServiceId());
                            item.put("name", s.getName());
                            item.put("label", s.getLabel());
                            item.put("category", s.getCategory());
                            item.put("categoryId", s.getCategoryId() != null ? s.getCategoryId() : inferCategoryId(s.getCategory()));
                            item.put("categoryName", s.getCategoryName() != null ? s.getCategoryName() : inferCategoryName(s.getCategory()));
                            item.put("subtitle", s.getSubtitle());
                            item.put("description", s.getDescription());
                            item.put("baseFare", s.getBaseFare());
                            item.put("basePrice", s.getBaseFare());
                            item.put("baseKm", s.getBaseKm());
                            item.put("perKmRate", s.getPerKmRate());
                            item.put("pricePerKm", s.getPerKmRate());
                            item.put("helperRate", s.getHelperRate());
                            item.put("capacityKg", s.getCapacityKg());
                            item.put("capacity", s.getCapacityLabel());
                            item.put("capacityLabel", s.getCapacityLabel());
                            item.put("eta", s.getEtaLabel());
                            item.put("etaLabel", s.getEtaLabel());
                            item.put("imageUrl", s.getIconUrl());
                            item.put("iconUrl", s.getIconUrl());
                            item.put("customerAppVisible", !Boolean.FALSE.equals(s.getCustomerAppVisible()));
                            item.put("isActive", Boolean.TRUE.equals(s.getIsActive()));
                            item.put("order", s.getDisplayOrder());
                            item.put("displayOrder", s.getDisplayOrder());
                            return item;
                        })
                        .collect(Collectors.toList());
            }
        }

        // ── Announcements / Banners ───────────────────────────────────────────
        List<Map<String, Object>> announcements = bannerRepository
                .findByActiveTrueOrderByDisplayOrderAsc()
                .stream()
                .map(b -> {
                    Map<String, Object> ann = new LinkedHashMap<>();
                    ann.put("id", b.getId());
                    ann.put("title", b.getTitle() != null ? b.getTitle() : "");
                    ann.put("imageUrl", b.getImageUrl() != null ? b.getImageUrl() : "");
                    ann.put("targetAction", b.getTargetAction() != null ? b.getTargetAction() : "");
                    ann.put("targetValue", b.getTargetValue() != null ? b.getTargetValue() : "");
                    return ann;
                })
                .collect(Collectors.toList());

        // ── Quick User Profile ─────────────────────────────────────────────────
        Map<String, Object> userProfile = null;
        if (user != null) {
            userProfile = new LinkedHashMap<>();
            userProfile.put("id", user.getId());
            userProfile.put("name", user.getName() != null ? user.getName() : "");
            userProfile.put("phone", user.getPhone() != null ? user.getPhone() : "");
            userProfile.put("walletBalance", user.getWalletBalance() != null ? user.getWalletBalance() : 0.0);
        }

        // ── Assemble Response ─────────────────────────────────────────────────
        response.put("success", true);
        response.put("defaultPickupAddress", defaultPickup);
        response.put("featuredServices", featuredServices);
        response.put("services", featuredServices);
        response.put("announcements", announcements);
        if (userProfile != null) response.put("user", userProfile);

        return ResponseEntity.ok(response);
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
}
