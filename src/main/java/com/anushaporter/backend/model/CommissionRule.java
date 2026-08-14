package com.anushaporter.backend.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "commission_rules")
public class CommissionRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 64)
    private String ruleId;

    @Column(nullable = false, length = 64)
    private String serviceCategory = "ALL"; // "vehicle", "two_wheeler", "packers", "ALL"

    @Column(nullable = false, length = 32)
    private String commissionType = "PERCENTAGE"; // "PERCENTAGE", "FIXED", "HYBRID"

    @Column(nullable = false)
    private Double percentageRate = 10.0; // 10% platform commission default

    private Double fixedAmount = 0.0;
    private Double minCommission = 5.0;
    private Double maxCommission = 2000.0;
    private Double taxPercentage = 18.0; // GST on commission

    private Boolean isActive = true;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (updatedAt == null) updatedAt = LocalDateTime.now();
        if (isActive == null) isActive = true;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Getters and Setters

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getRuleId() { return ruleId; }
    public void setRuleId(String ruleId) { this.ruleId = ruleId; }

    public String getServiceCategory() { return serviceCategory; }
    public void setServiceCategory(String serviceCategory) { this.serviceCategory = serviceCategory; }

    public String getCommissionType() { return commissionType; }
    public void setCommissionType(String commissionType) { this.commissionType = commissionType; }

    public Double getPercentageRate() { return percentageRate; }
    public void setPercentageRate(Double percentageRate) { this.percentageRate = percentageRate; }

    public Double getFixedAmount() { return fixedAmount; }
    public void setFixedAmount(Double fixedAmount) { this.fixedAmount = fixedAmount; }

    public Double getMinCommission() { return minCommission; }
    public void setMinCommission(Double minCommission) { this.minCommission = minCommission; }

    public Double getMaxCommission() { return maxCommission; }
    public void setMaxCommission(Double maxCommission) { this.maxCommission = maxCommission; }

    public Double getTaxPercentage() { return taxPercentage; }
    public void setTaxPercentage(Double taxPercentage) { this.taxPercentage = taxPercentage; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
