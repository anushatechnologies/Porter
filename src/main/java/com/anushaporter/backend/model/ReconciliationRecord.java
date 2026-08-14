package com.anushaporter.backend.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "reconciliation_records", indexes = {
    @Index(name = "idx_recon_payment", columnList = "paymentId"),
    @Index(name = "idx_recon_match", columnList = "matchStatus")
})
public class ReconciliationRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32)
    private String reconciliationDate; // e.g. "2026-08-14"

    @Column(nullable = false, length = 64)
    private String paymentId;

    private String bookingId;

    private Double internalAmount;
    private Double gatewayAmount;

    @Column(length = 32)
    private String internalStatus;

    @Column(length = 32)
    private String gatewayStatus;

    @Column(nullable = false, length = 32)
    private String matchStatus; // "MATCHED", "MISMATCH", "PENDING_INVESTIGATION"

    @Column(length = 1000)
    private String notes;

    private LocalDateTime reconciledAt;

    @PrePersist
    public void prePersist() {
        if (reconciledAt == null) reconciledAt = LocalDateTime.now();
    }

    // Getters and Setters

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getReconciliationDate() { return reconciliationDate; }
    public void setReconciliationDate(String reconciliationDate) { this.reconciliationDate = reconciliationDate; }

    public String getPaymentId() { return paymentId; }
    public void setPaymentId(String paymentId) { this.paymentId = paymentId; }

    public String getBookingId() { return bookingId; }
    public void setBookingId(String bookingId) { this.bookingId = bookingId; }

    public Double getInternalAmount() { return internalAmount; }
    public void setInternalAmount(Double internalAmount) { this.internalAmount = internalAmount; }

    public Double getGatewayAmount() { return gatewayAmount; }
    public void setGatewayAmount(Double gatewayAmount) { this.gatewayAmount = gatewayAmount; }

    public String getInternalStatus() { return internalStatus; }
    public void setInternalStatus(String internalStatus) { this.internalStatus = internalStatus; }

    public String getGatewayStatus() { return gatewayStatus; }
    public void setGatewayStatus(String gatewayStatus) { this.gatewayStatus = gatewayStatus; }

    public String getMatchStatus() { return matchStatus; }
    public void setMatchStatus(String matchStatus) { this.matchStatus = matchStatus; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public LocalDateTime getReconciledAt() { return reconciledAt; }
    public void setReconciledAt(LocalDateTime reconciledAt) { this.reconciledAt = reconciledAt; }
}
