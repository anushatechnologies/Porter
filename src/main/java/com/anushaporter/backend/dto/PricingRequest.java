package com.anushaporter.backend.dto;

public class PricingRequest {
    private String vehicleId;
    private Double distanceKm;
    private Double weightKg;
    private Integer helperCount;
    private Double pickupLat;
    private Double pickupLng;
    private Double dropLat;
    private Double dropLng;
    private Double waitingMins;
    private Double tollCharge;

    // Getters and Setters
    public String getVehicleId() { return vehicleId; }
    public void setVehicleId(String vehicleId) { this.vehicleId = vehicleId; }

    public Double getDistanceKm() { return distanceKm; }
    public void setDistanceKm(Double distanceKm) { this.distanceKm = distanceKm; }

    public Double getWeightKg() { return weightKg; }
    public void setWeightKg(Double weightKg) { this.weightKg = weightKg; }

    public Integer getHelperCount() { return helperCount; }
    public void setHelperCount(Integer helperCount) { this.helperCount = helperCount; }

    public Double getPickupLat() { return pickupLat; }
    public void setPickupLat(Double pickupLat) { this.pickupLat = pickupLat; }

    public Double getPickupLng() { return pickupLng; }
    public void setPickupLng(Double pickupLng) { this.pickupLng = pickupLng; }

    public Double getDropLat() { return dropLat; }
    public void setDropLat(Double dropLat) { this.dropLat = dropLat; }

    public Double getDropLng() { return dropLng; }
    public void setDropLng(Double dropLng) { this.dropLng = dropLng; }

    public Double getWaitingMins() { return waitingMins; }
    public void setWaitingMins(Double waitingMins) { this.waitingMins = waitingMins; }

    public Double getTollCharge() { return tollCharge; }
    public void setTollCharge(Double tollCharge) { this.tollCharge = tollCharge; }
}
