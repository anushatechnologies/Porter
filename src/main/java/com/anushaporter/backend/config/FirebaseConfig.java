package com.anushaporter.backend.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import jakarta.annotation.PostConstruct;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.api.client.http.apache.v2.ApacheHttpTransport;

@Configuration
public class FirebaseConfig {

    private static final Logger logger = LoggerFactory.getLogger(FirebaseConfig.class);

    @PostConstruct
    public void initialize() {
        try (InputStream serviceAccount = getServiceAccountStream()) {
            if (serviceAccount == null) {
                logger.error("Firebase credentials are not configured. Set FIREBASE_SERVICE_ACCOUNT_BASE64.");
                return;
            }

            // Use ApacheHttpTransport to bypass Java's built-in HttpURLConnection GZIP bug
            // on AWS
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                    .setHttpTransport(new ApacheHttpTransport())
                    .build();

            if (FirebaseApp.getApps().isEmpty()) {
                FirebaseApp.initializeApp(options);
                logger.info("Firebase Admin SDK initialized successfully with Apache HTTP Transport.");
            }
        } catch (Exception e) {
            logger.error("Failed to initialize Firebase Admin SDK", e);
        }
    }

    private InputStream getServiceAccountStream() throws Exception {
        String encodedCredentials = System.getenv("FIREBASE_SERVICE_ACCOUNT_BASE64");
        if (encodedCredentials != null && !encodedCredentials.trim().isEmpty()) {
            byte[] credentials = Base64.getDecoder().decode(encodedCredentials.replaceAll("\\s+", ""));
            return new ByteArrayInputStream(credentials);
        }

        String jsonCredentials = System.getenv("FIREBASE_SERVICE_ACCOUNT_JSON");
        if (jsonCredentials != null && !jsonCredentials.trim().isEmpty()) {
            return new ByteArrayInputStream(jsonCredentials.getBytes(StandardCharsets.UTF_8));
        }

        org.springframework.core.io.Resource resource = new org.springframework.core.io.FileSystemResource(
                "/app/firebase-service-account.json");
        if (!resource.exists()) {
            resource = new org.springframework.core.io.ClassPathResource("firebase-service-account.json");
        }
        return resource.exists() ? resource.getInputStream() : null;
    }
}
