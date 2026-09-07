package com.anushaporter.backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "passenger_pricing_rules")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PassengerPricingRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "pricing_version_id", nullable = false, length = 50)
    private String pricingVersionId; // e.g. PV-2026-09-07-01

    @Column(name = "service_code", nullable = false, length = 50)
    private String serviceCode; // ONE_WAY, ROUND_TRIP, RENTAL, AIRPORT_TRANSFER

    @Column(name = "vehicle_category_code", nullable = false, length = 50)
    private String vehicleCategoryCode; // HATCHBACK, SEDAN, SUV, PREMIUM_SUV, LUXURY

    @Column(name = "base_fare", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal baseFare = BigDecimal.ZERO;

    @Column(name = "minimum_km", precision = 8, scale = 2)
    @Builder.Default
    private BigDecimal minimumKm = BigDecimal.ZERO;

    @Column(name = "per_km_rate", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal perKmRate = BigDecimal.ZERO;

    @Column(name = "minimum_fare", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal minimumFare = BigDecimal.ZERO;

    @Column(name = "per_minute_rate", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal perMinuteRate = BigDecimal.ZERO;

    @Column(name = "driver_allowance", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal driverAllowance = BigDecimal.ZERO;

    @Column(name = "free_waiting_minutes")
    @Builder.Default
    private Integer freeWaitingMinutes = 15;

    @Column(name = "waiting_charge_per_15_min", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal waitingChargePer15Min = new BigDecimal("50.00");

    @Column(name = "waiting_charge_per_hour", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal waitingChargePerHour = new BigDecimal("150.00");

    @Column(name = "night_charge_fixed", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal nightChargeFixed = new BigDecimal("150.00");

    @Column(name = "night_charge_percentage", precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal nightChargePercentage = BigDecimal.ZERO;

    @Column(name = "night_charge_per_km", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal nightChargePerKm = BigDecimal.ZERO;

    @Column(name = "night_start_hour")
    @Builder.Default
    private Integer nightStartHour = 23; // 11:00 PM

    @Column(name = "night_end_hour")
    @Builder.Default
    private Integer nightEndHour = 5; // 5:00 AM

    @Column(name = "first_stop_free")
    @Builder.Default
    private Boolean firstStopFree = true;

    @Column(name = "additional_stop_charge", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal additionalStopCharge = new BigDecimal("50.00");

    @Column(name = "toll_handling", length = 30)
    @Builder.Default
    private String tollHandling = "ACTUAL"; // INCLUDED, EXCLUDED, ACTUAL, FIXED

    @Column(name = "fixed_toll_amount", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal fixedTollAmount = BigDecimal.ZERO;

    @Column(name = "parking_handling", length = 30)
    @Builder.Default
    private String parkingHandling = "ACTUAL"; // INCLUDED, EXCLUDED, ACTUAL, FIXED

    @Column(name = "fixed_parking_amount", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal fixedParkingAmount = BigDecimal.ZERO;

    @Column(name = "driver_commission_percentage", precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal driverCommissionPercentage = new BigDecimal("20.00");

    @Column(name = "tax_percentage", precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal taxPercentage = new BigDecimal("5.00"); // 5% GST

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
