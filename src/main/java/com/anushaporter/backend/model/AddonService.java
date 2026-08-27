package com.anushaporter.backend.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Data
@Table(name = "addon_services")
public class AddonService {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String addonId; // e.g. "addon_load_assist", "addon_installation", "addon_unpacking", "addon_dismantling"

    private String name; // e.g. "Loading & Unloading Service", "Installation / Un-installation"
    private String category; // "truck", "packers", "all"
    private String serviceType; // "load_assist", "packing", "labour", "assembly"
    
    private String description; // "Starts @ ₹7 per item", "Professional mounting & installation"
    private String subtitle; // "Earliest pickup in 30 min"
    private String icon; // "truck-loading", "wrench", "box-open"

    private Double basePrice; // e.g. 0, 199.0, 300.0
    private Double perItemRate; // e.g. 7.0 for truck load assist
    private Double price; // Standard flat price if applicable, e.g. 300.0, 199.0, 249.0
    private String pricingUnit; // "per_item", "flat", "per_helper"
    private String currency = "INR";

    // Supported vehicle capacities (e.g. "[90, 500]" or "ALL")
    private String applicableVehicles; 

    // Goods type applicability (e.g. "commercial,personal")
    private String applicableGoodsTypes;

    private Integer displayOrder = 1;
    private Boolean isActive = true;
    private Boolean isRecommended = false;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (updatedAt == null) updatedAt = LocalDateTime.now();
        if (currency == null) currency = "INR";
        if (isActive == null) isActive = true;
        if (isRecommended == null) isRecommended = false;
        if (displayOrder == null) displayOrder = 1;
        if (price == null && basePrice != null) price = basePrice;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
