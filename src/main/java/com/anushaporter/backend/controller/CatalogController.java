package com.anushaporter.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/catalog")
public class CatalogController {

    @GetMapping("/vehicles")
    public ResponseEntity<List<Map<String, Object>>> getVehicles() {
        return ResponseEntity.ok(List.of(
            Map.of("id", "2wheeler", "name", "2 Wheeler", "subtitle", "Light parcels and urgent documents", "icon", "bicycle-outline", "value", "2 Wheeler"),
            Map.of("id", "mini-truck", "name", "Mini Truck", "subtitle", "Compact loads for local shifting", "icon", "cube-outline", "value", "Mini Truck"),
            Map.of("id", "truck", "name", "Truck", "subtitle", "Heavy transport for business loads", "icon", "bus-outline", "value", "Truck"),
            Map.of("id", "pickup", "name", "Pickup Vehicle", "subtitle", "Fast same-city deliveries", "icon", "car-sport-outline", "value", "Pickup Vehicle")
        ));
    }

    @GetMapping("/packers")
    public ResponseEntity<List<Map<String, Object>>> getPackers() {
        return ResponseEntity.ok(List.of(
            Map.of("id", "within-city", "name", "Within City Only", "subtitle", "Local home and office shifting inside the city", "icon", "home-outline", "value", "Within City Shifting"),
            Map.of("id", "intercity", "name", "Between Cities", "subtitle", "Door-to-door shifting service between cities", "icon", "navigate-outline", "value", "Intercity Shifting"),
            Map.of("id", "labor", "name", "Mini Truck + 2 Labours", "subtitle", "Within-city shifting with a mini truck and two helpers", "icon", "people-outline", "value", "Mini Truck + Labor")
        ));
    }
}
