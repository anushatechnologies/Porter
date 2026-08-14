package com.anushaporter.backend.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "driver_payout_accounts", indexes = {
    @Index(name = "idx_payout_acc_driver", columnList = "driverId", unique = true)
})
public class DriverPayoutAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 64)
    private String driverId;

    @Column(nullable = false, length = 128)
    private String accountHolderName;

    @Column(length = 128)
    private String bankName;

    @Column(length = 128)
    private String accountNumber; // Stored securely

    @Column(length = 32)
    private String accountNumberMasked; // e.g. "XXXX XXXX 4582"

    @Column(length = 32)
    private String ifscCode;

    @Column(length = 128)
    private String upiId;

    @Column(length = 128)
    private String beneficiaryId;

    @Column(length = 32)
    private String verificationStatus = "VERIFIED"; // "VERIFIED", "PENDING", "REJECTED"

    private Boolean isPrimary = true;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (updatedAt == null) updatedAt = LocalDateTime.now();
        if (verificationStatus == null) verificationStatus = "VERIFIED";
        if (isPrimary == null) isPrimary = true;
        maskAccountNumber();
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
        maskAccountNumber();
    }

    public void maskAccountNumber() {
        if (accountNumber != null && accountNumber.length() >= 4) {
            String last4 = accountNumber.substring(accountNumber.length() - 4);
            this.accountNumberMasked = "XXXX XXXX " + last4;
        } else if (accountNumber != null) {
            this.accountNumberMasked = "XXXX";
        }
    }

    // Getters and Setters

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getDriverId() { return driverId; }
    public void setDriverId(String driverId) { this.driverId = driverId; }

    public String getAccountHolderName() { return accountHolderName; }
    public void setAccountHolderName(String accountHolderName) { this.accountHolderName = accountHolderName; }

    public String getBankName() { return bankName; }
    public void setBankName(String bankName) { this.bankName = bankName; }

    public String getAccountNumber() { return accountNumber; }
    public void setAccountNumber(String accountNumber) {
        this.accountNumber = accountNumber;
        maskAccountNumber();
    }

    public String getAccountNumberMasked() { return accountNumberMasked; }
    public void setAccountNumberMasked(String accountNumberMasked) { this.accountNumberMasked = accountNumberMasked; }

    public String getIfscCode() { return ifscCode; }
    public void setIfscCode(String ifscCode) { this.ifscCode = ifscCode; }

    public String getUpiId() { return upiId; }
    public void setUpiId(String upiId) { this.upiId = upiId; }

    public String getBeneficiaryId() { return beneficiaryId; }
    public void setBeneficiaryId(String beneficiaryId) { this.beneficiaryId = beneficiaryId; }

    public String getVerificationStatus() { return verificationStatus; }
    public void setVerificationStatus(String verificationStatus) { this.verificationStatus = verificationStatus; }

    public Boolean getIsPrimary() { return isPrimary; }
    public void setIsPrimary(Boolean isPrimary) { this.isPrimary = isPrimary; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
