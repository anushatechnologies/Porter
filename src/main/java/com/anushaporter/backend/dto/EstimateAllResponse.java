package com.anushaporter.backend.dto;

import java.util.Map;

public class EstimateAllResponse {
    private Map<String, Double>  estimates;

    public EstimateAllResponse(Map<String, Double> estimates) {
        this.estimates = estimates; 
    }

    public Map<String, Double> getEstimates() {
        return estimates;
    }

    public void setEstimates(Map<String, Double> estimates) {
        this.estimates = estimates;
    }
}
