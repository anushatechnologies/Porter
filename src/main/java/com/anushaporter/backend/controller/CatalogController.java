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
            Map.of("id", "house-shifting", "name", "Full House Shifting", "subtitle", "Complete packing, loading, transport & unpacking", "value", "House Shifting", "basePrice", 2500, "icon", "home-outline"),
            Map.of("id", "1bhk", "name", "1 BHK Relocation", "subtitle", "Ideal for small apartments & single rooms", "value", "1 BHK Shifting", "basePrice", 1800, "icon", "cube-outline"),
            Map.of("id", "2bhk", "name", "2 BHK Relocation", "subtitle", "Complete apartment relocation service", "value", "2 BHK Shifting", "basePrice", 2800, "icon", "home-outline"),
            Map.of("id", "office-relocation", "name", "Office Relocation", "subtitle", "Packing and shifting for offices", "value", "Office Relocation", "basePrice", 3500, "icon", "business-outline"),
            Map.of("id", "mini-packers", "name", "Mini Packers", "subtitle", "Small-room packing and transport", "value", "Mini Packers", "basePrice", 1200, "icon", "cube-outline")
        ));
    }
}
