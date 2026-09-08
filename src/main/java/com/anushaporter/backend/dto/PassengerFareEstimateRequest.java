package com.anushaporter.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PassengerFareEstimateRequest {
    private String serviceType; // ONE_WAY, ROUND_TRIP, RENTAL, AIRPORT_TRANSFER
    private String vehicleCategoryCode; // HATCHBACK, SEDAN, SUV, PREMIUM_SUV, LUXURY
    private String pickupAddress;
    private Double pickupLat;
    private Double pickupLng;
    private String dropAddress;
    private Double dropLat;
    private Double dropLng;
    private Integer passengerCount;
    private Integer luggageCount;
    private LocalDateTime scheduledPickupTime;
    private Boolean roundTrip;
    private Long rentalPackageId;
    private BigDecimal manualDistanceKm; // Optional: manual override for preview/testing
    private Integer manualDurationMinutes; // Optional: manual override for preview/testing
    private Integer waitingMinutes;
    private List<String> additionalStops;
    private String couponCode;

    public void setPickupLatitude(Double pickupLatitude) {
        this.pickupLat = pickupLatitude;
    }

    public Double getPickupLatitude() {
        return this.pickupLat;
    }

    public void setPickupLongitude(Double pickupLongitude) {
        this.pickupLng = pickupLongitude;
    }

    public Double getPickupLongitude() {
        return this.pickupLng;
    }

    public void setDropLatitude(Double dropLatitude) {
        this.dropLat = dropLatitude;
    }

    public Double getDropLatitude() {
        return this.dropLat;
    }

    public void setDropLongitude(Double dropLongitude) {
        this.dropLng = dropLongitude;
    }

    public Double getDropLongitude() {
        return this.dropLng;
    }

    public void setStops(List<String> stops) {
        this.additionalStops = stops;
    }

    public List<String> getStops() {
        return this.additionalStops;
    }
}
