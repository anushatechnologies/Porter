package com.anushaporter.backend.controller;

import com.anushaporter.backend.service.LocationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/location")
public class LocationController {

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
    public ResponseEntity<?> search(
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "input", required = false) String input) {
        String query = (q != null && !q.trim().isEmpty()) ? q : input;
        if (query == null) {
            query = "";
        }
        Map<String, Object> result = locationService.getAutocomplete(query);
        return ResponseEntity.ok(result);
    }

    @Autowired(required = false)
    private com.anushaporter.backend.repository.DriverRepository driverRepository;

    @GetMapping("/reverse")
    public ResponseEntity<String> reverse(@RequestParam double lat, @RequestParam double lng) {
        return locationService.reverseGeocode(lat, lng);
    }

    @GetMapping({"/nearby-drivers", "/nearby"})
    public ResponseEntity<?> getNearbyDrivers(
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng,
            @RequestParam(required = false, defaultValue = "10") Double radiusKm,
            @RequestParam(required = false) String serviceType,
            @RequestParam(required = false) String vehicleType) {

        double searchLat = lat != null ? lat : 17.4486;
        double searchLng = lng != null ? lng : 78.3908;
        double maxRadius = (radiusKm != null && radiusKm > 0) ? radiusKm : 10.0;

        java.util.List<com.anushaporter.backend.model.Driver> allDrivers = driverRepository != null ? driverRepository.findAll() : java.util.Collections.emptyList();
        java.util.List<java.util.Map<String, Object>> nearby = new java.util.ArrayList<>();

        for (com.anushaporter.backend.model.Driver d : allDrivers) {
            String st = d.getStatus() != null ? d.getStatus().toLowerCase() : "";
            if (!st.contains("inactive") && !st.contains("suspended") && !st.contains("rejected") && !st.contains("blocked")) {
                if (d.getLatitude() != null && d.getLongitude() != null) {
                    double dist = calculateDistanceKm(searchLat, searchLng, d.getLatitude(), d.getLongitude());
                    if (dist <= maxRadius) {
                        if (serviceType != null && !serviceType.isBlank()) {
                            if (d.getServiceType() != null && !d.getServiceType().equalsIgnoreCase(serviceType)) {
                                continue;
                            }
                        }
                        if (vehicleType != null && !vehicleType.isBlank()) {
                            if (d.getVehicleType() != null && !d.getVehicleType().equalsIgnoreCase(vehicleType)) {
                                continue;
                            }
                        }
                        java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
                        map.put("id", d.getId());
                        map.put("driverId", d.getId());
                        map.put("name", d.getName() != null ? d.getName() : "Driver Partner");
                        map.put("phone", d.getPhone() != null ? d.getPhone() : "");
                        map.put("latitude", d.getLatitude());
                        map.put("longitude", d.getLongitude());
                        map.put("heading", d.getHeading() != null ? d.getHeading() : 0.0);
                        map.put("vehicleType", d.getVehicleType() != null ? d.getVehicleType() : (d.getVehicle() != null ? d.getVehicle() : "2 Wheeler"));
                        map.put("serviceType", d.getServiceType() != null ? d.getServiceType() : "GOODS");
                        map.put("rating", d.getRating() != null ? d.getRating() : "4.8");
                        map.put("distanceKm", Math.round(dist * 100.0) / 100.0);
                        map.put("etaMinutes", Math.max(2, (int) Math.round(dist * 3.0)));
                        nearby.add(map);
                    }
                }
            }
        }

        nearby.sort(java.util.Comparator.comparingDouble(m -> (Double) m.get("distanceKm")));

        java.util.Map<String, Object> resp = new java.util.LinkedHashMap<>();
        resp.put("success", true);
        resp.put("drivers", nearby);
        resp.put("count", nearby.size());
        resp.put("searchRadiusKm", maxRadius);
        return ResponseEntity.ok(resp);
    }

    private double calculateDistanceKm(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }
}
