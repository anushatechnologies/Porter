package com.anushaporter.backend.controller;

import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.beans.factory.annotation.Value;

@RestController
@RequestMapping("/api/location")

public class LocationController {

    @Autowired
    private RestTemplate restTemplate;

    @Value("${google.maps.api.key}")
    private String apiKey;

    @GetMapping("/search")
    public ResponseEntity<String> search(@RequestParam String q) {
        // Use Google Places Autocomplete. Restrict to India and prioritize Hyderabad area.
        String url = "https://maps.googleapis.com/maps/api/place/autocomplete/json?input=" + q 
                + "&components=country:in&location=17.3850,78.4867&radius=50000&key=" + apiKey;
        
        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
        return ResponseEntity.ok(response.getBody());
    }

    @GetMapping("/details")
    public ResponseEntity<String> details(@RequestParam String place_id) {
        String url = "https://maps.googleapis.com/maps/api/place/details/json?place_id=" + place_id 
                + "&fields=geometry&key=" + apiKey;
        
        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
        return ResponseEntity.ok(response.getBody());
    }

    @GetMapping("/reverse")
    public ResponseEntity<String> reverse(@RequestParam double lat, @RequestParam double lng) {
        String url = "https://maps.googleapis.com/maps/api/geocode/json?latlng=" + lat + "," + lng 
                + "&key=" + apiKey;
        
        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
        return ResponseEntity.ok(response.getBody());
    }
}
