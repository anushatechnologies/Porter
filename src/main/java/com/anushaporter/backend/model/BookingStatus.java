package com.anushaporter.backend.model;

/**
 * Canonical lifecycle states for a Booking / Order.
 */
public enum BookingStatus {
    PENDING,
    SEARCHING,
    ASSIGNED,
    DRIVER_EN_ROUTE,
    DRIVER_ARRIVED,
    PICKED_UP,
    IN_TRANSIT,
    DELIVERED,
    COMPLETED,
    AUTO_ASSIGN_FAILED,
    CANCELLED,
    DRIVER_CANCELLED;

    public static BookingStatus fromString(String status) {
        if (status == null || status.isBlank()) {
            return PENDING;
        }
        String normalized = status.trim().toUpperCase().replace("-", "_").replace(" ", "_");
        // Normalize legacy / alias strings
        switch (normalized) {
            case "ACCEPTED":
            case "DRIVER_ASSIGNED":
                return ASSIGNED;
            case "ARRIVING_AT_PICKUP":
            case "ON_THE_WAY":
                return DRIVER_EN_ROUTE;
            case "DRIVER_REACHED":
            case "AT_PICKUP":
                return DRIVER_ARRIVED;
            case "PICKUP_STARTED":
            case "LOADED":
                return PICKED_UP;
            case "TRANSIT":
            case "ON_TRIP":
                return IN_TRANSIT;
            case "OTP_VERIFIED":
            case "PAYMENT_CONFIRMATION_PENDING":
                return DELIVERED;
            case "DRIVER_NOT_FOUND":
            case "FAILED":
                return AUTO_ASSIGN_FAILED;
            default:
                try {
                    return BookingStatus.valueOf(normalized);
                } catch (IllegalArgumentException e) {
                    return PENDING;
                }
        }
    }
}
