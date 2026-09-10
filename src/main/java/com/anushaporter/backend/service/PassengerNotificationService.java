package com.anushaporter.backend.service;

import com.anushaporter.backend.config.handler.TelemetryWebSocketHandler;
import com.anushaporter.backend.model.PassengerBooking;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class PassengerNotificationService {

    private final TelemetryWebSocketHandler telemetryWebSocketHandler;

    public enum EventType {
        BOOKING_CREATED,
        DRIVER_ASSIGNED,
        DRIVER_ACCEPTED,
        DRIVER_ARRIVING,
        DRIVER_ARRIVED,
        TRIP_STARTED,
        TRIP_COMPLETED,
        BOOKING_CANCELLED,
        PAYMENT_SUCCESSFUL,
        PAYMENT_FAILED,
        REFUND_INITIATED
    }

    public void sendEvent(EventType eventType, PassengerBooking booking, String recipientPhone, String recipientEmail, String customMessage) {
        log.info("[PassengerNotification] Event: {}, Booking: {}, Phone: {}, Email: {}, Note: {}",
                eventType,
                booking != null ? booking.getBookingNumber() : "N/A",
                recipientPhone,
                recipientEmail,
                customMessage);

        if (booking != null && booking.getBookingNumber() != null && telemetryWebSocketHandler != null) {
            String status = mapEventToStatus(eventType, booking);
            if (status != null) {
                try {
                    telemetryWebSocketHandler.broadcastPassengerStatus(booking.getBookingNumber(), status);
                } catch (Exception e) {
                    log.warn("Failed to broadcast passenger WebSocket status: {}", e.getMessage());
                }
            }
        }
    }

    private String mapEventToStatus(EventType eventType, PassengerBooking booking) {
        return switch (eventType) {
            case BOOKING_CREATED -> "DRIVER_SEARCHING";
            case DRIVER_ASSIGNED -> "DRIVER_ASSIGNED";
            case DRIVER_ACCEPTED, DRIVER_ARRIVING -> "DRIVER_ON_THE_WAY";
            case DRIVER_ARRIVED -> "ARRIVED_AT_PICKUP";
            case TRIP_STARTED -> "IN_TRIP";
            case TRIP_COMPLETED -> "COMPLETED";
            case BOOKING_CANCELLED -> "CANCELLED";
            default -> booking.getStatus() != null ? booking.getStatus().name() : null;
        };
    }
}

