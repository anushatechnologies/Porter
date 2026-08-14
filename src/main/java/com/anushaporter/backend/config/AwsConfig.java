package com.anushaporter.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

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
}
