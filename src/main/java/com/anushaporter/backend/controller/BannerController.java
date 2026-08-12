package com.anushaporter.backend.controller;

import com.anushaporter.backend.model.Banner;
import com.anushaporter.backend.repository.BannerRepository;
import com.anushaporter.backend.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Promotional Banners API (GET, POST, DELETE /api/banners & /api/admin/banners).
 */
@RestController
public class BannerController {

    @Autowired
    private BannerRepository bannerRepository;

    @Autowired
    private JwtUtil jwtUtil;

    /**
     * GET /api/banners
     * Returns list of banners for mobile app & admin panel.
     */
    @GetMapping({"/api/banners", "/api/admin/banners"})
    public ResponseEntity<?> getBanners() {
        List<Banner> banners = bannerRepository.findAll();

        if (banners.isEmpty()) {
            // Seed a default promo banner if empty
            Banner b = new Banner();
            b.setTitle("Monsoon Offer");
            b.setImageUrl("https://poteranusha.s3.ap-south-2.amazonaws.com/banners/promo1.png");
            b.setActive(true);
            bannerRepository.save(b);
            banners = List.of(b);
        }

        List<Map<String, Object>> items = banners.stream().map(b -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", "BNR-" + (1000 + b.getId()));
            map.put("bannerId", b.getId());
            map.put("title", b.getTitle() != null ? b.getTitle() : "Promotional Offer");
            map.put("imageUrl", b.getImageUrl() != null ? b.getImageUrl() : "");
            map.put("isActive", Boolean.TRUE.equals(b.getActive()));
            map.put("active", Boolean.TRUE.equals(b.getActive()));
            map.put("targetAction", b.getTargetAction() != null ? b.getTargetAction() : "promo");
            map.put("targetValue", b.getTargetValue() != null ? b.getTargetValue() : "");
            return map;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(items);
    }

    /**
     * POST /api/banners & POST /api/admin/banners
     * Creates new promotional banner.
     */
    @PostMapping({"/api/banners", "/api/admin/banners"})
    public ResponseEntity<Map<String, Object>> createBanner(@RequestBody Map<String, Object> body) {
        Banner banner = fromBody(new Banner(), body);
        bannerRepository.save(banner);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("id", "BNR-" + (1000 + banner.getId()));
        response.put("bannerId", banner.getId());
        response.put("title", banner.getTitle());
        response.put("imageUrl", banner.getImageUrl());
        response.put("isActive", Boolean.TRUE.equals(banner.getActive()));

        return ResponseEntity.ok(response);
    }

    /**
     * PUT /api/admin/banners/{id} & PUT /api/banners/{id}
     */
    @PutMapping({"/api/banners/{id}", "/api/admin/banners/{id}"})
    public ResponseEntity<Map<String, Object>> updateBanner(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {

        return bannerRepository.findById(id).map(banner -> {
            fromBody(banner, body);
            bannerRepository.save(banner);
            return ResponseEntity.ok(Map.of("success", true, "banner", banner));
        }).orElse(ResponseEntity.notFound().build());
    }

    /**
     * DELETE /api/banners/{id} & DELETE /api/admin/banners/{id}
     */
    @DeleteMapping({"/api/banners/{id}", "/api/admin/banners/{id}"})
    public ResponseEntity<Map<String, Object>> deleteBanner(@PathVariable Long id) {
        if (!bannerRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        bannerRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("success", true, "message", "Banner deleted successfully."));
    }

    private Banner fromBody(Banner banner, Map<String, Object> body) {
        if (body.containsKey("title"))        banner.setTitle((String) body.get("title"));
        if (body.containsKey("imageUrl"))     banner.setImageUrl((String) body.get("imageUrl"));
        if (body.containsKey("targetAction")) banner.setTargetAction((String) body.get("targetAction"));
        if (body.containsKey("targetValue"))  banner.setTargetValue((String) body.get("targetValue"));
        if (body.get("isActive") != null)     banner.setActive(Boolean.TRUE.equals(body.get("isActive")));
        if (body.get("active") != null)       banner.setActive(Boolean.TRUE.equals(body.get("active")));
        if (body.get("displayOrder") != null) banner.setDisplayOrder(((Number) body.get("displayOrder")).intValue());
        return banner;
    }
}
