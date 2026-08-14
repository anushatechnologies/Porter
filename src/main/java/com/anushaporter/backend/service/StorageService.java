package com.anushaporter.backend.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class StorageService {

    @Autowired(required = false)
    private S3Presigner s3Presigner;

    // Pattern to match standard S3 URLs:
    // e.g. https://bucket-name.s3.ap-south-2.amazonaws.com/path/to/file.jpg
    // or https://s3.ap-south-2.amazonaws.com/bucket-name/path/to/file.jpg
    private static final Pattern S3_VIRTUAL_HOST_PATTERN = Pattern.compile("^https?://([^.]+)\\.s3[.-][^/]+\\.amazonaws\\.com/(.+)$");
    private static final Pattern S3_PATH_STYLE_PATTERN = Pattern.compile("^https?://s3[.-][^/]+\\.amazonaws\\.com/([^/]+)/(.+)$");

    /**
     * Sanitizes any URI to eliminate duplicated or prepended hostnames.
     * e.g. "https://api.anushaporter.comhttps://poteranusha.s3.amazonaws.com/license/123.jpeg"
     * -> "https://poteranusha.s3.amazonaws.com/license/123.jpeg"
     */
    public String sanitizeUri(String uri) {
        if (uri == null || uri.trim().isEmpty()) {
            return "";
        }
        String cleaned = uri.trim();

        // Check for multiple http:// or https:// occurrences
        int lastHttps = cleaned.lastIndexOf("https://");
        int lastHttp = cleaned.lastIndexOf("http://");
        int lastIndex = Math.max(lastHttps, lastHttp);

        if (lastIndex > 0) {
            cleaned = cleaned.substring(lastIndex);
        }

        return cleaned;
    }

    /**
     * Generates a pre-signed URL for an S3 object (valid for 2 hours) to prevent 403 Forbidden,
     * or returns the sanitized URL if presigning is unavailable.
     */
    public String getPresignedOrSanitizedUrl(String uri) {
        String cleanUrl = sanitizeUri(uri);
        if (cleanUrl.isEmpty() || s3Presigner == null) {
            return cleanUrl;
        }

        try {
            String bucket = null;
            String key = null;

            Matcher vHostMatcher = S3_VIRTUAL_HOST_PATTERN.matcher(cleanUrl);
            if (vHostMatcher.matches()) {
                bucket = vHostMatcher.group(1);
                key = vHostMatcher.group(2);
            } else {
                Matcher pathStyleMatcher = S3_PATH_STYLE_PATTERN.matcher(cleanUrl);
                if (pathStyleMatcher.matches()) {
                    bucket = pathStyleMatcher.group(1);
                    key = pathStyleMatcher.group(2);
                }
            }

            if (bucket != null && key != null) {
                // If key contains query params, strip them before building GetObjectRequest
                if (key.contains("?")) {
                    key = key.substring(0, key.indexOf("?"));
                }

                GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .build();

                GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                        .signatureDuration(Duration.ofHours(2))
                        .getObjectRequest(getObjectRequest)
                        .build();

                PresignedGetObjectRequest presigned = s3Presigner.presignGetObject(presignRequest);
                return presigned.url().toString();
            }
        } catch (Exception ignored) {
            // Graceful fallback to the sanitized URL
        }

        return cleanUrl;
    }
}
