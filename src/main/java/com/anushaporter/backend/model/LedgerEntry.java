package com.anushaporter.backend.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "financial_ledger", indexes = {
    @Index(name = "idx_ledger_booking", columnList = "bookingId"),
    @Index(name = "idx_ledger_payment", columnList = "paymentId"),
    @Index(name = "idx_ledger_driver", columnList = "driverId"),
    @Index(name = "idx_ledger_type", columnList = "type"),
    @Index(name = "idx_ledger_entry_no", columnList = "entryNumber", unique = true)
})
public class LedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 64)
    private String entryNumber; // Unique UUID or sequence number

    @Column(length = 128)
    private String transactionId;

    @Column(length = 64)
    private String bookingId;

    @Column(length = 64)
    private String paymentId;

    @Column(length = 64)
    private String driverId;

    @Column(length = 64)
    private String customerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private LedgerType type;

    @Column(nullable = false, length = 16)
    private String entryType; // "DEBIT" or "CREDIT"

    @Column(nullable = false)
    private Double amount;

    private String currency = "INR";

    private Double balanceAfter;

    @Column(length = 500)
    private String description;

    @Column(length = 128)
    private String referenceId; // Gateway UTR or payment ID

    @Column(length = 64)
    private String createdBy = "SYSTEM"; // "SYSTEM", "ADMIN", "GATEWAY_WEBHOOK"

    @Column(length = 128)
    private String immutableHash; // SHA-256 integrity hash for auditing

    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (currency == null) currency = "INR";
        if (createdBy == null) createdBy = "SYSTEM";
    }

    // Getters and Setters

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getEntryNumber() { return entryNumber; }
    public void setEntryNumber(String entryNumber) { this.entryNumber = entryNumber; }

    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }

    public String getBookingId() { return bookingId; }
    public void setBookingId(String bookingId) { this.bookingId = bookingId; }

    public String getPaymentId() { return paymentId; }
    public void setPaymentId(String paymentId) { this.paymentId = paymentId; }

    public String getDriverId() { return driverId; }
    public void setDriverId(String driverId) { this.driverId = driverId; }

    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }

    public LedgerType getType() { return type; }
    public void setType(LedgerType type) { this.type = type; }

    public String getEntryType() { return entryType; }
    public void setEntryType(String entryType) { this.entryType = entryType; }

    public Double getAmount() { return amount; }
    public void setAmount(Double amount) { this.amount = amount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public Double getBalanceAfter() { return balanceAfter; }
    public void setBalanceAfter(Double balanceAfter) { this.balanceAfter = balanceAfter; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getReferenceId() { return referenceId; }
    public void setReferenceId(String referenceId) { this.referenceId = referenceId; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public String getImmutableHash() { return immutableHash; }
    public void setImmutableHash(String immutableHash) { this.immutableHash = immutableHash; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
