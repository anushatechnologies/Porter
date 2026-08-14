package com.anushaporter.backend.controller;

import com.anushaporter.backend.model.AppUser;
import com.anushaporter.backend.repository.AppUserRepository;
import com.anushaporter.backend.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/users/preferences")
public class UserPreferencesController {

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private JwtUtil jwtUtil;

    @GetMapping
    public ResponseEntity<Map<String, Object>> getPreferences(HttpServletRequest request) {
        AppUser user = resolveUser(request);
        Map<String, Object> prefs = new LinkedHashMap<>();
        prefs.put("language", user != null && user.getLanguage() != null ? user.getLanguage() : "en");
        prefs.put("theme", "system");
        prefs.put("notificationsEnabled", true);

        return ResponseEntity.ok(Map.of("success", true, "preferences", prefs));
    }

    @PutMapping
    public ResponseEntity<Map<String, Object>> updatePreferences(
            HttpServletRequest request,
            @RequestBody Map<String, Object> body) {

        AppUser user = resolveUser(request);
        String lang = (String) body.getOrDefault("language", body.get("lang"));
        if (lang == null || lang.isBlank()) {
            lang = "en";
        }

        if (user != null) {
            user.setLanguage(lang.trim().toLowerCase());
            userRepository.save(user);
        }

        Map<String, Object> prefs = new LinkedHashMap<>();
        prefs.put("language", lang.trim().toLowerCase());
        prefs.put("theme", body.getOrDefault("theme", "system"));
        prefs.put("notificationsEnabled", body.getOrDefault("notificationsEnabled", true));

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Preferences updated successfully",
                "preferences", prefs
        ));
    }

    private AppUser resolveUser(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            try {
                String token = authHeader.substring(7);
                String identifier = jwtUtil.getUsernameFromToken(token);
                if (identifier != null) {
                    Optional<AppUser> userOpt = userRepository.findFirstByEmailOrderByIdDesc(identifier);
                    if (userOpt.isPresent()) return userOpt.get();
                    userOpt = userRepository.findFirstByPhoneOrderByIdDesc(identifier);
                    if (userOpt.isPresent()) return userOpt.get();
                }
            } catch (Exception ignored) {}
        }
        return null;
    }
}
