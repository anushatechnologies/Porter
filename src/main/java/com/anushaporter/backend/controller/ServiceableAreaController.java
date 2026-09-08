package com.anushaporter.backend.controller;

import com.anushaporter.backend.model.ServiceableArea;
import com.anushaporter.backend.service.ServiceableAreaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@CrossOrigin(originPatterns = "*")
public class ServiceableAreaController {

    @Autowired
    private ServiceableAreaService serviceableAreaService;

    // ─── ADMIN ENDPOINTS ─────────────────────────────────────────────────────

    /**
     * Admin: Fetch all areas & pincodes for a city with serviceable toggle status.
     * GET /api/admin/serviceable-areas?city=Hyderabad
     */
    @GetMapping({"/api/admin/serviceable-areas", "/api/serviceable-areas"})
    public ResponseEntity<Map<String, Object>> getAreasByCity(@RequestParam(name = "city", required = false, defaultValue = "Hyderabad") String city) {
        List<ServiceableArea> areas = serviceableAreaService.getAreasByCity(city);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("success", true);
        resp.put("city", city);
        resp.put("count", areas.size());
        resp.put("areas", areas);
        return ResponseEntity.ok(resp);
    }

    /**
     * Admin: Add a new area and pincode to a city.
     * POST /api/admin/serviceable-areas
     */
    @PostMapping("/api/admin/serviceable-areas")
    public ResponseEntity<Map<String, Object>> addArea(@RequestBody ServiceableArea area) {
        if (area.getCity() == null || area.getCity().isBlank()) {
            area.setCity("Hyderabad");
        }
        if (area.getAreaName() == null || area.getAreaName().isBlank() || area.getPincode() == null || area.getPincode().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "areaName and pincode are required."));
        }
        ServiceableArea saved = serviceableAreaService.saveArea(area);
        return ResponseEntity.ok(Map.of("success", true, "message", "Serviceable area added successfully.", "area", saved));
    }

    /**
     * Admin: Toggle individual area serviceable status.
     * PUT /api/admin/serviceable-areas/{id}/toggle
     */
    @PutMapping("/api/admin/serviceable-areas/{id}/toggle")
    public ResponseEntity<Map<String, Object>> toggleArea(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Object> body) {
        Boolean active = null;
        if (body != null && body.containsKey("isServiceable")) {
            active = Boolean.TRUE.equals(body.get("isServiceable"));
        }
        Optional<ServiceableArea> updated = serviceableAreaService.toggleAreaStatus(id, active);
        if (updated.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("success", false, "message", "Serviceable area not found with ID: " + id));
        }
        return ResponseEntity.ok(Map.of("success", true, "message", "Area serviceable status updated.", "area", updated.get()));
    }

    /**
     * Admin: Multi-select / Bulk update approved areas & pincodes for a city.
     * POST /api/admin/serviceable-areas/bulk-update
     * Body: { "city": "Hyderabad", "activePincodes": ["500081", "500086", "500032"] }
     */
    @PostMapping("/api/admin/serviceable-areas/bulk-update")
    public ResponseEntity<Map<String, Object>> bulkUpdatePincodes(@RequestBody Map<String, Object> payload) {
        String city = (String) payload.getOrDefault("city", "Hyderabad");

        @SuppressWarnings("unchecked")
        List<String> activePincodes = (List<String>) payload.get("activePincodes");
        if (activePincodes == null && payload.get("approvedPincodes") instanceof List) {
            @SuppressWarnings("unchecked")
            List<String> list = (List<String>) payload.get("approvedPincodes");
            activePincodes = list;
        }

        Map<String, Object> result = serviceableAreaService.bulkUpdateServiceablePincodes(city, activePincodes);
        return ResponseEntity.ok(result);
    }

    // ─── USER APP / MAP ENDPOINTS ────────────────────────────────────────────

    /**
     * User App: Fetch only active approved areas for city dropdown or map boundaries.
     * GET /api/serviceable-areas/active?city=Hyderabad
     */
    @GetMapping("/api/serviceable-areas/active")
    public ResponseEntity<Map<String, Object>> getActiveAreas(@RequestParam(name = "city", required = false, defaultValue = "Hyderabad") String city) {
        List<ServiceableArea> activeAreas = serviceableAreaService.getActiveAreasByCity(city);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("success", true);
        resp.put("city", city);
        resp.put("count", activeAreas.size());
        resp.put("areas", activeAreas);
        return ResponseEntity.ok(resp);
    }

    /**
     * User App: Validate dropped map pin or entered pincode.
     * POST /api/location/validate-serviceable
     * Body: { "lat": 17.4486, "lng": 78.3808, "pincode": "500081", "city": "Hyderabad" }
     */
    @PostMapping("/api/location/validate-serviceable")
    public ResponseEntity<Map<String, Object>> validateServiceablePost(@RequestBody Map<String, Object> payload) {
        Double lat = payload.get("lat") != null ? ((Number) payload.get("lat")).doubleValue() : null;
        if (lat == null && payload.get("latitude") != null) lat = ((Number) payload.get("latitude")).doubleValue();

        Double lng = payload.get("lng") != null ? ((Number) payload.get("lng")).doubleValue() : null;
        if (lng == null && payload.get("longitude") != null) lng = ((Number) payload.get("longitude")).doubleValue();

        String pincode = (String) payload.get("pincode");
        if (pincode == null) pincode = (String) payload.get("postalCode");

        String city = (String) payload.getOrDefault("city", "Hyderabad");

        Map<String, Object> result = serviceableAreaService.validateLocation(lat, lng, pincode, city);
        return ResponseEntity.ok(result);
    }

    /**
     * User App: Validate location via query params (GET).
     * GET /api/location/validate-serviceable?lat=17.4486&lng=78.3808&pincode=500081&city=Hyderabad
     */
    @GetMapping("/api/location/validate-serviceable")
    public ResponseEntity<Map<String, Object>> validateServiceableGet(
            @RequestParam(name = "lat", required = false) Double lat,
            @RequestParam(name = "lng", required = false) Double lng,
            @RequestParam(name = "pincode", required = false) String pincode,
            @RequestParam(name = "city", required = false, defaultValue = "Hyderabad") String city) {
        Map<String, Object> result = serviceableAreaService.validateLocation(lat, lng, pincode, city);
        return ResponseEntity.ok(result);
    }
}
