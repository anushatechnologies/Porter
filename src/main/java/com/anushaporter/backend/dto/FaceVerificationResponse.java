package com.anushaporter.backend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class FaceVerificationResponse {
    private boolean success;
    private boolean isFace;
    private boolean faceVerified;
    private int faceCount;
    private Double confidence;
    private Integer matchPercentage;
    private String message;
    private String url;
    private String fileUrl;

    public FaceVerificationResponse() {}

    // Success constructor
    public FaceVerificationResponse(boolean success, boolean isFace, int faceCount, Double confidence, String message, String url) {
        this.success = success;
        this.isFace = isFace;
        this.faceVerified = success && isFace;
        this.faceCount = faceCount;
        this.confidence = confidence;
        this.matchPercentage = confidence != null ? (int) Math.round(confidence * 100) : null;
        this.message = message;
        this.url = url;
        this.fileUrl = url;
    }

    // Error constructor
    public FaceVerificationResponse(boolean success, boolean isFace, int faceCount, String message) {
        this.success = success;
        this.isFace = isFace;
        this.faceVerified = false;
        this.faceCount = faceCount;
        this.message = message;
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public boolean isFace() { return isFace; }
    public void setFace(boolean face) { isFace = face; }

    public boolean isFaceVerified() { return faceVerified; }
    public void setFaceVerified(boolean faceVerified) { this.faceVerified = faceVerified; }

    public int getFaceCount() { return faceCount; }
    public void setFaceCount(int faceCount) { this.faceCount = faceCount; }

    public Double getConfidence() { return confidence; }
    public void setConfidence(Double confidence) { this.confidence = confidence; }

    public Integer getMatchPercentage() { return matchPercentage; }
    public void setMatchPercentage(Integer matchPercentage) { this.matchPercentage = matchPercentage; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getFileUrl() { return fileUrl; }
    public void setFileUrl(String fileUrl) { this.fileUrl = fileUrl; }
}
