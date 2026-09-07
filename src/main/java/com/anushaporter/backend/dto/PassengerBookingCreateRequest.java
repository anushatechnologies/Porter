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
}
