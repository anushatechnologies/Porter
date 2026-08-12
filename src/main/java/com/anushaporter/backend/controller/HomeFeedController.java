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
    @Autowired private BannerRepository bannerRepository;
    @Autowired private JwtUtil jwtUtil;

    @GetMapping
    public ResponseEntity<Map<String, Object>> getHomeFeed(HttpServletRequest request) {
        Map<String, Object> response = new HashMap<>();

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
            // Prefer "home" tagged address, otherwise use the first one
            Optional<SavedAddress> homeAddr = addresses.stream()
                    .filter(a -> "home".equalsIgnoreCase(a.getTag()))
                    .findFirst();
            SavedAddress addr = homeAddr.orElse(addresses.isEmpty() ? null : addresses.get(0));
            if (addr != null) {
                defaultPickup = new HashMap<>();
                defaultPickup.put("id", "addr_" + addr.getId());
                defaultPickup.put("label", addr.getLabel());
                defaultPickup.put("tag", addr.getTag());
                defaultPickup.put("addressLine", addr.getAddressLine());
                defaultPickup.put("lat", addr.getLat());
                defaultPickup.put("lng", addr.getLng());
            }
        }

        // ── Active Services (vehicle types) ──────────────────────────────────
        List<PricingVehicle> vehicles = vehicleRepository.findByStatus(true);
        List<Map<String, Object>> services = vehicles.stream().map(v -> {
            Map<String, Object> svc = new HashMap<>();
            svc.put("vehicleId", v.getVehicleId());
            svc.put("name", v.getName());
            svc.put("description", v.getCapacityKg() != null ? "Up to " + v.getCapacityKg().intValue() + " kg" : "");
            svc.put("iconUrl", v.getImageUrl() != null ? v.getImageUrl() : (v.getIcon() != null ? v.getIcon() : ""));
            svc.put("baseFare", v.getBaseFare() != null ? v.getBaseFare() : 0.0);
            svc.put("pricePerKm", v.getPricePerKm() != null ? v.getPricePerKm() : 0.0);
            return svc;
        }).collect(Collectors.toList());

        // If no vehicles configured yet, return sensible defaults
        if (services.isEmpty()) {
            services = List.of(
                    Map.of("vehicleId", "2-wheeler", "name", "2 Wheeler",
                            "description", "Up to 20 kg", "iconUrl", "", "baseFare", 40.0, "pricePerKm", 10.0),
                    Map.of("vehicleId", "mini-truck", "name", "Mini Truck",
                            "description", "Up to 500 kg", "iconUrl", "", "baseFare", 200.0, "pricePerKm", 15.0),
                    Map.of("vehicleId", "full-truck", "name", "Full Truck",
                            "description", "Up to 2000 kg", "iconUrl", "", "baseFare", 500.0, "pricePerKm", 20.0)
            );
        }

        // ── Announcements / Banners ───────────────────────────────────────────
        List<Map<String, Object>> announcements = bannerRepository
                .findByActiveTrueOrderByDisplayOrderAsc()
                .stream()
                .map(b -> {
                    Map<String, Object> ann = new HashMap<>();
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
            userProfile = new HashMap<>();
            userProfile.put("id", user.getId());
            userProfile.put("name", user.getName() != null ? user.getName() : "");
            userProfile.put("phone", user.getPhone() != null ? user.getPhone() : "");
            userProfile.put("walletBalance", user.getWalletBalance() != null ? user.getWalletBalance() : 0.0);
        }

        // ── Assemble Response ─────────────────────────────────────────────────
        response.put("success", true);
        response.put("defaultPickupAddress", defaultPickup);
        response.put("services", services);
        response.put("announcements", announcements);
        if (userProfile != null) response.put("user", userProfile);

        return ResponseEntity.ok(response);
    }
}
