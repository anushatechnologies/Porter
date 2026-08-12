package com.anushaporter.backend.dto;

public class PricingResponse {

    // Context
    private String vehicleId;
    private String vehicleName;
    private Double distanceKm;
    private Integer helperCount;
    private Double helperChargePerHead;
    private Double gstRate;

    // Fare breakdown
    private Double baseFare;
    private Double distanceFare;
    private Double weightCharge;
    private Double helperCharge;
    private Double waitingCharge;
    private Double tollCharge;
    private Double fuelCharge;
    private Double platformFee;
    private Double discount;
    private Double gst;
    private Double totalFare;

    // ── Getters & Setters ──────────────────────────────────────────────────────
    public String getVehicleId() { return vehicleId; }
    public void setVehicleId(String vehicleId) { this.vehicleId = vehicleId; }

    public String getVehicleName() { return vehicleName; }
    public void setVehicleName(String vehicleName) { this.vehicleName = vehicleName; }

    public Double getDistanceKm() { return distanceKm; }
    public void setDistanceKm(Double distanceKm) { this.distanceKm = distanceKm; }

    public Integer getHelperCount() { return helperCount; }
    public void setHelperCount(Integer helperCount) { this.helperCount = helperCount; }

    public Double getHelperChargePerHead() { return helperChargePerHead; }
    public void setHelperChargePerHead(Double helperChargePerHead) { this.helperChargePerHead = helperChargePerHead; }

    public Double getGstRate() { return gstRate; }
    public void setGstRate(Double gstRate) { this.gstRate = gstRate; }

    public Double getBaseFare() { return baseFare; }
    public void setBaseFare(Double baseFare) { this.baseFare = baseFare; }

    public Double getDistanceFare() { return distanceFare; }
    public void setDistanceFare(Double distanceFare) { this.distanceFare = distanceFare; }

    public Double getWeightCharge() { return weightCharge; }
    public void setWeightCharge(Double weightCharge) { this.weightCharge = weightCharge; }

    public Double getHelperCharge() { return helperCharge; }
    public void setHelperCharge(Double helperCharge) { this.helperCharge = helperCharge; }

    public Double getWaitingCharge() { return waitingCharge; }
    public void setWaitingCharge(Double waitingCharge) { this.waitingCharge = waitingCharge; }

    public Double getTollCharge() { return tollCharge; }
    public void setTollCharge(Double tollCharge) { this.tollCharge = tollCharge; }

    public Double getFuelCharge() { return fuelCharge; }
    public void setFuelCharge(Double fuelCharge) { this.fuelCharge = fuelCharge; }

    public Double getPlatformFee() { return platformFee; }
    public void setPlatformFee(Double platformFee) { this.platformFee = platformFee; }

    public Double getDiscount() { return discount; }
    public void setDiscount(Double discount) { this.discount = discount; }

    public Double getGst() { return gst; }
    public void setGst(Double gst) { this.gst = gst; }

    public Double getTotalFare() { return totalFare; }
    public void setTotalFare(Double totalFare) { this.totalFare = totalFare; }
}
