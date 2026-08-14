package com.anushaporter.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
public class AwsConfig {

    @Bean
    public S3Client s3Client() {
        String regionStr = System.getenv("AWS_REGION");
        if (regionStr == null || regionStr.isBlank()) {
            regionStr = System.getProperty("aws.region", "ap-south-1");
        }
        return S3Client.builder()
                .region(Region.of(regionStr))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    @Bean
    public S3Presigner s3Presigner() {
        String regionStr = System.getenv("AWS_REGION");
        if (regionStr == null || regionStr.isBlank()) {
            regionStr = System.getProperty("aws.region", "ap-south-1");
        }
        try {
            return S3Presigner.builder()
                    .region(Region.of(regionStr))
                    .credentialsProvider(DefaultCredentialsProvider.create())
                    .build();
        } catch (Exception e) {
            return null;
        }
    }
}

