package com.anushaporter.backend.service;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

public interface MapsDirectionsProvider {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    class RouteDetails {
        private BigDecimal distanceKm;
        private Integer durationMinutes;
        private String polyline;
        private String provider;
    }

    RouteDetails getRoute(
            Double pickupLat, Double pickupLng,
            Double dropLat, Double dropLng,
            List<String> intermediateStops
    );

    RouteDetails calculateEstimatedRoute(String pickupAddress, String dropAddress, List<String> intermediateStops);
}
