package com.anushaporter.backend.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "driver_payout_records", indexes = {
    @Index(name = "idx_payout_rec_id", columnList = "payoutId", unique = true),
    @Index(name = "idx_payout_rec_driver", columnList = "driverId"),
    @Index(name = "idx_payout_rec_status", columnList = "status"),
    @Index(name = "idx_payout_rec_idempotency", columnList = "idempotencyKey", unique = true)
})
public class DriverPayoutRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 64)
    private String payoutId;

    @Column(nullable = false, length = 64)
    private String driverId;

    private String driverName;

    @Column(nullable = false)
    private Double amount;

    private String currency = "INR";

    @Column(length = 32)
    private String payoutMode = "MANUAL"; // "DAILY", "WEEKLY", "MANUAL", "INSTANT"

    @Column(length = 32)
    private String destinationType = "BANK_ACCOUNT"; // "BANK_ACCOUNT", "UPI"

    @Column(length = 128)
    private String destinationMasked; // "Bank Account ••••4582" or "driver@upi"

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PayoutStatus status = PayoutStatus.PENDING;

    @Column(length = 128)
    private String utr; // Bank / Gateway settlement UTR reference

    @Column(length = 500)
    private String failureReason;

    @Column(unique = true, length = 128)
    private String idempotencyKey;

    @Column(length = 128)
    private String gatewayPayoutId;

    private LocalDateTime requestedAt;
    private LocalDateTime settledAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (requestedAt == null) requestedAt = LocalDateTime.now();
        if (updatedAt == null) updatedAt = LocalDateTime.now();
        if (currency == null) currency = "INR";
        if (status == null) status = PayoutStatus.PENDING;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Getters and Setters

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getPayoutId() { return payoutId; }
    public void setPayoutId(String payoutId) { this.payoutId = payoutId; }

    public String getDriverId() { return driverId; }
    public void setDriverId(String driverId) { this.driverId = driverId; }

    public String getDriverName() { return driverName; }
    public void setDriverName(String driverName) { this.driverName = driverName; }

    public Double getAmount() { return amount; }
    public void setAmount(Double amount) { this.amount = amount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getPayoutMode() { return payoutMode; }
    public void setPayoutMode(String payoutMode) { this.payoutMode = payoutMode; }

    public String getDestinationType() { return destinationType; }
    public void setDestinationType(String destinationType) { this.destinationType = destinationType; }

    public String getDestinationMasked() { return destinationMasked; }
    public void setDestinationMasked(String destinationMasked) { this.destinationMasked = destinationMasked; }

    public PayoutStatus getStatus() { return status; }
    public void setStatus(PayoutStatus status) { this.status = status; }

    public String getUtr() { return utr; }
    public void setUtr(String utr) { this.utr = utr; }

    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }

    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }

    public String getGatewayPayoutId() { return gatewayPayoutId; }
    public void setGatewayPayoutId(String gatewayPayoutId) { this.gatewayPayoutId = gatewayPayoutId; }

    public LocalDateTime getRequestedAt() { return requestedAt; }
    public void setRequestedAt(LocalDateTime requestedAt) { this.requestedAt = requestedAt; }

    public LocalDateTime getSettledAt() { return settledAt; }
    public void setSettledAt(LocalDateTime settledAt) { this.settledAt = settledAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
