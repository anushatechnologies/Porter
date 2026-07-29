package com.anushaporter.backend.controller;

import com.anushaporter.backend.dto.EstimateAllResponse;
import com.anushaporter.backend.dto.PricingRequest;
import com.anushaporter.backend.dto.PricingResponse;
import com.anushaporter.backend.service.PricingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController

@RequestMapping("/api/pricing")
public class PricingController {

    @Autowired
    private PricingService pricingService;

    @Autowired
    private com.anushaporter.backend.repository.PricingVehicleRepository vehicleRepo;

    @GetMapping("/vehicles")
    public ResponseEntity<java.util.List<com.anushaporter.backend.model.PricingVehicle>> getActiveVehicles() {
        // Return only vehicles that are active
        return ResponseEntity.ok(vehicleRepo.findByStatus(true));
    }

    @PostMapping("/calculate")
    public ResponseEntity<PricingResponse> calculatePricing(@RequestBody PricingRequest request) {
        PricingResponse response = pricingService.calculatePricing(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/estimate-all")
    public ResponseEntity<EstimateAllResponse> estimateAll(@RequestBody PricingRequest request) {
        EstimateAllResponse response = new EstimateAllResponse(pricingService.estimateAll(request));
        return ResponseEntity.ok(response);
    }

    // --- ADMIN CONFIGURATION ENDPOINTS ---
    @GetMapping("/vehicle/{vehicleId}")
    public ResponseEntity<com.anushaporter.backend.model.PricingVehicle> getVehiclePricing(@PathVariable String vehicleId) {
        return vehicleRepo.findByVehicleId(vehicleId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<java.util.Map<String, Object>> addVehiclePricing(@RequestBody com.anushaporter.backend.model.PricingVehicle vehicle) {
        com.anushaporter.backend.model.PricingVehicle saved = vehicleRepo.save(vehicle);
        return ResponseEntity.ok(java.util.Map.of("success", true, "vehicle", saved));
    }

    @PutMapping("/vehicle/{vehicleId}")
    public ResponseEntity<java.util.Map<String, Object>> updateVehiclePricing(@PathVariable String vehicleId, @RequestBody com.anushaporter.backend.model.PricingVehicle vehicle) {
        return vehicleRepo.findByVehicleId(vehicleId).map(existing -> {
            vehicle.setId(existing.getId());
            com.anushaporter.backend.model.PricingVehicle saved = vehicleRepo.save(vehicle);
            return ResponseEntity.ok(java.util.Map.of("success", true, "vehicle", saved));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<java.util.Map<String, Object>> deleteVehiclePricing(@PathVariable Long id) {
        vehicleRepo.deleteById(id);
        return ResponseEntity.ok(java.util.Map.of("success", true));
    }
}
