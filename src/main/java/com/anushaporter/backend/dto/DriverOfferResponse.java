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
    private Double pickupDistanceKm;
    private Double offeredFare;
    private String pickupAddress;
    private String dropAddress;
    private Double pickupLat;
    private Double pickupLng;
    private Double dropLat;
    private Double dropLng;
    private String serviceName;
    private String goodsCategory;
    private Integer helpersCount;
    private LocalDateTime offeredAt;
    private LocalDateTime expiresAt;
    private Long remainingSeconds;
}
