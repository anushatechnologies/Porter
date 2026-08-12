package com.anushaporter.backend.controller;

import com.anushaporter.backend.model.Vehicle;
import com.anushaporter.backend.repository.VehicleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/vehicles")
public class VehicleController {
    @Autowired
    private VehicleRepository repository;

    /**
     * GET /api/vehicles
     * Returns fleet vehicles inventory for Admin Vehicles module.
     */
    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getAll() {
        List<Vehicle> vehicles = repository.findAll();

        List<Map<String, Object>> items = vehicles.stream().map(v -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", v.getId() != null ? "V-" + v.getId() : "V-100");
            map.put("vehicleId", v.getId());
            map.put("model", v.getModel() != null ? v.getModel() : "Commercial Truck");
            map.put("type", v.getType() != null ? v.getType() : "Commercial Truck");
            map.put("plate", v.getPlate() != null ? v.getPlate() : "");
            map.put("owner", v.getOwner() != null ? v.getOwner() : "Unassigned");
            map.put("capacity", v.getCapacity() != null ? v.getCapacity() : "750 kg");
            map.put("trips", v.getTrips() != null ? v.getTrips() : 0);
            return map;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(items);
    }

    @PostMapping
    public Vehicle create(@RequestBody Vehicle entity) {
        return repository.save(entity);
    }
}
