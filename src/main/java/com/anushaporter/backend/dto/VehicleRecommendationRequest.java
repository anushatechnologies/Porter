package com.anushaporter.backend.dto;

import lombok.Data;

@Data
public class VehicleRecommendationRequest {
    private Double weightKg;
    private String goodsCategory;
    private Double lengthFt;
    private Double widthFt;
    private Double heightFt;
    private Integer helperCount;
    private Double distanceKm;
    private String houseSize;
}
