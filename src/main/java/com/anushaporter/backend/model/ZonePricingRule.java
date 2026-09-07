package com.anushaporter.backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "zone_pricing_rules")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ZonePricingRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "zone_id", nullable = false)
    private Long zoneId;

    @Column(name = "vehicle_category_code", length = 50)
    private String vehicleCategoryCode;

    @Column(name = "service_code", length = 50)
    private String serviceCode;

    @Column(name = "base_fare_adjustment", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal baseFareAdjustment = BigDecimal.ZERO;

    @Column(name = "per_km_adjustment", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal perKmAdjustment = BigDecimal.ZERO;

    @Column(name = "fixed_surcharge", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal fixedSurcharge = BigDecimal.ZERO;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;
}
