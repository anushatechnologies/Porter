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
            @RequestParam(name = "place_id", required = false) String place_id,
            @RequestParam(name = "id", required = false) String id,
            @RequestParam(name = "name", required = false) String name,
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "query", required = false) String query,
            @RequestParam(name = "address", required = false) String address,
            @RequestParam(name = "lat", required = false) Double lat,
            @RequestParam(name = "latitude", required = false) Double latitude,
            @RequestParam(name = "lng", required = false) Double lng,
            @RequestParam(name = "lon", required = false) Double lon,
            @RequestParam(name = "longitude", required = false) Double longitude) {
        String targetPlaceId = placeId;
        if (targetPlaceId == null || targetPlaceId.isBlank()) targetPlaceId = place_id;
        if (targetPlaceId == null || targetPlaceId.isBlank()) targetPlaceId = id;
        if (targetPlaceId == null || targetPlaceId.isBlank()) targetPlaceId = q;
        if (targetPlaceId == null || targetPlaceId.isBlank()) targetPlaceId = query;
        if (targetPlaceId == null || targetPlaceId.isBlank()) targetPlaceId = name;
        if (targetPlaceId == null || targetPlaceId.isBlank()) targetPlaceId = address;

        Double targetLat = lat != null ? lat : latitude;
        Double targetLng = lng != null ? lng : (lon != null ? lon : longitude);
        String nameHint = name != null ? name : (q != null ? q : (query != null ? query : address));

        Map<String, Object> result = locationService.getPlaceDetails(targetPlaceId, nameHint, targetLat, targetLng);
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
