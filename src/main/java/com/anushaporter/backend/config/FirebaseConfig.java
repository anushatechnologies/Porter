package com.anushaporter.backend.config;

import com.google.api.client.http.apache.v2.ApacheHttpTransport;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Configuration
public class FirebaseConfig {

    private static final Logger logger = LoggerFactory.getLogger(FirebaseConfig.class);

    @Value("${firebase.service-account-base64:}")
    private String propBase64;

    @Value("${firebase.service-account-path:}")
    private String propPath;

    @PostConstruct
    public void initialize() {
        try (InputStream serviceAccount = getServiceAccountStream()) {
            if (serviceAccount == null) {
                logger.info("Firebase service account credentials not configured. Push notifications will use Expo and WebSocket fallback.");
                return;
            }

            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                    .setHttpTransport(new ApacheHttpTransport())
                    .build();

            if (FirebaseApp.getApps().isEmpty()) {
                FirebaseApp.initializeApp(options);
                logger.info("Firebase Admin SDK initialized successfully with Apache HTTP Transport.");
            }
        } catch (Exception e) {
            logger.warn("Failed to initialize Firebase Admin SDK (will fallback to Expo/WebSocket): {}", e.getMessage());
        }
    }

    @Bean
    public FirebaseMessaging firebaseMessaging() {
        try {
            if (!FirebaseApp.getApps().isEmpty()) {
                return FirebaseMessaging.getInstance();
            }
        } catch (Exception ignored) {}
        return null;
    }

    private InputStream getServiceAccountStream() throws Exception {
        // 1. Check Spring property or Environment variable for Base64 encoded JSON
        String encodedCredentials = (propBase64 != null && !propBase64.isBlank())
                ? propBase64
                : System.getenv("FIREBASE_SERVICE_ACCOUNT_BASE64");

        if (encodedCredentials != null && !encodedCredentials.trim().isEmpty()) {
            String value = encodedCredentials.trim();
            if (value.startsWith("{")) {
                return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
            }
            try {
                byte[] credentials = Base64.getDecoder().decode(value.replaceAll("\\s+", ""));
                return new ByteArrayInputStream(credentials);
            } catch (IllegalArgumentException ex) {
                logger.warn("FIREBASE_SERVICE_ACCOUNT_BASE64 is not valid Base64; trying it as JSON");
                return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
            }
        }

        // 2. Check Spring property or Environment variable for file path
        String filePath = (propPath != null && !propPath.isBlank())
                ? propPath
                : System.getenv("FIREBASE_SERVICE_ACCOUNT_PATH");

        if (filePath != null && !filePath.isBlank()) {
            File f = new File(filePath);
            if (f.exists() && f.isFile()) {
                return new FileInputStream(f);
            }
        }

        // 3. Check raw JSON in Environment variable
        String jsonCredentials = System.getenv("FIREBASE_SERVICE_ACCOUNT_JSON");
        if (jsonCredentials != null && !jsonCredentials.trim().isEmpty()) {
            return new ByteArrayInputStream(jsonCredentials.getBytes(StandardCharsets.UTF_8));
        }

        // 4. Check standard file locations
        Resource resource = new FileSystemResource("/app/firebase-service-account.json");
        if (!resource.exists()) {
            resource = new FileSystemResource("firebase-service-account.json");
        }
        if (!resource.exists()) {
            resource = new ClassPathResource("firebase-service-account.json");
        }
        return resource.exists() ? resource.getInputStream() : null;
    }
}
