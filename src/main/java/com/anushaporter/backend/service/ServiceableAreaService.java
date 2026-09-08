package com.anushaporter.backend.service;

import com.anushaporter.backend.model.ServiceableArea;
import com.anushaporter.backend.repository.ServiceableAreaRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class ServiceableAreaService {

    private static final Logger log = LoggerFactory.getLogger(ServiceableAreaService.class);

    @Autowired
    private ServiceableAreaRepository repository;

    @Autowired(required = false)
    private LocationService locationService;

    @Autowired
    private ObjectMapper objectMapper;

    @PostConstruct
    public void seedDefaultServiceableAreas() {
        if (repository.count() == 0) {
            log.info("Seeding initial admin-approved serviceable areas and pincodes...");
            List<ServiceableArea> defaults = Arrays.asList(
                    // Hyderabad Core IT & Business Hubs (Active)
                    createArea("Hyderabad", "Hitech City", "500081", true, 17.4486, 78.3808, 6.0),
                    createArea("Hyderabad", "Madhapur", "500086", true, 17.4483, 78.3915, 5.0),
                    createArea("Hyderabad", "Gachibowli", "500032", true, 17.4401, 78.3489, 7.0),
                    createArea("Hyderabad", "Jubilee Hills", "500033", true, 17.4319, 78.4073, 5.0),
                    createArea("Hyderabad", "Banjara Hills", "500034", true, 17.4156, 78.4357, 5.0),
                    createArea("Hyderabad", "Kondapur", "500084", true, 17.4646, 78.3639, 5.0),
                    createArea("Hyderabad", "Kukatpally", "500072", true, 17.4947, 78.3996, 6.0),
                    createArea("Hyderabad", "Begumpet", "500016", true, 17.4448, 78.4664, 5.0),
                    createArea("Hyderabad", "Secunderabad", "500003", true, 17.4399, 78.4983, 6.0),
                    createArea("Hyderabad", "Ameerpet", "500038", true, 17.4375, 78.4482, 5.0),

                    // Outlying / Inactive areas (Admin can enable anytime)
                    createArea("Hyderabad", "Shamshabad Airport Zone", "501218", false, 17.2403, 78.4294, 8.0),
                    createArea("Hyderabad", "Medchal Outer", "501401", false, 17.6297, 78.4814, 8.0)
            );
            repository.saveAll(defaults);
            log.info("Seeded {} serviceable areas into database.", defaults.size());
        }
    }

    private ServiceableArea createArea(String city, String name, String pincode, boolean active, double lat, double lng, double radius) {
        ServiceableArea a = new ServiceableArea();
        a.setCity(city);
        a.setAreaName(name);
        a.setPincode(pincode);
        a.setIsServiceable(active);
        a.setCenterLat(lat);
        a.setCenterLng(lng);
        a.setRadiusKm(radius);
        a.setCreatedAt(LocalDateTime.now());
        a.setUpdatedAt(LocalDateTime.now());
        return a;
    }

    public List<ServiceableArea> getAreasByCity(String city) {
        if (city == null || city.isBlank()) {
            return repository.findAll();
        }
        return repository.findByCityIgnoreCaseOrderByAreaNameAsc(city.trim());
    }

    public List<ServiceableArea> getActiveAreasByCity(String city) {
        if (city == null || city.isBlank()) {
            return repository.findByIsServiceableTrue();
        }
        return repository.findByCityIgnoreCaseAndIsServiceableTrueOrderByAreaNameAsc(city.trim());
    }

    public ServiceableArea saveArea(ServiceableArea area) {
        if (area.getCreatedAt() == null) area.setCreatedAt(LocalDateTime.now());
        area.setUpdatedAt(LocalDateTime.now());
        return repository.save(area);
    }

    public Optional<ServiceableArea> toggleAreaStatus(Long id, Boolean active) {
        return repository.findById(id).map(a -> {
            a.setIsServiceable(active != null ? active : !a.getIsServiceable());
            a.setUpdatedAt(LocalDateTime.now());
            return repository.save(a);
        });
    }

    public Map<String, Object> bulkUpdateServiceablePincodes(String city, List<String> activePincodes) {
        String targetCity = (city != null && !city.isBlank()) ? city.trim() : "Hyderabad";
        List<ServiceableArea> areasInCity = repository.findByCityIgnoreCaseOrderByAreaNameAsc(targetCity);

        Set<String> approvedSet = activePincodes != null
                ? activePincodes.stream().map(String::trim).collect(Collectors.toSet())
                : Collections.emptySet();

        int enabledCount = 0;
        int disabledCount = 0;

        for (ServiceableArea a : areasInCity) {
            boolean shouldBeActive = approvedSet.contains(a.getPincode().trim());
            a.setIsServiceable(shouldBeActive);
            a.setUpdatedAt(LocalDateTime.now());
            if (shouldBeActive) enabledCount++;
            else disabledCount++;
        }
        repository.saveAll(areasInCity);

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("success", true);
        res.put("city", targetCity);
        res.put("enabledCount", enabledCount);
        res.put("disabledCount", disabledCount);
        res.put("totalAreasInCity", areasInCity.size());
        res.put("activePincodes", approvedSet);
        return res;
    }

    /**
     * Validates whether given coordinates, address, or pincode fall within admin-approved serviceable zones.
     */
    public Map<String, Object> validateLocation(Double lat, Double lng, String rawPincode, String city) {
        Map<String, Object> result = new LinkedHashMap<>();
        String targetCity = (city != null && !city.isBlank()) ? city.trim() : "Hyderabad";

        // 1. Direct Pincode check if supplied
        String resolvedPincode = null;
        if (rawPincode != null && !rawPincode.isBlank()) {
            Matcher m = Pattern.compile("(\\d{6})").matcher(rawPincode);
            if (m.find()) {
                resolvedPincode = m.group(1);
            }
        }

        // 2. If no pincode provided, extract from reverse-geocoding coordinates
        if (resolvedPincode == null && lat != null && lng != null && locationService != null) {
            try {
                ResponseEntity<String> geoRes = locationService.reverseGeocode(lat, lng);
                if (geoRes != null && geoRes.getStatusCode().is2xxSuccessful() && geoRes.getBody() != null) {
                    JsonNode root = objectMapper.readTree(geoRes.getBody());
                    JsonNode results = root.path("results");
                    if (results.isArray() && results.size() > 0) {
                        for (JsonNode resNode : results) {
                            JsonNode components = resNode.path("address_components");
                            for (JsonNode c : components) {
                                JsonNode types = c.path("types");
                                for (JsonNode t : types) {
                                    if ("postal_code".equalsIgnoreCase(t.asText())) {
                                        resolvedPincode = c.path("long_name").asText();
                                        break;
                                    }
                                }
                                if (resolvedPincode != null) break;
                            }
                            if (resolvedPincode != null) break;
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Reverse geocode failed while resolving pincode for ({}, {}): {}", lat, lng, e.getMessage());
            }
        }

        // 3. Match against database of active serviceable areas
        ServiceableArea matchedArea = null;

        // A. Match by pincode
        if (resolvedPincode != null) {
            Optional<ServiceableArea> areaOpt = repository.findByPincodeAndIsServiceableTrue(resolvedPincode);
            if (areaOpt.isPresent()) {
                matchedArea = areaOpt.get();
            }
        }

        // B. Fallback: Match by coordinate proximity to any active area center radius
        if (matchedArea == null && lat != null && lng != null) {
            List<ServiceableArea> activeAreas = repository.findByIsServiceableTrue();
            for (ServiceableArea a : activeAreas) {
                if (a.getCenterLat() != null && a.getCenterLng() != null) {
                    double dist = calculateDistanceKm(lat, lng, a.getCenterLat(), a.getCenterLng());
                    double radius = a.getRadiusKm() != null ? a.getRadiusKm() : 5.0;
                    if (dist <= radius) {
                        matchedArea = a;
                        if (resolvedPincode == null) {
                            resolvedPincode = a.getPincode();
                        }
                        break;
                    }
                }
            }
        }

        // 4. Construct Response
        if (matchedArea != null) {
            result.put("success", true);
            result.put("serviceable", true);
            result.put("areaName", matchedArea.getAreaName());
            result.put("pincode", resolvedPincode != null ? resolvedPincode : matchedArea.getPincode());
            result.put("city", matchedArea.getCity());
            result.put("message", "Location is in an admin-approved serviceable zone.");
            return result;
        }

        // Outside serviceable area: Provide list of active areas in city for user guidance
        List<ServiceableArea> activeInCity = repository.findByCityIgnoreCaseAndIsServiceableTrueOrderByAreaNameAsc(targetCity);
        List<String> approvedNames = activeInCity.stream()
                .map(a -> a.getAreaName() + " (" + a.getPincode() + ")")
                .collect(Collectors.toList());

        result.put("success", true);
        result.put("serviceable", false);
        result.put("pincode", resolvedPincode != null ? resolvedPincode : "Unknown");
        result.put("city", targetCity);
        result.put("message", String.format("Porter service is not currently available at this location%s. Please choose an approved area in %s.",
                (resolvedPincode != null ? " (Pincode: " + resolvedPincode + ")" : ""), targetCity));
        result.put("approvedAreas", approvedNames);
        return result;
    }

    private double calculateDistanceKm(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371; // Radius of the Earth in km
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }
}
