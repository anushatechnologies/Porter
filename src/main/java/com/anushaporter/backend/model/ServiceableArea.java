package com.anushaporter.backend.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Data
@Table(name = "serviceable_areas", indexes = {
        @Index(name = "idx_serv_city", columnList = "city"),
        @Index(name = "idx_serv_pincode", columnList = "pincode")
})
public class ServiceableArea {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String city;

    @Column(nullable = false)
    private String areaName;

    @Column(nullable = false, length = 10)
    private String pincode;

    @Column(nullable = false)
    private Boolean isServiceable = true;

    private Double centerLat;
    private Double centerLng;
    private Double radiusKm = 5.0;

    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt = LocalDateTime.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public String getAreaName() { return areaName; }
    public void setAreaName(String areaName) { this.areaName = areaName; }

    public String getPincode() { return pincode; }
    public void setPincode(String pincode) { this.pincode = pincode; }

    public Boolean getIsServiceable() { return isServiceable != null ? isServiceable : true; }
    public void setIsServiceable(Boolean isServiceable) { this.isServiceable = isServiceable; }

    public Double getCenterLat() { return centerLat; }
    public void setCenterLat(Double centerLat) { this.centerLat = centerLat; }

    public Double getCenterLng() { return centerLng; }
    public void setCenterLng(Double centerLng) { this.centerLng = centerLng; }

    public Double getRadiusKm() { return radiusKm != null ? radiusKm : 5.0; }
    public void setRadiusKm(Double radiusKm) { this.radiusKm = radiusKm; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
