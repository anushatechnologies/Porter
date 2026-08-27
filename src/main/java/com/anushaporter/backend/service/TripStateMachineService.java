package com.anushaporter.backend.service;

import com.anushaporter.backend.dto.TripStatusUpdateRequest;
import com.anushaporter.backend.model.BookingStatus;
import com.anushaporter.backend.model.Order;
import com.anushaporter.backend.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class TripStateMachineService {

    private static final Logger log = LoggerFactory.getLogger(TripStateMachineService.class);

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private AutoAssignmentService autoAssignmentService;

    @Autowired(required = false)
    private PushNotificationService pushNotificationService;

    // Allowed State Transitions Map
    private static final Map<BookingStatus, Set<BookingStatus>> ALLOWED_TRANSITIONS = new EnumMap<>(BookingStatus.class);

    static {
        ALLOWED_TRANSITIONS.put(BookingStatus.PENDING, Set.of(BookingStatus.SEARCHING, BookingStatus.ASSIGNED, BookingStatus.CANCELLED));
        ALLOWED_TRANSITIONS.put(BookingStatus.SEARCHING, Set.of(BookingStatus.ASSIGNED, BookingStatus.AUTO_ASSIGN_FAILED, BookingStatus.CANCELLED));
        ALLOWED_TRANSITIONS.put(BookingStatus.ASSIGNED, Set.of(BookingStatus.DRIVER_EN_ROUTE, BookingStatus.DRIVER_ARRIVED, BookingStatus.DRIVER_CANCELLED, BookingStatus.CANCELLED));
        ALLOWED_TRANSITIONS.put(BookingStatus.DRIVER_EN_ROUTE, Set.of(BookingStatus.DRIVER_ARRIVED, BookingStatus.PICKED_UP, BookingStatus.DRIVER_CANCELLED, BookingStatus.CANCELLED));
        ALLOWED_TRANSITIONS.put(BookingStatus.DRIVER_ARRIVED, Set.of(BookingStatus.PICKED_UP, BookingStatus.IN_TRANSIT, BookingStatus.DRIVER_CANCELLED, BookingStatus.CANCELLED));
        ALLOWED_TRANSITIONS.put(BookingStatus.PICKED_UP, Set.of(BookingStatus.IN_TRANSIT, BookingStatus.DELIVERED, BookingStatus.CANCELLED));
        ALLOWED_TRANSITIONS.put(BookingStatus.IN_TRANSIT, Set.of(BookingStatus.DELIVERED, BookingStatus.COMPLETED));
        ALLOWED_TRANSITIONS.put(BookingStatus.DELIVERED, Set.of(BookingStatus.COMPLETED));
        ALLOWED_TRANSITIONS.put(BookingStatus.AUTO_ASSIGN_FAILED, Set.of(BookingStatus.SEARCHING, BookingStatus.ASSIGNED, BookingStatus.CANCELLED));
        ALLOWED_TRANSITIONS.put(BookingStatus.DRIVER_CANCELLED, Set.of(BookingStatus.SEARCHING, BookingStatus.ASSIGNED, BookingStatus.CANCELLED));
        ALLOWED_TRANSITIONS.put(BookingStatus.COMPLETED, Set.of());
        ALLOWED_TRANSITIONS.put(BookingStatus.CANCELLED, Set.of());
    }

    public Map<String, Object> updateTripStatus(String bookingId, TripStatusUpdateRequest request, String driverId) {
        Map<String, Object> response = new LinkedHashMap<>();

        Optional<Order> orderOpt = orderRepository.findByBookingId(bookingId);
        if (orderOpt.isEmpty()) {
            response.put("success", false);
            response.put("statusCode", 404);
            response.put("message", "Booking not found: " + bookingId);
            return response;
        }

        Order order = orderOpt.get();

        BookingStatus currentStatus = BookingStatus.fromString(order.getStatus());
        BookingStatus targetStatus = request.getTargetStatus();
        if (targetStatus == null && request.getRawStatus() != null) {
            targetStatus = BookingStatus.fromString(request.getRawStatus());
        }

        if (targetStatus == null) {
            response.put("success", false);
            response.put("statusCode", 400);
            response.put("message", "Target status is required.");
            return response;
        }

        // Idempotency: if already in target status, return success
        if (currentStatus == targetStatus) {
            response.put("success", true);
            response.put("statusCode", 200);
            response.put("status", targetStatus.name());
            response.put("message", "Order is already in status: " + targetStatus.name());
            response.put("order", order);
            return response;
        }

        // Validate transition
        Set<BookingStatus> allowed = ALLOWED_TRANSITIONS.getOrDefault(currentStatus, Set.of());
        if (!allowed.contains(targetStatus)) {
            response.put("success", false);
            response.put("statusCode", 400);
            response.put("error", "INVALID_TRANSITION");
            response.put("message", String.format("Illegal state transition from '%s' to '%s'.", currentStatus.name(), targetStatus.name()));
            return response;
        }

        // Handle DRIVER_CANCELLED (Triggers auto-reassignment)
        if (targetStatus == BookingStatus.DRIVER_CANCELLED) {
            String reason = request.getCancellationReason() != null ? request.getCancellationReason() : "Driver cancelled trip.";
            order.setCancellationReason(reason);
            order.setStatus(BookingStatus.DRIVER_CANCELLED.name());
            order.setDriverId(null);
            order.setDriverName(null);
            order.setDriverPhone(null);
            order.setDriverVehicleNumber(null);
            orderRepository.save(order);

            log.info("Driver {} cancelled Booking '{}'. Initiating automatic re-assignment...", driverId, bookingId);

            // Re-trigger auto-assignment in background
            autoAssignmentService.startAutoAssignment(bookingId);

            response.put("success", true);
            response.put("statusCode", 200);
            response.put("status", BookingStatus.SEARCHING.name());
            response.put("message", "Driver cancelled. Auto-reassignment initiated.");
            return response;
        }

        // Apply transition
        order.setStatus(targetStatus.name());

        if (targetStatus == BookingStatus.COMPLETED || targetStatus == BookingStatus.DELIVERED) {
            order.setCompletedAt(LocalDateTime.now());
            if (driverId != null) {
                order.setCompletedByDriverId(driverId);
            }
        }

        Order saved = orderRepository.save(order);

        if (pushNotificationService != null) {
            pushNotificationService.notifyOrderStatus(saved, targetStatus.name());
        }

        response.put("success", true);
        response.put("statusCode", 200);
        response.put("status", targetStatus.name());
        response.put("bookingId", bookingId);
        response.put("message", "Trip status successfully updated to " + targetStatus.name());
        response.put("order", saved);
        return response;
    }
}
