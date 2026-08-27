package com.anushaporter.backend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FaceAuthResponse {

    private boolean success;
    private Boolean authenticated;
    private Long driverId;
    private String driverName;
    private String phone;
    private Double confidence;
    private Integer matchPercentage;
    private Double livenessScore;
    private Integer faceCount;
    private String token;
    private String message;
    private String photoUrl;

    public FaceAuthResponse() {}

    public static FaceAuthResponse failure(String message) {
        FaceAuthResponse res = new FaceAuthResponse();
        res.setSuccess(false);
        res.setAuthenticated(false);
        res.setMessage(message);
        res.setConfidence(0.0);
        res.setMatchPercentage(0);
        return res;
    }

    public static FaceAuthResponse failure(String message, Double confidence, Double livenessScore, Integer faceCount) {
        FaceAuthResponse res = new FaceAuthResponse();
        res.setSuccess(false);
        res.setAuthenticated(false);
        res.setMessage(message);
        res.setConfidence(confidence != null ? confidence : 0.0);
        res.setMatchPercentage(confidence != null ? (int) Math.round(confidence * 100) : 0);
        res.setLivenessScore(livenessScore);
        res.setFaceCount(faceCount);
        return res;
    }

    public static FaceAuthResponse success(Long driverId, String driverName, String phone, Double confidence, Double livenessScore, String token, String photoUrl, String message) {
        FaceAuthResponse res = new FaceAuthResponse();
        res.setSuccess(true);
        res.setAuthenticated(true);
        res.setDriverId(driverId);
        res.setDriverName(driverName);
        res.setPhone(phone);
        res.setConfidence(confidence != null ? Math.round(confidence * 1000.0) / 1000.0 : 1.0);
        res.setMatchPercentage(confidence != null ? (int) Math.round(confidence * 100) : 100);
        res.setLivenessScore(livenessScore != null ? Math.round(livenessScore * 1000.0) / 1000.0 : 1.0);
        res.setFaceCount(1);
        res.setToken(token);
        res.setPhotoUrl(photoUrl);
        res.setMessage(message);
        return res;
    }

    public static FaceAuthResponse registrationSuccess(Long driverId, String driverName, Double livenessScore, String photoUrl, String message) {
        FaceAuthResponse res = new FaceAuthResponse();
        res.setSuccess(true);
        res.setAuthenticated(true);
        res.setDriverId(driverId);
        res.setDriverName(driverName);
        res.setLivenessScore(livenessScore != null ? Math.round(livenessScore * 1000.0) / 1000.0 : 1.0);
        res.setConfidence(1.0);
        res.setMatchPercentage(100);
        res.setFaceCount(1);
        res.setPhotoUrl(photoUrl);
        res.setMessage(message);
        return res;
    }
}
