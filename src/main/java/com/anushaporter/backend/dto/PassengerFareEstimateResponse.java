package com.anushaporter.backend.dto;

import com.anushaporter.backend.model.BookingFareBreakdown;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PassengerFareEstimateResponse {
    private String fareLockToken;
    private LocalDateTime fareLockExpiresAt;
    private String pricingVersionId;
    private String serviceType;
    private String vehicleCategoryCode;
    private String vehicleDisplayName;
    private Integer passengerCapacity;
    private Integer luggageCapacity;
    private BigDecimal distanceKm;
    private Integer durationMinutes;
    private BookingFareBreakdown breakdown;
    private String message;

    @Builder.Default
    private boolean success = true;

    private java.util.List<java.util.Map<String, Object>> estimates;

    public String getFareToken() {
        return fareLockToken;
    }

    public LocalDateTime getTokenExpiresAt() {
        return fareLockExpiresAt;
    }

    public BigDecimal getEstimatedFare() {
        return breakdown != null ? breakdown.getTotalFare() : null;
    }

    public String getPricingVersion() {
        return pricingVersionId;
    }
}
