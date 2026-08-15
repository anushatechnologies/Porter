package com.anushaporter.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

@RestController
public class CityController {

    @GetMapping({"/api/cities", "/api/franchises/cities"})
    public ResponseEntity<Map<String, Object>> getOperationalCities() {
        List<Map<String, Object>> cities = Arrays.asList(
                createCity("hyderabad", "Hyderabad", true, "Telangana"),
                createCity("warangal", "Warangal", false, "Telangana"),
                createCity("bengaluru", "Bengaluru", false, "Karnataka"),
                createCity("mumbai", "Mumbai", false, "Maharashtra"),
                createCity("chennai", "Chennai", false, "Tamil Nadu")
        );

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("count", cities.size());
        response.put("cities", cities);
        return ResponseEntity.ok(response);
    }

    private Map<String, Object> createCity(String id, String name, boolean isPrimary, String state) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", id);
        map.put("name", name);
        map.put("isPrimary", isPrimary);
        map.put("state", state);
        return map;
    }
}
