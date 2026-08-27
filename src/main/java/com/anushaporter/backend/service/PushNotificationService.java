package com.anushaporter.backend.service;

import com.anushaporter.backend.model.AppUser;
import com.anushaporter.backend.model.Notification;
import com.anushaporter.backend.model.Order;
import com.anushaporter.backend.repository.AppUserRepository;
import com.anushaporter.backend.repository.NotificationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;

@Service
public class PushNotificationService {
    private static final Logger logger = LoggerFactory.getLogger(PushNotificationService.class);
    private static final URI EXPO_URI = URI.create("https://exp.host/--/api/v2/push/send");

    @Autowired private AppUserRepository userRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private ObjectMapper objectMapper;

    public void notifyUser(AppUser user, String bookingId, String type, String title, String message) {
        if (user == null) return;
        Notification notification = new Notification();
        notification.setUserId(user.getId());
        notification.setBookingId(bookingId);
        notification.setNotificationType(type);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setAudience(user.getRole());
        notification.setTarget(user.getEmail());
        notificationRepository.save(notification);

        String token = user.getFcmToken();
        if (token == null || token.isBlank()) return;
        try {
            if (token.startsWith("ExpoPushToken[")) {
                sendExpo(token, title, message, bookingId, type);
            } else {
                Message push = Message.builder()
                        .setToken(token)
                        .setNotification(com.google.firebase.messaging.Notification.builder()
                                .setTitle(title).setBody(message).build())
                        .putData("bookingId", bookingId == null ? "" : bookingId)
                        .putData("notificationType", type)
                        .build();
                FirebaseMessaging.getInstance().send(push);
            }
        } catch (Exception e) {
            logger.warn("Push delivery failed for user {}: {}", user.getId(), e.getMessage());
        }
    }

    public void notifyOrderStatus(Order order, String status) {
        String value = status == null ? "" : status.toLowerCase().replace('-', '_');
        String title;
        String message;
        String type;
        if (value.equals("assigned") || value.equals("accepted") || value.equals("driver_assigned")) {
            title = "Driver Accepted! 🚚";
            message = "Driver " + safe(order.getDriverName(), "Your driver") + " ("
                    + safe(order.getDriverVehicleNumber(), "vehicle") + ") has accepted your order.";
            type = "DRIVER_ACCEPTED";
        } else if (value.equals("arriving_at_pickup") || value.equals("pickup_started")) {
            title = "Driver Arriving 📍";
            message = safe(order.getDriverName(), "Your driver") + " is arriving at your pickup location.";
            type = "ARRIVING_AT_PICKUP";
        } else if (value.equals("picked_up") || value.equals("transit") || value.equals("in_transit")) {
            title = "Goods Picked Up 📦";
            message = "Your package is now in transit to the drop location.";
            type = "IN_TRANSIT";
        } else if (value.equals("delivered") || value.equals("completed")) {
            title = "Order Delivered 🎉";
            message = "Your goods have been delivered successfully. Thank you for choosing Anusha Porter!";
            type = "DELIVERED";
        } else if (value.equals("cancelled") || value.equals("canceled")) {
            title = "Order Cancelled ❌";
            message = "Order #" + safe(order.getBookingId(), String.valueOf(order.getId())) + " has been cancelled.";
            type = "CANCELLED";
        } else return;

        userRepository.findFirstByEmailOrderByIdDesc(order.getUserEmail())
                .ifPresent(user -> notifyUser(user, order.getBookingId(), type, title, message));
        if (type.equals("CANCELLED") && order.getDriverEmail() != null) {
            userRepository.findFirstByEmailOrderByIdDesc(order.getDriverEmail())
                    .ifPresent(user -> notifyUser(user, order.getBookingId(), type, title, message));
        }
    }

    public void notifyDriverAssignment(String driverIdentifier, String bookingId, String pickup, String drop) {
        if (driverIdentifier == null || driverIdentifier.isBlank()) return;
        userRepository.findFirstByEmailOrderByIdDesc(driverIdentifier)
                .or(() -> userRepository.findFirstByPhoneOrderByIdDesc(driverIdentifier))
                .ifPresent(driverUser -> {
                    String title = "New Delivery Offer! 📦";
                    String message = "Pickup: " + safe(pickup, "Near you") + " → Drop: " + safe(drop, "Destination");
                    notifyUser(driverUser, bookingId, "DRIVER_OFFER", title, message);
                });
    }

    private void sendExpo(String token, String title, String message, String bookingId, String type) throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("to", token);
        payload.put("title", title);
        payload.put("body", message);
        payload.put("sound", "default");
        payload.put("priority", "high");
        payload.put("data", Map.of("bookingId", bookingId == null ? "" : bookingId, "notificationType", type));
        HttpRequest request = HttpRequest.newBuilder(EXPO_URI)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                .build();
        HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }

    private String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
