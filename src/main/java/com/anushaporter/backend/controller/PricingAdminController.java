package com.anushaporter.backend.controller;

import com.anushaporter.backend.model.*;
import com.anushaporter.backend.repository.*;
import com.anushaporter.backend.service.AdminAuthService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/admin/pricing")
public class PricingAdminController {

    @Autowired private PricingVehicleRepository vehicleRepo;
    @Autowired private DistanceSlabRepository distanceRepo;
    @Autowired private WeightSlabRepository weightRepo;
    @Autowired private GlobalSettingsRepository settingsRepo;
    @Autowired private PricingHistoryRepository historyRepo;
    @Autowired private AdminAuthService adminAuthService;

    // --- VEHICLES ---
    @GetMapping("/vehicles")
    public List<PricingVehicle> getVehicles() {
        return vehicleRepo.findAll();
    }

    @PostMapping("/vehicles")
    public ResponseEntity<?> addVehicle(@RequestBody PricingVehicle vehicle, HttpServletRequest request) {
        var auth = adminAuthService.verifyAdmin(request);
        if (!auth.isAuthorized()) {
            return ResponseEntity.status(auth.getStatusCode()).body(Map.of(
                    "success", false,
                    "error", auth.getStatusCode() == 401 ? "Unauthorized" : "Forbidden",
                    "message", auth.getMessage()
            ));
        }

        String adminName = adminAuthService.getAdminIdentifier(request);
        logHistory(adminName, vehicle.getVehicleId(), "-", String.valueOf(vehicle.getBaseFare()), "Added Vehicle", "-");
        PricingVehicle saved = vehicleRepo.save(vehicle);
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/vehicles/{id}")
    public ResponseEntity<?> updateVehicle(@PathVariable Long id, @RequestBody PricingVehicle vehicle, HttpServletRequest request) {
        var auth = adminAuthService.verifyAdmin(request);
        if (!auth.isAuthorized()) {
            return ResponseEntity.status(auth.getStatusCode()).body(Map.of(
                    "success", false,
                    "error", auth.getStatusCode() == 401 ? "Unauthorized" : "Forbidden",
                    "message", auth.getMessage()
            ));
        }

        String adminName = adminAuthService.getAdminIdentifier(request);
        return vehicleRepo.findById(id).map(v -> {
            logHistory(adminName, v.getVehicleId(), String.valueOf(v.getBaseFare()), String.valueOf(vehicle.getBaseFare()), "Updated Vehicle", "-");
            vehicle.setId(id);
            return ResponseEntity.ok(vehicleRepo.save(vehicle));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/vehicles/{id}")
    public ResponseEntity<?> deleteVehicle(@PathVariable Long id, HttpServletRequest request) {
        var auth = adminAuthService.verifyAdmin(request);
        if (!auth.isAuthorized()) {
            return ResponseEntity.status(auth.getStatusCode()).body(Map.of(
                    "success", false,
                    "error", auth.getStatusCode() == 401 ? "Unauthorized" : "Forbidden",
                    "message", auth.getMessage()
            ));
        }

        if (!vehicleRepo.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        vehicleRepo.deleteById(id);
        return ResponseEntity.ok(Map.of("success", true, "message", "Vehicle deleted successfully"));
    }

    // --- DISTANCE SLABS ---
    @GetMapping("/distance-slabs")
    public List<DistanceSlab> getDistanceSlabs(@RequestParam(required = false) String city, @RequestParam(required = false) String vehicleId) {
        if (city != null && vehicleId != null) {
            return distanceRepo.findByCityAndVehicleIdOrderByFromKmAsc(city, vehicleId);
        }
        return distanceRepo.findAll();
    }

    @PostMapping("/distance-slabs")
    public ResponseEntity<?> addDistanceSlab(@RequestBody DistanceSlab slab, HttpServletRequest request) {
        var auth = adminAuthService.verifyAdmin(request);
        if (!auth.isAuthorized()) {
            return ResponseEntity.status(auth.getStatusCode()).body(Map.of(
                    "success", false,
                    "error", auth.getStatusCode() == 401 ? "Unauthorized" : "Forbidden",
                    "message", auth.getMessage()
            ));
        }
        DistanceSlab saved = distanceRepo.save(slab);
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/distance-slabs/{id}")
    public ResponseEntity<?> deleteDistanceSlab(@PathVariable Long id, HttpServletRequest request) {
        var auth = adminAuthService.verifyAdmin(request);
        if (!auth.isAuthorized()) {
            return ResponseEntity.status(auth.getStatusCode()).body(Map.of(
                    "success", false,
                    "error", auth.getStatusCode() == 401 ? "Unauthorized" : "Forbidden",
                    "message", auth.getMessage()
            ));
        }
        if (!distanceRepo.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        distanceRepo.deleteById(id);
        return ResponseEntity.ok(Map.of("success", true, "message", "Distance slab deleted"));
    }

    // --- WEIGHT SLABS ---
    @GetMapping("/weight-slabs")
    public List<WeightSlab> getWeightSlabs(@RequestParam(required = false) String vehicleId) {
        if (vehicleId != null) {
            return weightRepo.findByVehicleIdOrderByFromKgAsc(vehicleId);
        }
        return weightRepo.findAll();
    }

    @PostMapping("/weight-slabs")
    public ResponseEntity<?> addWeightSlab(@RequestBody WeightSlab slab, HttpServletRequest request) {
        var auth = adminAuthService.verifyAdmin(request);
        if (!auth.isAuthorized()) {
            return ResponseEntity.status(auth.getStatusCode()).body(Map.of(
                    "success", false,
                    "error", auth.getStatusCode() == 401 ? "Unauthorized" : "Forbidden",
                    "message", auth.getMessage()
            ));
        }
        WeightSlab saved = weightRepo.save(slab);
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/weight-slabs/{id}")
    public ResponseEntity<?> deleteWeightSlab(@PathVariable Long id, HttpServletRequest request) {
        var auth = adminAuthService.verifyAdmin(request);
        if (!auth.isAuthorized()) {
            return ResponseEntity.status(auth.getStatusCode()).body(Map.of(
                    "success", false,
                    "error", auth.getStatusCode() == 401 ? "Unauthorized" : "Forbidden",
                    "message", auth.getMessage()
            ));
        }
        if (!weightRepo.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        weightRepo.deleteById(id);
        return ResponseEntity.ok(Map.of("success", true, "message", "Weight slab deleted"));
    }

    // --- GLOBAL SETTINGS ---
    @GetMapping("/settings")
    public Map<String, String> getSettings() {
        Map<String, String> result = new HashMap<>();
        for (GlobalSettings s : settingsRepo.findAll()) {
            if (s.getSettingKey() != null) {
                result.put(s.getSettingKey(), s.getSettingValue() != null ? s.getSettingValue() : "");
            }
        }
        if (!result.containsKey("GST_PERCENTAGE")) {
            result.put("GST_PERCENTAGE", "0.0");
        }
        return result;
    }

    @PostMapping("/settings")
    public ResponseEntity<?> updateSettings(@RequestBody Map<String, String> settings, HttpServletRequest request) {
        var auth = adminAuthService.verifyAdmin(request);
        if (!auth.isAuthorized()) {
            return ResponseEntity.status(auth.getStatusCode()).body(Map.of(
                    "success", false,
                    "error", auth.getStatusCode() == 401 ? "Unauthorized" : "Forbidden",
                    "message", auth.getMessage()
            ));
        }

        String adminName = adminAuthService.getAdminIdentifier(request);
        settings.forEach((key, value) -> {
            Optional<GlobalSettings> existing = settingsRepo.findBySettingKey(key);
            String oldVal = existing.map(GlobalSettings::getSettingValue).orElse("-");
            GlobalSettings s = existing.orElseGet(GlobalSettings::new);
            s.setSettingKey(key);
            s.setSettingValue(value);
            settingsRepo.save(s);
            logHistory(adminName, "GLOBAL_SETTING", oldVal, value, "Updated setting " + key, "ALL");
        });
        return ResponseEntity.ok(Map.of("success", true, "message", "Settings updated successfully"));
    }

    // --- DEDICATED GST MANAGEMENT ---
    @GetMapping("/gst")
    public ResponseEntity<?> getGst() {
        double gstPercentage = 0.0;
        Optional<GlobalSettings> gs = settingsRepo.findBySettingKey("GST_PERCENTAGE");
        if (gs.isEmpty()) gs = settingsRepo.findBySettingKey("gst_percentage");
        if (gs.isEmpty()) gs = settingsRepo.findBySettingKey("GST_RATE");
        if (gs.isPresent() && gs.get().getSettingValue() != null && !gs.get().getSettingValue().isBlank()) {
            try {
                gstPercentage = Double.parseDouble(gs.get().getSettingValue().trim());
            } catch (Exception ignored) {}
        }
        return ResponseEntity.ok(Map.of(
                "success", true,
                "gstPercentage", gstPercentage
        ));
    }

    @PostMapping("/gst")
    public ResponseEntity<?> updateGst(@RequestBody Map<String, Object> payload, HttpServletRequest request) {
        var auth = adminAuthService.verifyAdmin(request);
        if (!auth.isAuthorized()) {
            return ResponseEntity.status(auth.getStatusCode()).body(Map.of(
                    "success", false,
                    "error", auth.getStatusCode() == 401 ? "Unauthorized" : "Forbidden",
                    "message", auth.getMessage()
            ));
        }

        Double rate = null;
        if (payload.containsKey("gstPercentage")) {
            rate = Double.parseDouble(String.valueOf(payload.get("gstPercentage")));
        } else if (payload.containsKey("gst")) {
            rate = Double.parseDouble(String.valueOf(payload.get("gst")));
        } else if (payload.containsKey("rate")) {
            rate = Double.parseDouble(String.valueOf(payload.get("rate")));
        }

        if (rate == null || rate < 0.0 || rate > 100.0) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Valid GST percentage between 0 and 100 is required."
            ));
        }

        String adminName = adminAuthService.getAdminIdentifier(request);
        Optional<GlobalSettings> existing = settingsRepo.findBySettingKey("GST_PERCENTAGE");
        String oldRate = existing.map(GlobalSettings::getSettingValue).orElse("18.0");

        GlobalSettings gs = existing.orElseGet(GlobalSettings::new);
        gs.setSettingKey("GST_PERCENTAGE");
        gs.setSettingValue(String.valueOf(rate));
        settingsRepo.save(gs);

        logHistory(adminName, "ALL", oldRate + "%", rate + "%", "Updated Global GST Rate", "ALL");

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "GST percentage updated successfully",
                "gstPercentage", rate
        ));
    }

    // --- HISTORY ---
    @GetMapping("/history")
    public List<PricingHistory> getHistory() {
        return historyRepo.findAllByOrderByUpdatedTimeDesc();
    }

    private void logHistory(String adminName, String vehicleId, String oldPrice, String newPrice, String reason, String city) {
        PricingHistory history = new PricingHistory();
        history.setAdminName(adminName);
        history.setVehicleId(vehicleId);
        history.setOldPrice(oldPrice);
        history.setNewPrice(newPrice);
        history.setReason(reason);
        history.setCity(city);
        historyRepo.save(history);
    }
}
