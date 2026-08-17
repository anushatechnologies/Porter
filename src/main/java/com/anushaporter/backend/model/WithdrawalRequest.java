package com.anushaporter.backend.model;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Data
@Table(name = "withdrawal_requests")
public class WithdrawalRequest {

    // e.g. WDR_789456
    @Id
    @Column(name = "id", length = 64)
    private String id;

    @Column(name = "driver_id", length = 64, nullable = false)
    private String driverId;

    @Column(name = "bank_account_id", length = 64)
    private String bankAccountId;

    @Column(name = "amount", nullable = false)
    private Double amount;

    @Column(name = "held_amount", nullable = false)
    private Double heldAmount;

    // 'PENDING_ADMIN_APPROVAL', 'ADMIN_APPROVED', 'INITIATED', 'PROCESSING', 'COMPLETED', 'REJECTED', 'FAILED'
    @Column(name = "status", length = 32, nullable = false)
    private String status = "PENDING_ADMIN_APPROVAL";

    // 'RAZORPAYX', 'CASHFREE', 'AU_BANK'
    @Column(name = "payout_provider", length = 64)
    private String payoutProvider;

    @Column(name = "payout_reference", length = 128)
    private String payoutReference;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @CreationTimestamp
    @Column(name = "requested_at", updatable = false)
    private LocalDateTime requestedAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @PrePersist
    public void generateId() {
        if (this.id == null) {
            this.id = "WDR_" + (100000 + (long)(Math.random() * 900000)); // e.g. WDR_789456
        }
    }
}
