package com.anushaporter.backend.controller;

import com.anushaporter.backend.service.LocationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/places")
public class PlacesController {

    @Autowired
    private LocationService locationService;

    @GetMapping("/autocomplete")
    public ResponseEntity<Map<String, Object>> autocomplete(
            @RequestParam(name = "input", required = false) String input,
            @RequestParam(name = "q", required = false) String q) {
        String query = (input != null && !input.trim().isEmpty()) ? input : q;
        Map<String, Object> result = locationService.getAutocomplete(query);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/details")
    public ResponseEntity<Map<String, Object>> details(
            @RequestParam(name = "placeId", required = false) String placeId,
            @RequestParam(name = "place_id", required = false) String place_id) {
        String targetPlaceId = (placeId != null && !placeId.trim().isEmpty()) ? placeId : place_id;
        Map<String, Object> result = locationService.getPlaceDetails(targetPlaceId);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> search(
            @RequestParam(name = "input", required = false) String input,
            @RequestParam(name = "q", required = false) String q) {
        String query = (input != null && !input.trim().isEmpty()) ? input : q;
        Map<String, Object> result = locationService.getAutocomplete(query);
        return ResponseEntity.ok(result);
    }
}
