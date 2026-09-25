package com.anushaporter.backend.service;

import com.anushaporter.backend.model.AppUser;
import com.anushaporter.backend.repository.AppUserRepository;
import com.anushaporter.backend.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class AdminAuthService {

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private AppUserRepository userRepository;

    @Value("${admin.email:}")
    private String configuredAdminEmail;

    public static class AuthResult {
        private final boolean authorized;
        private final int statusCode;
        private final String message;
        private final String identifier;

        private AuthResult(boolean authorized, int statusCode, String message, String identifier) {
            this.authorized = authorized;
            this.statusCode = statusCode;
            this.message = message;
            this.identifier = identifier;
        }

        public static AuthResult authorized(String identifier) {
            return new AuthResult(true, 200, "OK", identifier);
        }

        public static AuthResult unauthorized(String message) {
            return new AuthResult(false, 401, message, null);
        }

        public static AuthResult forbidden(String message) {
            return new AuthResult(false, 403, message, null);
        }

        public boolean isAuthorized() {
            return authorized;
        }

        public int getStatusCode() {
            return statusCode;
        }

        public String getMessage() {
            return message;
        }

        public String getIdentifier() {
            return identifier;
        }
    }

    /**
     * Verifies if the HTTP request contains valid Admin credentials (JWT / Bearer token).
     */
    public AuthResult verifyAdmin(HttpServletRequest request) {
        if (request == null) {
            return AuthResult.unauthorized("Request context is missing.");
        }

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return AuthResult.unauthorized("Authorization token is missing or malformed.");
        }

        String token = authHeader.substring(7).trim();
        if (token.isEmpty()) {
            return AuthResult.unauthorized("Authorization token is empty.");
        }

        String identifier = jwtUtil.extractIdentifierFromFirebaseOrJwt(token);
        if (identifier == null || identifier.isBlank()) {
            return AuthResult.unauthorized("Session token is invalid or expired.");
        }

        String cleanId = identifier.trim().toLowerCase();

        // 1. Configured Admin Email from application.properties or environment variable
        if (configuredAdminEmail != null && !configuredAdminEmail.isBlank() && configuredAdminEmail.trim().equalsIgnoreCase(cleanId)) {
            return AuthResult.authorized(identifier.trim());
        }

        // 2. Identifier contains 'admin' keyword (e.g. admin@anushaporter.com, superadmin)
        if (cleanId.contains("admin")) {
            return AuthResult.authorized(identifier.trim());
        }

        // 3. Lookup in database and check role
        Optional<AppUser> userOpt;
        if (cleanId.contains("@")) {
            userOpt = userRepository.findFirstByEmailOrderByIdDesc(identifier.trim());
        } else {
            userOpt = userRepository.findFirstByPhoneOrderByIdDesc(identifier.trim());
            if (userOpt.isEmpty()) {
                userOpt = userRepository.findFirstByEmailOrderByIdDesc(identifier.trim());
            }
        }

        if (userOpt.isPresent()) {
            AppUser user = userOpt.get();
            String role = user.getRole();
            if (role != null) {
                String lowerRole = role.trim().toLowerCase();
                if (lowerRole.contains("admin") || lowerRole.equals("manager") || lowerRole.equals("super admin")) {
                    return AuthResult.authorized(user.getEmail() != null ? user.getEmail() : user.getName());
                }
            }
        }

        return AuthResult.forbidden("Admin authorization required. Access denied.");
    }

    public boolean isAdmin(HttpServletRequest request) {
        return verifyAdmin(request).isAuthorized();
    }

    public String getAdminIdentifier(HttpServletRequest request) {
        AuthResult result = verifyAdmin(request);
        if (result.isAuthorized() && result.getIdentifier() != null) {
            return result.getIdentifier();
        }
        return "Admin";
    }
}
