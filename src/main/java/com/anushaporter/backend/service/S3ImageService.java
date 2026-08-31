package com.anushaporter.backend.service;

import org.springframework.web.multipart.MultipartFile;

public interface S3ImageService {

    /**
     * Uploads a MultipartFile directly to S3 into the specified folder.
     *
     * @param file   the uploaded file
     * @param folder target S3 directory (e.g. "aadhaar", "license", "rc", "profile-photo", "bank-passbook")
     * @return the full public S3 URL (e.g. https://poteranusha.s3.ap-south-2.amazonaws.com/aadhaar/<uuid>.jpeg)
     */
    String uploadImage(MultipartFile file, String folder);

    /**
     * Uploads raw bytes to S3 into the specified folder.
     *
     * @param bytes                 image binary data
     * @param originalFilenameOrExt filename or extension (e.g. "image.png" or ".jpeg")
     * @param contentType           MIME type (e.g. "image/jpeg")
     * @param folder                target S3 directory
     * @return the full public S3 URL
     */
    String uploadImageFromBytes(byte[] bytes, String originalFilenameOrExt, String contentType, String folder);

    /**
     * Processes any supplied image URI/URL (HTTP/HTTPS remote URL, local uploads path, or Base64 data).
     * If the URI is already an S3 URL pointing to our bucket, it is returned directly without re-uploading.
     * If it is an external URL, the image is downloaded server-side and uploaded to S3.
     *
     * @param uriOrUrl the incoming image URL or local path
     * @param folder   target S3 directory
     * @return the resulting S3 public URL, or the original URI if null/blank
     */
    String processAndUploadImageUri(String uriOrUrl, String folder);

    /**
     * Deletes an S3 object if the URL belongs to the configured S3 bucket.
     *
     * @param s3Url full public S3 URL
     */
    void deleteImage(String s3Url);

    /**
     * Checks if the given URL is already hosted in our S3 bucket.
     *
     * @param url image URL to test
     * @return true if URL is an S3 URL for our bucket
     */
    boolean isOurS3Url(String url);

    /**
     * Generates the public S3 URL for a given object key.
     *
     * @param key S3 object key (e.g. "aadhaar/ba4c8473-c1dc-417d-b456-bfe7222be8a8.jpeg")
     * @return full public S3 URL
     */
    String getPublicUrl(String key);
}
