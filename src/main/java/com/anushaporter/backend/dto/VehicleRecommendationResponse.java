package com.anushaporter.backend.dto;

import lombok.Data;
import java.util.List;

@Data
public class VehicleRecommendationResponse {
    private boolean success;
    private String recommendedVehicleId;
    private String recommendedVehicleName;
    private Integer capacityKg;
    private String dimensions;
    private Double estimatedFare;
    private String reason;
    private List<VehicleOption> alternativeOptions;

    @Data
    public static class VehicleOption {
        private String vehicleId;
        private String vehicleName;
        private Integer capacityKg;
        private String dimensions;
        private Double estimatedFare;
        private boolean suitable;
    }
}
