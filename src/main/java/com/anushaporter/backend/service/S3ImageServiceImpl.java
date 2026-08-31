package com.anushaporter.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class S3ImageServiceImpl implements S3ImageService {

    private static final Logger log = LoggerFactory.getLogger(S3ImageServiceImpl.class);

    @Autowired(required = false)
    private S3Client s3Client;

    @Value("${aws.s3.bucket.name:poteranusha}")
    private String bucketName;

    @Value("${aws.s3.region:ap-south-2}")
    private String regionName;

    @Value("${file.upload-dir:uploads/}")
    private String baseUploadDir;

    @Autowired
    private StorageService storageService;

    private static final Pattern S3_KEY_PATTERN = Pattern.compile("^https?://[^/]+/(.+)$");

    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Override
    public String uploadImage(MultipartFile file, String folder) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Cannot upload empty or null file to S3");
        }

        try {
            byte[] bytes = file.getBytes();
            String originalFilename = file.getOriginalFilename();
            String contentType = file.getContentType();
            return uploadImageFromBytes(bytes, originalFilename, contentType, folder);
        } catch (IOException e) {
            log.error("Failed to read bytes from multipart file: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to read image file: " + e.getMessage(), e);
        }
    }

    @Override
    public String uploadImageFromBytes(byte[] bytes, String originalFilenameOrExt, String contentType, String folder) {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("Cannot upload empty byte array to S3");
        }

        String extension = determineExtension(originalFilenameOrExt, contentType);
        String resolvedContentType = determineContentType(extension, contentType);
        String cleanFolder = cleanFolderName(folder);

        String key = cleanFolder + "/" + UUID.randomUUID().toString() + extension;

        if (s3Client == null) {
            log.warn("S3Client is not configured. Falling back to mock public S3 URL for key: {}", key);
            return getPublicUrl(key);
        }

        try {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .contentType(resolvedContentType)
                    .build();

            s3Client.putObject(putObjectRequest, RequestBody.fromBytes(bytes));
            String publicUrl = getPublicUrl(key);
            log.info("✓ Successfully uploaded image to S3: {}", publicUrl);
            return publicUrl;
        } catch (Exception e) {
            log.error("S3 PutObject failed for key '{}' in bucket '{}': {}", key, bucketName, e.getMessage(), e);
            throw new RuntimeException("Failed to upload image to S3: " + e.getMessage(), e);
        }
    }

    @Override
    public String processAndUploadImageUri(String uriOrUrl, String folder) {
        if (uriOrUrl == null || uriOrUrl.trim().isEmpty()) {
            return null;
        }

        String cleaned = storageService.sanitizeUri(uriOrUrl);
        if (cleaned.isEmpty()) {
            return null;
        }

        // 1. If it is already an S3 URL pointing to our bucket, return it directly without duplicate uploading
        if (isOurS3Url(cleaned)) {
            log.debug("URI is already in our S3 bucket, skipping re-upload: {}", cleaned);
            return cleaned;
        }

        // 2. Base64 Data URI
        if (cleaned.startsWith("data:") || cleaned.contains(";base64,")) {
            try {
                String base64Data = cleaned;
                String inferredContentType = "image/jpeg";
                if (cleaned.contains(";base64,")) {
                    int prefixEnd = cleaned.indexOf(";base64,");
                    String meta = cleaned.substring(0, prefixEnd);
                    if (meta.startsWith("data:")) {
                        inferredContentType = meta.substring(5);
                    }
                    base64Data = cleaned.substring(prefixEnd + 8);
                }
                byte[] bytes = Base64.getDecoder().decode(base64Data.trim());
                return uploadImageFromBytes(bytes, null, inferredContentType, folder);
            } catch (Exception e) {
                log.error("Failed to decode base64 image data: {}", e.getMessage(), e);
                throw new RuntimeException("Invalid Base64 image payload: " + e.getMessage(), e);
            }
        }

        // 3. Local file path (e.g. "/uploads/...", "uploads/...", or relative disk path)
        if (cleaned.startsWith("/uploads/") || cleaned.startsWith("uploads/") || cleaned.startsWith("file:")) {
            try {
                String localRelativePath = cleaned;
                if (localRelativePath.startsWith("file:")) {
                    localRelativePath = localRelativePath.substring(5);
                }
                if (localRelativePath.startsWith("/")) {
                    localRelativePath = localRelativePath.substring(1);
                }

                Path diskPath = Paths.get(localRelativePath);
                if (!Files.exists(diskPath)) {
                    // Try resolving inside baseUploadDir
                    String normBase = baseUploadDir.replace("\\", "/");
                    if (!normBase.endsWith("/")) normBase += "/";
                    diskPath = Paths.get(normBase + diskPath.getFileName().toString());
                }

                if (Files.exists(diskPath)) {
                    byte[] bytes = Files.readAllBytes(diskPath);
                    String filename = diskPath.getFileName().toString();
                    String probeType = Files.probeContentType(diskPath);
                    log.info("Migrating local disk image '{}' to S3 under folder '{}'", diskPath, folder);
                    return uploadImageFromBytes(bytes, filename, probeType, folder);
                } else {
                    log.warn("Local image path '{}' not found on disk. Uploading cannot locate source.", diskPath);
                }
            } catch (Exception e) {
                log.error("Failed to read local file for S3 migration: {}", e.getMessage(), e);
            }
        }

        // 4. Remote HTTP/HTTPS URL (e.g. Google Images, iStock, third-party CDN)
        if (cleaned.startsWith("http://") || cleaned.startsWith("https://")) {
            try {
                log.info("Downloading remote image from '{}' to upload to S3 folder '{}'", cleaned, folder);
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(cleaned))
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                        .header("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
                        .timeout(Duration.ofSeconds(15))
                        .GET()
                        .build();

                HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    byte[] bytes = response.body();
                    if (bytes != null && bytes.length > 0) {
                        String contentType = response.headers().firstValue("Content-Type").orElse("image/jpeg");
                        // Clean content type (strip charset parameters)
                        if (contentType.contains(";")) {
                            contentType = contentType.split(";")[0].trim();
                        }
                        String path = URI.create(cleaned).getPath();
                        String originalFilename = (path != null && path.contains("/")) ? path.substring(path.lastIndexOf("/") + 1) : null;
                        return uploadImageFromBytes(bytes, originalFilename, contentType, folder);
                    }
                } else {
                    log.warn("Remote image download returned HTTP status {}: {}", response.statusCode(), cleaned);
                }
            } catch (Exception e) {
                log.error("Failed to download remote image from '{}': {}", cleaned, e.getMessage(), e);
                throw new RuntimeException("Failed to download external image: " + e.getMessage(), e);
            }
        }

        return cleaned;
    }

    @Override
    public void deleteImage(String s3Url) {
        if (s3Url == null || s3Url.trim().isEmpty() || s3Client == null) {
            return;
        }

        String cleaned = storageService.sanitizeUri(s3Url);
        if (!isOurS3Url(cleaned)) {
            return;
        }

        try {
            String key = extractKeyFromS3Url(cleaned);
            if (key != null && !key.isBlank()) {
                DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                        .bucket(bucketName)
                        .key(key)
                        .build();
                s3Client.deleteObject(deleteRequest);
                log.info("✓ Deleted S3 object: key '{}' from bucket '{}'", key, bucketName);
            }
        } catch (Exception e) {
            log.warn("Failed to delete S3 object '{}': {}", s3Url, e.getMessage());
        }
    }

    @Override
    public boolean isOurS3Url(String url) {
        if (url == null || url.trim().isEmpty()) {
            return false;
        }
        String clean = storageService.sanitizeUri(url).toLowerCase();
        return clean.contains(bucketName.toLowerCase() + ".s3.") ||
                clean.contains(bucketName.toLowerCase() + ".s3-") ||
                clean.contains(bucketName.toLowerCase() + ".s3.amazonaws.com") ||
                clean.contains("amazonaws.com/" + bucketName.toLowerCase()) ||
                clean.contains("poteranusha.s3");
    }

    @Override
    public String getPublicUrl(String key) {
        String cleanKey = key.startsWith("/") ? key.substring(1) : key;
        return "https://" + bucketName + ".s3." + regionName + ".amazonaws.com/" + cleanKey;
    }

    private String cleanFolderName(String folder) {
        if (folder == null || folder.trim().isEmpty()) {
            return "misc";
        }
        String clean = folder.trim().replace("\\", "/");
        while (clean.startsWith("/")) clean = clean.substring(1);
        while (clean.endsWith("/")) clean = clean.substring(0, clean.length() - 1);
        return clean.isEmpty() ? "misc" : clean;
    }

    private String determineExtension(String originalFilenameOrExt, String contentType) {
        if (originalFilenameOrExt != null && originalFilenameOrExt.contains(".")) {
            String ext = originalFilenameOrExt.substring(originalFilenameOrExt.lastIndexOf(".")).toLowerCase();
            if (ext.matches("^\\.[a-z0-9]{3,5}$") && !ext.equals(".html") && !ext.equals(".php")) {
                return ext;
            }
        }

        if (contentType != null) {
            String type = contentType.toLowerCase();
            if (type.contains("png")) return ".png";
            if (type.contains("jpeg") || type.contains("jpg")) return ".jpeg";
            if (type.contains("webp")) return ".webp";
            if (type.contains("gif")) return ".gif";
            if (type.contains("pdf")) return ".pdf";
            if (type.contains("svg")) return ".svg";
        }

        return ".jpeg";
    }

    private String determineContentType(String extension, String fallbackContentType) {
        if (extension != null) {
            String ext = extension.toLowerCase();
            if (ext.equals(".png")) return "image/png";
            if (ext.equals(".jpg") || ext.equals(".jpeg")) return "image/jpeg";
            if (ext.equals(".webp")) return "image/webp";
            if (ext.equals(".gif")) return "image/gif";
            if (ext.equals(".pdf")) return "application/pdf";
            if (ext.equals(".svg")) return "image/svg+xml";
        }
        return (fallbackContentType != null && !fallbackContentType.isBlank() && !fallbackContentType.equals("application/octet-stream"))
                ? fallbackContentType : "image/jpeg";
    }

    private String extractKeyFromS3Url(String s3Url) {
        try {
            URI uri = URI.create(s3Url);
            String path = uri.getPath();
            if (path != null) {
                while (path.startsWith("/")) {
                    path = path.substring(1);
                }
                // If path starts with bucket name (path style), strip it
                if (path.startsWith(bucketName + "/")) {
                    path = path.substring(bucketName.length() + 1);
                }
                return path;
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
