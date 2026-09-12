package com.anushaporter.backend.service;

import com.anushaporter.backend.model.AppUser;
import com.anushaporter.backend.model.Driver;
import com.anushaporter.backend.model.Notification;
import com.anushaporter.backend.model.Order;
import com.anushaporter.backend.repository.AppUserRepository;
import com.anushaporter.backend.repository.DriverRepository;
import com.anushaporter.backend.repository.NotificationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.firebase.FirebaseApp;
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
    @Autowired(required = false) private DriverRepository driverRepository;
    @Autowired private ObjectMapper objectMapper;

    private boolean isFirebaseReady() {
        try {
            return !FirebaseApp.getApps().isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    private void safeSendFirebase(Message push) {
        if (!isFirebaseReady()) {
            logger.debug("Firebase is not initialized; skipping FCM message send.");
            return;
        }
        try {
            FirebaseMessaging.getInstance().send(push);
        } catch (Exception e) {
            logger.warn("FCM push delivery failed: {}", e.getMessage());
        }
    }

    public void notifyUser(AppUser user, String bookingId, String type, String title, String message) {
        if (user == null) return;
        Notification notification = new Notification();
        notification.setUserId(user.getId());
        notification.setBookingId(bookingId);
        notification.setNotificationType(type);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setAudience(user.getRole() != null ? user.getRole() : "driver");
        notification.setTarget(user.getEmail() != null ? user.getEmail() : user.getPhone());
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
                safeSendFirebase(push);
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

    public void notifyDriverOffer(Driver driver, Order order) {
        if (driver == null || order == null) return;
        boolean isPassenger = "PASSENGER".equalsIgnoreCase(order.getServiceType());
        int count = order.getPassengerCount() != null ? order.getPassengerCount() : 1;
        String title;
        String message;
        if (isPassenger) {
            String s = (order.getServiceName() != null ? order.getServiceName() : "").toLowerCase();
            if (s.contains("bike") || s.contains("2wheel")) {
                title = "New Bike Taxi Ride Request! 🛵";
            } else if (s.contains("auto") || s.contains("3wheel")) {
                title = "New Auto Ride Request! 🛺";
            } else if (s.contains("cab") || s.contains("car")) {
                title = "New Cab Ride Request! 🚗";
            } else {
                title = "New Passenger Ride Request! 👤";
            }
            message = String.format("Passenger Ride (%d rider%s) | Pickup: %s → Drop: %s (₹%.0f)",
                    count, count > 1 ? "s" : "",
                    safe(order.getPickupAddress(), "Near you"),
                    safe(order.getDropAddress(), "Destination"),
                    order.getAmount() != null ? order.getAmount() : 0.0);
        } else {
            title = "New Goods Delivery Offer! 📦🚚";
            message = String.format("Goods: %s | Pickup: %s → Drop: %s (₹%.0f)",
                    safe(order.getGoodsCategory() != null ? order.getGoodsCategory() : order.getServiceName(), "Package"),
                    safe(order.getPickupAddress(), "Near you"),
                    safe(order.getDropAddress(), "Destination"),
                    order.getAmount() != null ? order.getAmount() : 0.0);
        }

        AppUser driverUser = resolveDriverUser(driver);
        if (driverUser != null) {
            notifyUser(driverUser, order.getBookingId(), "DRIVER_OFFER", title, message);
        } else {
            Notification notification = new Notification();
            notification.setUserId(driver.getId());
            notification.setBookingId(order.getBookingId());
            notification.setNotificationType("DRIVER_OFFER");
            notification.setTitle(title);
            notification.setMessage(message);
            notification.setAudience("driver");
            notification.setTarget(driver.getEmail() != null ? driver.getEmail() : driver.getPhone());
            notificationRepository.save(notification);
        }
    }

    public void notifyDriverOffer(Driver driver, String bookingId, String pickup, String drop, Double fare) {
        if (driver == null) return;
        AppUser driverUser = resolveDriverUser(driver);

        String title = "New Delivery Offer! 🚚";
        String message = String.format("Pickup: %s → Drop: %s (₹%.0f)",
                safe(pickup, "Near you"),
                safe(drop, "Destination"),
                fare != null ? fare : 0.0);

        if (driverUser != null) {
            notifyUser(driverUser, bookingId, "DRIVER_OFFER", title, message);
        } else {
            Notification notification = new Notification();
            notification.setUserId(driver.getId());
            notification.setBookingId(bookingId);
            notification.setNotificationType("DRIVER_OFFER");
            notification.setTitle(title);
            notification.setMessage(message);
            notification.setAudience("driver");
            notification.setTarget(driver.getEmail() != null ? driver.getEmail() : driver.getPhone());
            notificationRepository.save(notification);
        }
    }

    public void notifyOfferTaken(Driver driver, String bookingId) {
        if (driver == null) return;
        AppUser driverUser = resolveDriverUser(driver);
        if (driverUser != null && driverUser.getFcmToken() != null && !driverUser.getFcmToken().isBlank()) {
            String token = driverUser.getFcmToken();
            String title = "Order Accepted";
            String message = "Another driver partner has accepted this order.";
            try {
                if (token.startsWith("ExpoPushToken[")) {
                    sendExpoStopOffer(token, title, message, bookingId, "ACCEPTED_BY_ANOTHER");
                } else {
                    Message push = Message.builder()
                            .setToken(token)
                            .putData("bookingId", bookingId == null ? "" : bookingId)
                            .putData("notificationType", "STOP_DRIVER_OFFER")
                            .putData("status", "ACCEPTED_BY_ANOTHER")
                            .putData("stopSound", "true")
                            .putData("stopAudio", "true")
                            .putData("stop_ringtone", "true")
                            .putData("action", "STOP_RINGTONE")
                            .build();
                    safeSendFirebase(push);
                }
            } catch (Exception e) {
                logger.debug("Failed to send stop offer push to driver {}: {}", driver.getId(), e.getMessage());
            }
        }
    }

    public void notifyOfferAcceptedBySelf(Driver driver, String bookingId) {
        if (driver == null) return;
        AppUser driverUser = resolveDriverUser(driver);
        if (driverUser != null && driverUser.getFcmToken() != null && !driverUser.getFcmToken().isBlank()) {
            String token = driverUser.getFcmToken();
            String title = "Booking Confirmed! 🚚";
            String message = "You have accepted booking #" + bookingId;
            try {
                if (token.startsWith("ExpoPushToken[")) {
                    sendExpoStopOffer(token, title, message, bookingId, "ACCEPTED_BY_YOU");
                } else {
                    Message push = Message.builder()
                            .setToken(token)
                            .putData("bookingId", bookingId == null ? "" : bookingId)
                            .putData("notificationType", "STOP_DRIVER_OFFER")
                            .putData("status", "ACCEPTED_BY_YOU")
                            .putData("stopSound", "true")
                            .putData("stopAudio", "true")
                            .putData("stop_ringtone", "true")
                            .putData("action", "STOP_RINGTONE")
                            .build();
                    safeSendFirebase(push);
                }
            } catch (Exception e) {
                logger.debug("Failed to send accept stop push to winning driver {}: {}", driver.getId(), e.getMessage());
            }
        }
    }

    public void notifyOfferDismissedForDriver(Driver driver, String bookingId) {
        if (driver == null) return;
        AppUser driverUser = resolveDriverUser(driver);
        if (driverUser != null && driverUser.getFcmToken() != null && !driverUser.getFcmToken().isBlank()) {
            String token = driverUser.getFcmToken();
            try {
                if (token.startsWith("ExpoPushToken[")) {
                    sendExpoStopOffer(token, "Offer Dismissed", "Offer dismissed", bookingId, "REJECTED_BY_YOU");
                } else {
                    Message push = Message.builder()
                            .setToken(token)
                            .putData("bookingId", bookingId == null ? "" : bookingId)
                            .putData("notificationType", "STOP_DRIVER_OFFER")
                            .putData("status", "REJECTED_BY_YOU")
                            .putData("stopSound", "true")
                            .putData("stopAudio", "true")
                            .putData("stop_ringtone", "true")
                            .putData("action", "STOP_RINGTONE")
                            .build();
                    safeSendFirebase(push);
                }
            } catch (Exception e) {
                logger.debug("Failed to send dismiss push to driver {}: {}", driver.getId(), e.getMessage());
            }
        }
    }

    private AppUser resolveDriverUser(Driver driver) {
        if (driver == null) return null;
        AppUser driverUser = null;
        if (driver.getEmail() != null && !driver.getEmail().isBlank()) {
            driverUser = userRepository.findFirstByEmailOrderByIdDesc(driver.getEmail()).orElse(null);
        }
        if (driverUser == null && driver.getPhone() != null && !driver.getPhone().isBlank()) {
            driverUser = userRepository.findFirstByPhoneOrderByIdDesc(driver.getPhone()).orElse(null);
            if (driverUser == null) {
                String cleanPhone = driver.getPhone().replaceAll("\\D+", "");
                if (cleanPhone.length() > 10) cleanPhone = cleanPhone.substring(cleanPhone.length() - 10);
                if (!cleanPhone.isEmpty()) {
                    driverUser = userRepository.findFirstByPhoneOrderByIdDesc(cleanPhone).orElse(null);
                }
            }
        }
        if (driverUser == null && driver.getId() != null) {
            driverUser = userRepository.findById(driver.getId()).orElse(null);
        }
        return driverUser;
    }

    public void notifyDriverAssignment(String driverIdentifier, String bookingId, String pickup, String drop) {
        if (driverIdentifier == null || driverIdentifier.isBlank()) return;
        AppUser driverUser = userRepository.findFirstByEmailOrderByIdDesc(driverIdentifier)
                .or(() -> userRepository.findFirstByPhoneOrderByIdDesc(driverIdentifier))
                .orElse(null);

        if (driverUser == null && driverRepository != null) {
            try {
                Long dId = Long.parseLong(driverIdentifier.replaceAll("[^0-9]", ""));
                Driver d = driverRepository.findById(dId).orElse(null);
                if (d != null) {
                    driverUser = resolveDriverUser(d);
                }
            } catch (Exception ignored) {}
        }

        String title = "New Delivery Offer! 📦";
        String message = "Pickup: " + safe(pickup, "Near you") + " → Drop: " + safe(drop, "Destination");

        if (driverUser != null) {
            notifyUser(driverUser, bookingId, "DRIVER_OFFER", title, message);
        }
    }

    private void sendExpo(String token, String title, String message, String bookingId, String type) throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("to", token);
        payload.put("title", title);
        payload.put("body", message);
        payload.put("sound", "default");
        payload.put("priority", "high");
        payload.put("data", Map.of(
                "bookingId", bookingId == null ? "" : bookingId,
                "notificationType", type
        ));
        HttpRequest request = HttpRequest.newBuilder(EXPO_URI)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                .build();
        HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }

    /**
     * Sends a silent Expo push notification without sound playback to instruct
     * the mobile app to stop the offer audio/ringtone.
     */
    private void sendExpoStopOffer(String token, String title, String message, String bookingId, String status) throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("to", token);
        payload.put("title", title);
        payload.put("body", message);
        payload.put("sound", null); // Explicitly null so device DOES NOT play alert audio
        payload.put("priority", "high");

        Map<String, Object> data = new HashMap<>();
        data.put("bookingId", bookingId == null ? "" : bookingId);
        data.put("notificationType", "STOP_DRIVER_OFFER");
        data.put("status", status);
        data.put("stopSound", "true");
        data.put("stopAudio", "true");
        data.put("stop_ringtone", "true");
        data.put("action", "STOP_RINGTONE");
        payload.put("data", data);

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
