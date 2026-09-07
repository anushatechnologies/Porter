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
@Table(name = "rental_packages")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RentalPackage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "package_name", nullable = false, length = 100)
    private String packageName; // e.g. "4 Hours / 40 KM", "8 Hours / 80 KM"

    @Column(name = "vehicle_category_code", nullable = false, length = 50)
    private String vehicleCategoryCode; // HATCHBACK, SEDAN, SUV, etc.

    @Column(name = "base_fare", precision = 10, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal baseFare = BigDecimal.ZERO;

    @Column(name = "included_distance_km", precision = 8, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal includedDistanceKm = BigDecimal.ZERO;

    @Column(name = "included_hours", precision = 8, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal includedHours = BigDecimal.ZERO;

    @Column(name = "extra_km_rate", precision = 10, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal extraKmRate = BigDecimal.ZERO;

    @Column(name = "extra_hour_rate", precision = 10, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal extraHourRate = BigDecimal.ZERO;

    @Column(name = "driver_allowance", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal driverAllowance = BigDecimal.ZERO;

    @Column(name = "night_charge", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal nightCharge = BigDecimal.ZERO;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
