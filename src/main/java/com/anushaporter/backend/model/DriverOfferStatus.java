package com.anushaporter.backend.model;

/**
 * Status of an individual offer extended to a driver for a booking.
 */
public enum DriverOfferStatus {
    OFFERED,
    ACCEPTED,
    REJECTED,
    EXPIRED,
    CANCELLED,
    TOO_LATE;

    public static DriverOfferStatus fromString(String status) {
        if (status == null || status.isBlank()) {
            return OFFERED;
        }
        try {
            return DriverOfferStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return OFFERED;
        }
    }
}
