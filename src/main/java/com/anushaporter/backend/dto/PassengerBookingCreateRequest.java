package com.anushaporter.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PassengerBookingCreateRequest {
    private String fareLockToken;
    private String serviceType;
    private String vehicleCategoryCode;
    private Integer passengerCount;
    private Integer luggageCount;
    private Long customerId;
    private String customerName;
    private String customerPhone;
    private String customerEmail;
    private String pickupAddress;
    private Double pickupLat;
    private Double pickupLng;
    private String dropAddress;
    private Double dropLat;
    private Double dropLng;
    private Boolean roundTrip;
    private LocalDateTime scheduledPickupTime;
    private LocalDateTime returnTime;
    private Long rentalPackageId;
    private List<String> additionalStops;
    private String couponCode;
    private String paymentMethod;

    public void setFareToken(String fareToken) {
        this.fareLockToken = fareToken;
    }

    public String getFareToken() {
        return this.fareLockToken;
    }

    public void setPassengerName(String passengerName) {
        this.customerName = passengerName;
    }

    public String getPassengerName() {
        return this.customerName;
    }

    public void setPassengerPhone(String passengerPhone) {
        this.customerPhone = passengerPhone;
    }

    public String getPassengerPhone() {
        return this.customerPhone;
    }

    public void setPaymentMode(String paymentMode) {
        this.paymentMethod = paymentMode;
    }

    public String getPaymentMode() {
        return this.paymentMethod;
    }

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
