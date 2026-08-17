package com.anushaporter.backend.model;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Data
@Table(name = "wallet_transactions")
public class WalletTransaction {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "driver_id", length = 64, nullable = false)
    private String driverId;

    @Column(name = "order_id", length = 64)
    private String orderId;

    // 'ORDER_EARNING', 'COMMISSION', 'WITHDRAWAL', 'REFUND'
    @Column(name = "transaction_type", length = 32, nullable = false)
    private String transactionType;

    @Column(name = "gross_amount")
    private Double grossAmount;

    @Column(name = "commission_amount")
    private Double commissionAmount;

    @Column(name = "amount", nullable = false)
    private Double amount; // Net change to wallet

    @Column(name = "balance_before", nullable = false)
    private Double balanceBefore;

    @Column(name = "balance_after", nullable = false)
    private Double balanceAfter;

    // 'PENDING', 'AVAILABLE', 'WITHDRAWN', 'SUCCESS'
    @Column(name = "status", length = 32, nullable = false)
    private String status;

    @Column(name = "reference_id", length = 128)
    private String referenceId;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void generateId() {
        if (this.id == null) {
            this.id = UUID.randomUUID().toString();
        }
    }
}
