package com.anushaporter.backend.service;

import com.anushaporter.backend.model.PassengerBooking;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class PassengerNotificationService {

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
    }
}
