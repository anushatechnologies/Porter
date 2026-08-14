package com.anushaporter.backend.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "driver_earnings", indexes = {
    @Index(name = "idx_earn_driver", columnList = "driverId"),
    @Index(name = "idx_earn_booking", columnList = "bookingId"),
    @Index(name = "idx_earn_payment", columnList = "paymentId"),
    @Index(name = "idx_earn_created", columnList = "createdAt")
})
public class DriverEarnings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String driverId;

    @Column(nullable = false, length = 64)
    private String bookingId;

    @Column(length = 64)
    private String paymentId;

    @Column(nullable = false)
    private Double grossFare;

    @Column(nullable = false)
    private Double platformCommission;

    @Column(nullable = false)
    private Double driverNetEarning;

    private Double taxAmount = 0.0;
    private Double adjustments = 0.0;

    @Column(length = 32)
    private String paymentStatus = "PAID"; // "PAID", "REFUNDED"

    @Column(length = 32)
    private String settlementStatus = "PENDING"; // "PENDING", "PROCESSING", "SETTLED"

    @Column(length = 64)
    private String payoutId;

    private LocalDateTime rideCompletedAt;
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (settlementStatus == null) settlementStatus = "PENDING";
        if (paymentStatus == null) paymentStatus = "PAID";
    }

    // Getters and Setters

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getDriverId() { return driverId; }
    public void setDriverId(String driverId) { this.driverId = driverId; }

    public String getBookingId() { return bookingId; }
    public void setBookingId(String bookingId) { this.bookingId = bookingId; }

    public String getPaymentId() { return paymentId; }
    public void setPaymentId(String paymentId) { this.paymentId = paymentId; }

    public Double getGrossFare() { return grossFare; }
    public void setGrossFare(Double grossFare) { this.grossFare = grossFare; }

    public Double getPlatformCommission() { return platformCommission; }
    public void setPlatformCommission(Double platformCommission) { this.platformCommission = platformCommission; }

    public Double getDriverNetEarning() { return driverNetEarning; }
    public void setDriverNetEarning(Double driverNetEarning) { this.driverNetEarning = driverNetEarning; }

    public Double getTaxAmount() { return taxAmount; }
    public void setTaxAmount(Double taxAmount) { this.taxAmount = taxAmount; }

    public Double getAdjustments() { return adjustments; }
    public void setAdjustments(Double adjustments) { this.adjustments = adjustments; }

    public String getPaymentStatus() { return paymentStatus; }
    public void setPaymentStatus(String paymentStatus) { this.paymentStatus = paymentStatus; }

    public String getSettlementStatus() { return settlementStatus; }
    public void setSettlementStatus(String settlementStatus) { this.settlementStatus = settlementStatus; }

    public String getPayoutId() { return payoutId; }
    public void setPayoutId(String payoutId) { this.payoutId = payoutId; }

    public LocalDateTime getRideCompletedAt() { return rideCompletedAt; }
    public void setRideCompletedAt(LocalDateTime rideCompletedAt) { this.rideCompletedAt = rideCompletedAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
