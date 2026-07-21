package com.anushaporter.backend.model;

import jakarta.persistence.*;

@Entity
public class DistanceSlab {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String city;
    private String vehicleId;
    private Double fromKm;
    private Double toKm;
    private Double pricePerKm;
    private Double baseFare; // Optional if this slab has a flat base fare instead of per km

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public String getVehicleId() { return vehicleId; }
    public void setVehicleId(String vehicleId) { this.vehicleId = vehicleId; }

    public Double getFromKm() { return fromKm; }
    public void setFromKm(Double fromKm) { this.fromKm = fromKm; }

    public Double getToKm() { return toKm; }
    public void setToKm(Double toKm) { this.toKm = toKm; }

    public Double getPricePerKm() { return pricePerKm; }
    public void setPricePerKm(Double pricePerKm) { this.pricePerKm = pricePerKm; }

    public Double getBaseFare() { return baseFare; }
    public void setBaseFare(Double baseFare) { this.baseFare = baseFare; }
}
