package com.anushaporter.backend.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Data
@Table(name = "driver_offers", indexes = {
        @Index(name = "idx_offers_booking_status", columnList = "bookingId, status"),
        @Index(name = "idx_offers_driver_status", columnList = "driverId, status"),
        @Index(name = "idx_offers_expires_at", columnList = "expiresAt")
})
public class DriverOffer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String bookingId;

    private Long orderId;

    @Column(nullable = false)
    private Long driverId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private DriverOfferStatus status = DriverOfferStatus.OFFERED;

    private Double radiusTierKm;
    private Double distanceKm;
    private Double pickupDistanceKm;
    private Double offeredFare;

    private LocalDateTime offeredAt;
    private LocalDateTime expiresAt;
    private LocalDateTime respondedAt;

    @PrePersist
    protected void onCreate() {
        if (offeredAt == null) offeredAt = LocalDateTime.now();
        if (status == null) status = DriverOfferStatus.OFFERED;
    }
}
