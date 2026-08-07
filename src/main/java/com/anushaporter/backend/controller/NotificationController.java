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

import java.util.HashMap;
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

    @PostMapping("/users/fcm-token")
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

    @GetMapping("/notifications")
    public ResponseEntity<?> getMine(HttpServletRequest request) {
        Optional<AppUser> user = currentUser(request);
        if (user.isEmpty()) return unauthorized();
        List<Map<String, Object>> data = repository.findByUserIdOrderByCreatedAtDesc(user.get().getId())
                .stream().map(this::toResponse).collect(Collectors.toList());
        return ResponseEntity.ok(Map.of("success", true, "data", data));
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
