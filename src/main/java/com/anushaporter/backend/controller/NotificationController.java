package com.anushaporter.backend.controller;

import com.anushaporter.backend.model.AppUser;
import com.anushaporter.backend.model.Notification;
import com.anushaporter.backend.repository.AppUserRepository;
import com.anushaporter.backend.repository.NotificationRepository;
import com.anushaporter.backend.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class NotificationController {
    @Autowired private NotificationRepository repository;
    @Autowired private AppUserRepository userRepository;
    @Autowired private JwtUtil jwtUtil;

    @PostMapping({"/users/fcm-token", "/user/fcm-token", "/fcm-token"})
    public ResponseEntity<?> registerToken(HttpServletRequest request, @RequestBody Map<String, String> body) {
        Optional<AppUser> user = currentUser(request);
        String token = body.get("fcmToken");
        if (user.isEmpty()) return unauthorized();
        if (token == null || token.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "fcmToken is required"));
        }
        AppUser saved = user.get();
        saved.setFcmToken(token.trim());
        userRepository.save(saved);
        return ResponseEntity.ok(Map.of("success", true, "message", "FCM token registered"));
    }

    /**
     * Endpoint 2: Driver Notifications List
     * GET /api/drivers/me/notifications
     */
    @GetMapping({"/drivers/me/notifications", "/drivers/notifications"})
    public ResponseEntity<Map<String, Object>> getDriverNotifications() {
        List<Map<String, Object>> notifs = new ArrayList<>();

        Map<String, Object> n1 = new LinkedHashMap<>();
        n1.put("id", "notif_01");
        n1.put("title", "Payout Processed");
        n1.put("message", "₹1,250 has been transferred to your bank account.");
        n1.put("createdAt", java.time.LocalDateTime.now().minusHours(2).toString());
        n1.put("read", false);
        notifs.add(n1);

        Map<String, Object> n2 = new LinkedHashMap<>();
        n2.put("id", "notif_02");
        n2.put("title", "New Trip Bonus Available");
        n2.put("message", "Complete 5 trips today to get an extra ₹200 bonus!");
        n2.put("createdAt", java.time.LocalDateTime.now().minusHours(5).toString());
        n2.put("read", true);
        notifs.add(n2);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "notifications", notifs
        ));
    }

    @GetMapping("/notifications")
    public ResponseEntity<?> getNotifications(HttpServletRequest request) {
        Optional<AppUser> user = currentUser(request);

        List<Notification> notifications;
        if (user.isPresent()) {
            notifications = repository.findByUserIdOrderByCreatedAtDesc(user.get().getId());
            if (notifications.isEmpty()) {
                notifications = repository.findAll();
            }
        } else {
            notifications = repository.findAll();
        }

        List<Map<String, Object>> items = notifications.stream().map(n -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", "NTF-" + (100 + n.getId()));
            map.put("notificationId", n.getId());
            map.put("title", n.getTitle() != null ? n.getTitle() : "Notification");
            map.put("message", n.getMessage() != null ? n.getMessage() : "");
            map.put("audience", n.getAudience() != null ? n.getAudience() : "all");
            map.put("date", n.getCreatedAt() != null ? n.getCreatedAt().toString() : java.time.LocalDateTime.now().toString());
            map.put("read", Boolean.TRUE.equals(n.getReadStatus()));
            map.put("isRead", Boolean.TRUE.equals(n.getReadStatus()));
            map.put("bookingId", n.getBookingId());
            return map;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(items);
    }

    @RequestMapping(value = "/notifications/{id}/read", method = {RequestMethod.PUT, RequestMethod.POST})
    public ResponseEntity<?> markRead(@PathVariable Long id, HttpServletRequest request) {
        Optional<AppUser> user = currentUser(request);
        if (user.isEmpty()) return unauthorized();
        return repository.findByIdAndUserId(id, user.get().getId()).map(notification -> {
            notification.setReadStatus(true);
            repository.save(notification);
            return ResponseEntity.ok(Map.of("success", true, "message", "Notification marked as read"));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/notifications/read-all")
    public ResponseEntity<?> markAllRead(HttpServletRequest request) {
        Optional<AppUser> user = currentUser(request);
        if (user.isEmpty()) return unauthorized();
        List<Notification> notifications = repository.findByUserIdOrderByCreatedAtDesc(user.get().getId());
        notifications.forEach(notification -> notification.setReadStatus(true));
        repository.saveAll(notifications);
        return ResponseEntity.ok(Map.of("success", true, "message", "All notifications marked as read"));
    }

    @PostMapping("/notifications/broadcast")
    public ResponseEntity<?> broadcast(@RequestBody Map<String, String> body) {
        String title = body.get("title");
        String message = body.get("message");
        if (title == null || title.isBlank() || message == null || message.isBlank())
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "title and message are required"));

        String audience = body.getOrDefault("audience", "all");
        List<AppUser> recipients = "all".equalsIgnoreCase(audience)
                ? userRepository.findAll() : userRepository.findByRoleIgnoreCase(audience);
        List<Notification> notifications = recipients.stream().map(user -> {
            Notification notification = new Notification();
            notification.setUserId(user.getId()); notification.setTitle(title); notification.setMessage(message);
            notification.setAudience(audience); notification.setTarget(body.get("target"));
            notification.setNotificationType("BROADCAST");
            return notification;
        }).collect(Collectors.toList());
        repository.saveAll(notifications);
        return ResponseEntity.ok(Map.of("success", true, "recipientCount", notifications.size()));
    }

    private Optional<AppUser> currentUser(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) return Optional.empty();
        try {
            String token = header.substring(7);
            if (!jwtUtil.validateToken(token)) return Optional.empty();
            return userRepository.findFirstByEmailOrderByIdDesc(jwtUtil.getUsernameFromToken(token));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private ResponseEntity<Map<String, Object>> unauthorized() {
        return ResponseEntity.status(401).body(Map.of("success", false, "message", "Unauthorized"));
    }

    private Map<String, Object> toResponse(Notification n) {
        Map<String, Object> result = new HashMap<>();
        result.put("id", n.getId());
        result.put("bookingId", n.getBookingId());
        result.put("title", n.getTitle());
        result.put("message", n.getMessage());
        result.put("notificationType", n.getNotificationType());
        result.put("isRead", Boolean.TRUE.equals(n.getReadStatus()));
        result.put("createdAt", n.getCreatedAt());
        return result;
    }
}
