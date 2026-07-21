package com.anushaporter.backend.dto;

public class PricingResponse {
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

    // Getters and Setters
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
