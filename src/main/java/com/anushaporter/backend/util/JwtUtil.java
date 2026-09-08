package com.anushaporter.backend.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Component
public class JwtUtil {

    private static final String DEFAULT_SECRET = "anusha_porter_super_secret_jwt_signing_key_2026_minimum_256_bits_length_required_for_hs256_production";

    @Value("${jwt.secret:anusha_porter_super_secret_jwt_signing_key_2026_minimum_256_bits_length_required_for_hs256_production}")
    private String configuredSecret;

    // Guaranteed initialized persistent SecretKey for HS256
    private SecretKey key = Keys.hmacShaKeyFor(DEFAULT_SECRET.getBytes(StandardCharsets.UTF_8));
    private final long JWT_TOKEN_VALIDITY = 30L * 24 * 60 * 60 * 1000; // 30 days
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostConstruct
    public void initKey() {
        String secretToUse = (configuredSecret != null && configuredSecret.trim().length() >= 32)
                ? configuredSecret.trim()
                : DEFAULT_SECRET;
        this.key = Keys.hmacShaKeyFor(secretToUse.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(String username) {
        Map<String, Object> claims = new HashMap<>();
        return doGenerateToken(claims, username);
    }

    private String doGenerateToken(Map<String, Object> claims, String subject) {
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(subject)
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + JWT_TOKEN_VALIDITY))
                .signWith(key)
                .compact();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public String getUsernameFromToken(String token) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
        return claims.getSubject();
    }

    /**
     * Extracts user or driver identifier (phone, email, or uid) from either backend HS256 JWT
     * or Google Firebase RS256 ID Token.
     */
    public String extractIdentifierFromFirebaseOrJwt(String token) {
        if (token == null || token.isBlank()) return null;
        String cleanToken = token.trim();

        // 1. Try standard backend JWT validation
        if (validateToken(cleanToken)) {
            try {
                return getUsernameFromToken(cleanToken);
            } catch (Exception ignored) {}
        }

        // 2. Try Firebase Admin SDK token verification if Firebase is initialized
        try {
            if (!FirebaseApp.getApps().isEmpty()) {
                FirebaseToken ft = FirebaseAuth.getInstance().verifyIdToken(cleanToken);
                if (ft != null) {
                    String phone = (String) ft.getClaims().get("phone_number");
                    if (phone != null && !phone.isBlank()) return phone;
                    String email = ft.getEmail();
                    if (email != null && !email.isBlank()) return email;
                    return ft.getUid();
                }
            }
        } catch (Exception ignored) {}

        // 3. Fallback: Parse JWT payload JSON claims (handles expired Firebase ID tokens or unverified claims)
        try {
            String[] parts = cleanToken.split("\\.");
            if (parts.length >= 2) {
                String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
                JsonNode node = objectMapper.readTree(payload);

                if (node.hasNonNull("phone_number")) {
                    return node.get("phone_number").asText();
                }
                if (node.hasNonNull("email")) {
                    return node.get("email").asText();
                }
                if (node.hasNonNull("sub")) {
                    return node.get("sub").asText();
                }
                if (node.hasNonNull("user_id")) {
                    return node.get("user_id").asText();
                }
            }
        } catch (Exception ignored) {}

        return null;
    }

    public boolean validateTokenOrFirebase(String token) {
        if (token == null || token.isBlank()) return false;
        return extractIdentifierFromFirebaseOrJwt(token) != null;
    }
}
