package com.anushaporter.backend.dto;

import com.anushaporter.backend.model.DriverOfferStatus;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class DriverOfferResponse {
    private Long offerId;
    private String bookingId;
    private Long orderId;
    private Long driverId;
    private DriverOfferStatus status;
    private Double radiusTierKm;
    private Double distanceKm;
    private Integer distanceMeters;
    private Integer durationSeconds;
    private Double pickupDistanceKm;
    private Double offeredFare;
    private String pickupAddress;
    private String dropAddress;
    private String pickup;
    private String drop;
    private Double pickupLat;
    private Double pickupLng;
    private Double dropLat;
    private Double dropLng;
    private Double pickupLatitude;
    private Double pickupLongitude;
    private Double dropLatitude;
    private Double dropLongitude;
    private String serviceName;
    private String goodsCategory;
    private String serviceType;
    private String serviceLabel;
    private Integer passengerCount;
    private String startOtp;
    private Integer helpersCount;
    private LocalDateTime offeredAt;
    private LocalDateTime expiresAt;
    private Long remainingSeconds;
}
