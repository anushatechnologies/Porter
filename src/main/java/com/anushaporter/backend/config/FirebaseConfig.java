package com.anushaporter.backend.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.api.client.http.apache.v2.ApacheHttpTransport;

@Configuration
public class FirebaseConfig {

    private static final Logger logger = LoggerFactory.getLogger(FirebaseConfig.class);

    @PostConstruct
    public void initialize() {
        try {
            org.springframework.core.io.Resource resource = new org.springframework.core.io.FileSystemResource("/app/firebase-service-account.json");
            
            if (!resource.exists()) {
                resource = new org.springframework.core.io.ClassPathResource("firebase-service-account.json");
            }

            if (!resource.exists()) {
                logger.warn("firebase-service-account.json not found in /app/ or classpath. Firebase Admin SDK will not be initialized.");
                return;
            }
            
            InputStream serviceAccount = resource.getInputStream();

            // Use ApacheHttpTransport to bypass Java's built-in HttpURLConnection GZIP bug on AWS
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
}
