package com.anushaporter.backend.controller;

import com.anushaporter.backend.model.*;
import com.anushaporter.backend.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/pricing")
public class PricingAdminController {

    @Autowired private PricingVehicleRepository vehicleRepo;
    @Autowired private DistanceSlabRepository distanceRepo;
    @Autowired private WeightSlabRepository weightRepo;
    @Autowired private GlobalSettingsRepository settingsRepo;
    @Autowired private PricingHistoryRepository historyRepo;

    // --- VEHICLES ---
    @GetMapping("/vehicles")
    public List<PricingVehicle> getVehicles() {
        return vehicleRepo.findAll();
    }

    @PostMapping("/vehicles")
    public PricingVehicle addVehicle(@RequestBody PricingVehicle vehicle) {
        logHistory("Admin", vehicle.getVehicleId(), "-", String.valueOf(vehicle.getBaseFare()), "Added Vehicle", "-");
        return vehicleRepo.save(vehicle);
    }

    @PutMapping("/vehicles/{id}")
    public ResponseEntity<PricingVehicle> updateVehicle(@PathVariable Long id, @RequestBody PricingVehicle vehicle) {
        return vehicleRepo.findById(id).map(v -> {
            logHistory("Admin", v.getVehicleId(), String.valueOf(v.getBaseFare()), String.valueOf(vehicle.getBaseFare()), "Updated Vehicle", "-");
            vehicle.setId(id);
            return ResponseEntity.ok(vehicleRepo.save(vehicle));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/vehicles/{id}")
    public ResponseEntity<Void> deleteVehicle(@PathVariable Long id) {
        vehicleRepo.deleteById(id);
        return ResponseEntity.ok().build();
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
    public DistanceSlab addDistanceSlab(@RequestBody DistanceSlab slab) {
        return distanceRepo.save(slab);
    }

    @DeleteMapping("/distance-slabs/{id}")
    public ResponseEntity<Void> deleteDistanceSlab(@PathVariable Long id) {
        distanceRepo.deleteById(id);
        return ResponseEntity.ok().build();
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
    public WeightSlab addWeightSlab(@RequestBody WeightSlab slab) {
        return weightRepo.save(slab);
    }

    @DeleteMapping("/weight-slabs/{id}")
    public ResponseEntity<Void> deleteWeightSlab(@PathVariable Long id) {
        weightRepo.deleteById(id);
        return ResponseEntity.ok().build();
    }

    // --- GLOBAL SETTINGS ---
    @GetMapping("/settings")
    public Map<String, String> getSettings() {
        return settingsRepo.findAll().stream()
                .collect(Collectors.toMap(s -> s.getSettingKey(), s -> s.getSettingValue()));
    }

    @PostMapping("/settings")
    public ResponseEntity<Void> updateSettings(@RequestBody Map<String, String> settings) {
        settings.forEach((key, value) -> {
            Optional<GlobalSettings> existing = settingsRepo.findBySettingKey(key);
            GlobalSettings s = existing.orElseGet(GlobalSettings::new);
            s.setSettingKey(key);
            s.setSettingValue(value);
            settingsRepo.save(s);
        });
        return ResponseEntity.ok().build();
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
