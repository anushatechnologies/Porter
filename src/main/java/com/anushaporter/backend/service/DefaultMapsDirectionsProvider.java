package com.anushaporter.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
public class DefaultMapsDirectionsProvider implements MapsDirectionsProvider {

    @Value("${google.maps.api.key:}")
    private String googleMapsApiKey;

    private static final double EARTH_RADIUS_KM = 6371.0;
    private static final double ROAD_FACTOR = 1.25; // accounts for road curvature vs aerial distance
    private static final double AVERAGE_CITY_SPEED_KMH = 30.0; // 30 km/h average city speed

    @Override
    public RouteDetails getRoute(
            Double pickupLat, Double pickupLng,
            Double dropLat, Double dropLng,
            List<String> intermediateStops
    ) {
        if (pickupLat == null || pickupLng == null || dropLat == null || dropLng == null) {
            return RouteDetails.builder()
                    .distanceKm(new BigDecimal("15.00"))
                    .durationMinutes(35)
                    .provider("DEFAULT_FALLBACK")
                    .build();
        }

        // Haversine calculation
        double dLat = Math.toRadians(dropLat - pickupLat);
        double dLng = Math.toRadians(dropLng - pickupLng);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(pickupLat)) * Math.cos(Math.toRadians(dropLat))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        double straightDistance = EARTH_RADIUS_KM * c;
        double roadDistance = straightDistance * ROAD_FACTOR;

        // Additional stops add approx 3-5 km each
        if (intermediateStops != null && !intermediateStops.isEmpty()) {
            roadDistance += intermediateStops.size() * 4.0;
        }

        if (roadDistance < 1.0) {
            roadDistance = 1.0; // Minimum 1 km
        }

        BigDecimal finalDistance = BigDecimal.valueOf(roadDistance).setScale(2, RoundingMode.HALF_UP);
        int durationMinutes = (int) Math.round((roadDistance / AVERAGE_CITY_SPEED_KMH) * 60);
        if (durationMinutes < 10) {
            durationMinutes = 10;
        }

        return RouteDetails.builder()
                .distanceKm(finalDistance)
                .durationMinutes(durationMinutes)
                .provider("HAVERSINE_ROUTING_ENGINE")
                .build();
    }

    @Override
    public RouteDetails calculateEstimatedRoute(String pickupAddress, String dropAddress, List<String> intermediateStops) {
        double estimatedDistance = 15.0;
        if (pickupAddress != null && dropAddress != null) {
            String combined = (pickupAddress + dropAddress).toLowerCase();
            if (combined.contains("airport")) {
                estimatedDistance = 32.0;
            } else if (combined.contains("outstation") || combined.contains("highway")) {
                estimatedDistance = 65.0;
            }
        }
        if (intermediateStops != null && !intermediateStops.isEmpty()) {
            estimatedDistance += intermediateStops.size() * 5.0;
        }
        int duration = (int) Math.round((estimatedDistance / AVERAGE_CITY_SPEED_KMH) * 60);

        return RouteDetails.builder()
                .distanceKm(BigDecimal.valueOf(estimatedDistance).setScale(2, RoundingMode.HALF_UP))
                .durationMinutes(duration)
                .provider("HEURISTIC_ADDRESS_ESTIMATOR")
                .build();
    }
}
