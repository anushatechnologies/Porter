package com.anushaporter.backend.controller;

import com.anushaporter.backend.model.Banner;
import com.anushaporter.backend.repository.BannerRepository;
import com.anushaporter.backend.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Manages promotional banners.
 *
 * Customer App:
 *   GET  /api/banners              – fetch all active banners
 *
 * Admin Panel:
 *   GET  /api/admin/banners        – fetch all banners (including inactive)
 *   POST /api/admin/banners        – create a banner
 *   PUT  /api/admin/banners/{id}   – update a banner
 *   DELETE /api/admin/banners/{id} – delete a banner
 */
@RestController
public class BannerController {

    @Autowired
    private BannerRepository bannerRepository;

    @Autowired
    private JwtUtil jwtUtil;

    // ─── Customer-facing ─────────────────────────────────────────────────────

    /**
     * GET /api/banners
     * Returns active promotional banners ordered by display priority.
     */
    @GetMapping("/api/banners")
    public ResponseEntity<Map<String, Object>> getActiveBanners() {
        List<Map<String, Object>> items = bannerRepository
                .findByActiveTrueOrderByDisplayOrderAsc()
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

        return ResponseEntity.ok(Map.of(
                "success", true,
                "banners", items
        ));
    }

    // ─── Admin-facing ─────────────────────────────────────────────────────────

    /** GET /api/admin/banners – all banners */
    @GetMapping("/api/admin/banners")
    public ResponseEntity<Map<String, Object>> getAllBanners() {
        List<Map<String, Object>> items = bannerRepository
                .findAll()
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

        return ResponseEntity.ok(Map.of("success", true, "banners", items));
    }

    /** POST /api/admin/banners – create banner */
    @PostMapping("/api/admin/banners")
    public ResponseEntity<Map<String, Object>> createBanner(@RequestBody Map<String, Object> body) {
        Banner banner = fromBody(new Banner(), body);
        bannerRepository.save(banner);
        return ResponseEntity.ok(Map.of("success", true, "banner", toResponse(banner)));
    }

    /** PUT /api/admin/banners/{id} – update banner */
    @PutMapping("/api/admin/banners/{id}")
    public ResponseEntity<Map<String, Object>> updateBanner(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {

        return bannerRepository.findById(id).map(banner -> {
            fromBody(banner, body);
            bannerRepository.save(banner);
            return ResponseEntity.ok(Map.of("success", true, "banner", toResponse(banner)));
        }).orElse(ResponseEntity.notFound().build());
    }

    /** DELETE /api/admin/banners/{id} – delete banner */
    @DeleteMapping("/api/admin/banners/{id}")
    public ResponseEntity<Map<String, Object>> deleteBanner(@PathVariable Long id) {
        if (!bannerRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        bannerRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("success", true));
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private Banner fromBody(Banner banner, Map<String, Object> body) {
        if (body.containsKey("title"))        banner.setTitle((String) body.get("title"));
        if (body.containsKey("imageUrl"))     banner.setImageUrl((String) body.get("imageUrl"));
        if (body.containsKey("targetAction")) banner.setTargetAction((String) body.get("targetAction"));
        if (body.containsKey("targetValue"))  banner.setTargetValue((String) body.get("targetValue"));
        if (body.get("active") != null)       banner.setActive(Boolean.TRUE.equals(body.get("active")));
        if (body.get("displayOrder") != null) banner.setDisplayOrder(((Number) body.get("displayOrder")).intValue());
        return banner;
    }

    private Map<String, Object> toResponse(Banner b) {
        return Map.of(
                "id", b.getId(),
                "title", b.getTitle() != null ? b.getTitle() : "",
                "imageUrl", b.getImageUrl() != null ? b.getImageUrl() : "",
                "targetAction", b.getTargetAction() != null ? b.getTargetAction() : "",
                "targetValue", b.getTargetValue() != null ? b.getTargetValue() : "",
                "active", Boolean.TRUE.equals(b.getActive()),
                "displayOrder", b.getDisplayOrder() != null ? b.getDisplayOrder() : 0
        );
    }
}
