package com.anushaporter.backend.model;

import jakarta.persistence.*;

@Entity
@Table(name = "coupons")
public class Coupon {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String code;
    
    private String description;
    private Double discountPercentage; // e.g. 10.0 for 10%
    private Double flatDiscount; // e.g. 100.0 for flat ₹100
    private Double maxDiscount; // e.g. 250.0
    private Double minOrderAmount; // e.g. 200.0
    private Boolean active;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Double getDiscountPercentage() { return discountPercentage; }
    public void setDiscountPercentage(Double discountPercentage) { this.discountPercentage = discountPercentage; }

    public Double getFlatDiscount() { return flatDiscount; }
    public void setFlatDiscount(Double flatDiscount) { this.flatDiscount = flatDiscount; }

    public Double getMaxDiscount() { return maxDiscount; }
    public void setMaxDiscount(Double maxDiscount) { this.maxDiscount = maxDiscount; }

    public Double getMinOrderAmount() { return minOrderAmount; }
    public void setMinOrderAmount(Double minOrderAmount) { this.minOrderAmount = minOrderAmount; }

    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }
}
