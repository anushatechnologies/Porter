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
@Table(name = "passenger_vehicle_categories")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PassengerVehicleCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "category_code", unique = true, nullable = false, length = 50)
    private String categoryCode; // HATCHBACK, SEDAN, SUV, PREMIUM_SUV, LUXURY

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "passenger_capacity", nullable = false)
    @Builder.Default
    private Integer passengerCapacity = 4;

    @Column(name = "luggage_capacity", nullable = false)
    @Builder.Default
    private Integer luggageCapacity = 2;

    @Column(name = "base_fare", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal baseFare = BigDecimal.ZERO;

    @Column(name = "per_km_rate", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal perKmRate = BigDecimal.ZERO;

    @Column(name = "per_hour_rate", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal perHourRate = BigDecimal.ZERO;

    @Column(name = "minimum_fare", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal minimumFare = BigDecimal.ZERO;

    @Column(name = "minimum_km", precision = 8, scale = 2)
    @Builder.Default
    private BigDecimal minimumKm = BigDecimal.ZERO;

    @Column(name = "driver_allowance", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal driverAllowance = BigDecimal.ZERO;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Column(name = "display_order")
    @Builder.Default
    private Integer displayOrder = 1;

    @Column(name = "image_url")
    private String imageUrl;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
