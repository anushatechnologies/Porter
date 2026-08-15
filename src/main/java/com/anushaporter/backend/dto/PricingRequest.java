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

    private String serviceId;
    private String vehicleType;
    private String id;

    // Getters and Setters
    public String getVehicleId() {
        if (vehicleId != null && !vehicleId.isBlank()) return vehicleId;
        if (serviceId != null && !serviceId.isBlank()) return serviceId;
        if (vehicleType != null && !vehicleType.isBlank()) return vehicleType;
        if (id != null && !id.isBlank()) return id;
        return null;
    }
    public void setVehicleId(String vehicleId) { this.vehicleId = vehicleId; }

    public String getServiceId() { return serviceId; }
    public void setServiceId(String serviceId) { this.serviceId = serviceId; }

    public String getVehicleType() { return vehicleType; }
    public void setVehicleType(String vehicleType) { this.vehicleType = vehicleType; }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

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
