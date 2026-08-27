package com.anushaporter.backend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DriverPhotoUploadResponse {
    private boolean success;
    private Long driverId;
    private String driverName;
    private String url;
    private String message;
    private String error;

    public static DriverPhotoUploadResponse success(Long driverId, String driverName, String url, String message) {
        return DriverPhotoUploadResponse.builder()
                .success(true)
                .driverId(driverId)
                .driverName(driverName)
                .url(url)
                .message(message)
                .build();
    }

    public static DriverPhotoUploadResponse failure(String error) {
        return DriverPhotoUploadResponse.builder()
                .success(false)
                .error(error)
                .message(error)
                .build();
    }
}
