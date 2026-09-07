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
@Table(name = "passenger_zones")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PassengerZone {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "zone_name", nullable = false, length = 100)
    private String zoneName; // e.g. "Hyderabad Airport Zone", "Zone A Central"

    @Column(name = "city", nullable = false, length = 100)
    private String city;

    @Column(name = "zone_type", length = 50)
    @Builder.Default
    private String zoneType = "GENERAL"; // GENERAL, AIRPORT, OUTSTATION

    @Column(name = "center_lat")
    private Double centerLat;

    @Column(name = "center_lng")
    private Double centerLng;

    @Column(name = "radius_km")
    private Double radiusKm;

    @Column(name = "boundary_polygon_json", columnDefinition = "TEXT")
    private String boundaryPolygonJson;

    @Column(name = "airport_fee", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal airportFee = BigDecimal.ZERO;

    @Column(name = "parking_fee", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal parkingFee = BigDecimal.ZERO;

    @Column(name = "toll_fee", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal tollFee = BigDecimal.ZERO;

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
