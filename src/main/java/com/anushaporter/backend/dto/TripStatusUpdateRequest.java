package com.anushaporter.backend.dto;

import com.anushaporter.backend.model.BookingStatus;
import lombok.Data;

@Data
public class TripStatusUpdateRequest {
    private BookingStatus targetStatus;
    private String rawStatus;
    private Double currentLat;
    private Double currentLng;
    private String remarks;
    private String cancellationReason;
}
