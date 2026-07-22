package com.anushaporter.backend.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.io.FileNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Configuration
public class FirebaseConfig {

    private static final Logger logger = LoggerFactory.getLogger(FirebaseConfig.class);

    @PostConstruct
    public void initialize() {
        try {
            ClassPathResource resource = new ClassPathResource("firebase-service-account.json");
            
            if (!resource.exists()) {
                logger.warn("firebase-service-account.json not found in classpath. Firebase Admin SDK will not be initialized. Please add the file to src/main/resources/.");
                return;
            }
            
            InputStream serviceAccount = resource.getInputStream();

            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                    .build();

            if (FirebaseApp.getApps().isEmpty()) {
                FirebaseApp.initializeApp(options);
                logger.info("Firebase Admin SDK initialized successfully.");
            }
        } catch (Exception e) {
            logger.error("Failed to initialize Firebase Admin SDK", e);
        }
    }
}
