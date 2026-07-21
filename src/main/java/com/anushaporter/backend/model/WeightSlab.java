package com.anushaporter.backend.model;

import jakarta.persistence.*;

@Entity
public class WeightSlab {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String vehicleId;
    private Double fromKg;
    private Double toKg;
    private Double price;

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getVehicleId() { return vehicleId; }
    public void setVehicleId(String vehicleId) { this.vehicleId = vehicleId; }

    public Double getFromKg() { return fromKg; }
    public void setFromKg(Double fromKg) { this.fromKg = fromKg; }

    public Double getToKg() { return toKg; }
    public void setToKg(Double toKg) { this.toKg = toKg; }

    public Double getPrice() { return price; }
    public void setPrice(Double price) { this.price = price; }
}
