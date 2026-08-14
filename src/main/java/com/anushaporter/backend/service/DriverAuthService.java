package com.anushaporter.backend.service;

import com.anushaporter.backend.model.AppUser;
import com.anushaporter.backend.model.Driver;
import com.anushaporter.backend.repository.AppUserRepository;
import com.anushaporter.backend.repository.DriverRepository;
import com.anushaporter.backend.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

@Service
public class DriverAuthService {

    @Autowired
    private DriverRepository driverRepository;

    @Autowired(required = false)
    private AppUserRepository appUserRepository;

    @Autowired
    private JwtUtil jwtUtil;

    /**
     * Resolves authenticated driver from request attribute or Authorization Bearer header.
     */
    public Driver resolveAuthenticatedDriver(HttpServletRequest request) {
        if (request == null) return null;

        String subject = (String) request.getAttribute("userId");
        if (subject == null || subject.trim().isEmpty()) {
            String authHeader = request.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7).trim();
                if (jwtUtil.validateToken(token)) {
                    subject = jwtUtil.getUsernameFromToken(token);
                }
            }
        }

        if (subject == null || subject.trim().isEmpty()) {
            return null;
        }

        return resolveDriverByIdentifier(subject.trim());
    }

    /**
     * Resolves driver from email, phone, ID, or user account identifier.
     */
    public Driver resolveDriverByIdentifier(String rawIdentifier) {
        if (rawIdentifier == null || rawIdentifier.trim().isEmpty()) {
            return null;
        }

        String identifier;
        try {
            identifier = URLDecoder.decode(rawIdentifier.trim(), StandardCharsets.UTF_8).trim();
        } catch (Exception e) {
            identifier = rawIdentifier.trim();
        }

        // 1. Direct Email lookup
        if (identifier.contains("@")) {
            Optional<Driver> driverOpt = driverRepository.findByEmailIgnoreCase(identifier);
            if (driverOpt.isPresent()) return driverOpt.get();

            driverOpt = driverRepository.findByEmail(identifier);
            if (driverOpt.isPresent()) return driverOpt.get();
        }

        // 2. Direct Phone lookup (both raw and 10-digit clean phone)
        String cleanPhone = normalizePhone(identifier);
        if (!cleanPhone.isEmpty()) {
            Optional<Driver> driverOpt = driverRepository.findByPhone(identifier);
            if (driverOpt.isPresent()) return driverOpt.get();

            driverOpt = driverRepository.findByPhone(cleanPhone);
            if (driverOpt.isPresent()) return driverOpt.get();

            driverOpt = driverRepository.findFirstByPhoneOrderByIdDesc(cleanPhone);
            if (driverOpt.isPresent()) return driverOpt.get();
        }

        // 3. Direct Numeric / Formatted ID lookup (e.g. "1001", "DRV-1001")
        String idStr = identifier;
        if (idStr.toUpperCase().startsWith("DRV-")) {
            idStr = idStr.substring(4).trim();
        } else if (idStr.toUpperCase().startsWith("DRV_")) {
            idStr = idStr.substring(4).trim();
        }
        if (idStr.matches("^\\d+$")) {
            try {
                Long id = Long.parseLong(idStr);
                Optional<Driver> driverOpt = driverRepository.findById(id);
                if (driverOpt.isPresent()) return driverOpt.get();
            } catch (Exception ignored) {}
        }

        // 4. Resolve via AppUser
        if (appUserRepository != null) {
            Optional<AppUser> userOpt = Optional.empty();
            if (identifier.contains("@")) {
                userOpt = appUserRepository.findFirstByEmailOrderByIdDesc(identifier);
            } else if (!cleanPhone.isEmpty()) {
                userOpt = appUserRepository.findFirstByPhoneOrderByIdDesc(cleanPhone);
                if (userOpt.isEmpty()) {
                    userOpt = appUserRepository.findFirstByPhoneOrderByIdDesc(identifier);
                }
            }

            if (userOpt.isPresent()) {
                AppUser user = userOpt.get();
                if (user.getEmail() != null && !user.getEmail().isBlank()) {
                    Optional<Driver> d = driverRepository.findByEmailIgnoreCase(user.getEmail());
                    if (d.isPresent()) return d.get();
                }
                if (user.getPhone() != null && !user.getPhone().isBlank()) {
                    String userCleanPhone = normalizePhone(user.getPhone());
                    Optional<Driver> d = driverRepository.findByPhone(user.getPhone());
                    if (d.isPresent()) return d.get();

                    d = driverRepository.findByPhone(userCleanPhone);
                    if (d.isPresent()) return d.get();
                }
            }
        }

        // 5. Fallback: match by name or any field across all drivers
        for (Driver d : driverRepository.findAll()) {
            if (d.getEmail() != null && d.getEmail().equalsIgnoreCase(identifier)) return d;
            if (d.getPhone() != null && (d.getPhone().equalsIgnoreCase(identifier) || normalizePhone(d.getPhone()).equals(cleanPhone))) return d;
            if (d.getName() != null && d.getName().equalsIgnoreCase(identifier)) return d;
        }

        return null;
    }

    /**
     * Normalizes phone string to 10 digits.
     */
    public String normalizePhone(String phone) {
        if (phone == null) return "";
        String digits = phone.replaceAll("\\D+", "");
        if (digits.length() > 10) {
            digits = digits.substring(digits.length() - 10);
        }
        return digits;
    }

    /**
     * Normalizes status to allowed lowercase values: "online", "offline", "suspended".
     */
    public String normalizeStatus(Object statusValue) {
        if (statusValue == null) return "offline";

        if (statusValue instanceof Boolean) {
            return (Boolean) statusValue ? "online" : "offline";
        }

        String str = String.valueOf(statusValue).trim().toLowerCase();
        if (str.equals("true") || str.equals("1") || str.equals("on") || str.equals("online") || str.equals("available") || str.equals("active")) {
            return "online";
        }
        if (str.equals("suspended") || str.equals("block") || str.equals("blocked")) {
            return "suspended";
        }
        return "offline";
    }
}
