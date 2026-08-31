package com.anushaporter.backend;

import com.anushaporter.backend.service.S3ImageServiceImpl;
import com.anushaporter.backend.service.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class S3ImageServiceImplTest {

    @Mock
    private S3Client s3Client;

    @Spy
    private StorageService storageService;

    @InjectMocks
    private S3ImageServiceImpl s3ImageService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(s3ImageService, "bucketName", "poteranusha");
        ReflectionTestUtils.setField(s3ImageService, "regionName", "ap-south-2");
        ReflectionTestUtils.setField(s3ImageService, "baseUploadDir", "uploads/");
    }

    @Test
    void testUploadMultipartFileDirectlyToS3() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "driver_license.jpg",
                "image/jpeg",
                "dummy license image content".getBytes(StandardCharsets.UTF_8)
        );

        String resultUrl = s3ImageService.uploadImage(file, "license");

        assertNotNull(resultUrl);
        assertTrue(resultUrl.startsWith("https://poteranusha.s3.ap-south-2.amazonaws.com/license/"));
        assertTrue(resultUrl.endsWith(".jpg"));

        ArgumentCaptor<PutObjectRequest> requestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client, times(1)).putObject(requestCaptor.capture(), any(RequestBody.class));

        PutObjectRequest captured = requestCaptor.getValue();
        assertEquals("poteranusha", captured.bucket());
        assertTrue(captured.key().startsWith("license/"));
        assertEquals("image/jpeg", captured.contentType());
    }

    @Test
    void testUploadImageFromBytesWithDedicatedFolder() {
        byte[] bytes = "png dummy image content".getBytes(StandardCharsets.UTF_8);

        String resultUrl = s3ImageService.uploadImageFromBytes(bytes, "rc_doc.png", "image/png", "rc");

        assertNotNull(resultUrl);
        assertTrue(resultUrl.startsWith("https://poteranusha.s3.ap-south-2.amazonaws.com/rc/"));
        assertTrue(resultUrl.endsWith(".png"));

        ArgumentCaptor<PutObjectRequest> requestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client, times(1)).putObject(requestCaptor.capture(), any(RequestBody.class));

        PutObjectRequest captured = requestCaptor.getValue();
        assertEquals("poteranusha", captured.bucket());
        assertEquals("image/png", captured.contentType());
        assertTrue(captured.key().startsWith("rc/"));
    }

    @Test
    void testProcessAndUploadImageUri_WhenAlreadyOurS3Url_DoesNotReupload() {
        String existingS3Url = "https://poteranusha.s3.ap-south-2.amazonaws.com/aadhaar/ba4c8473-c1dc-417d-b456-bfe7222be8a8.jpeg";

        String result = s3ImageService.processAndUploadImageUri(existingS3Url, "aadhaar");

        assertEquals(existingS3Url, result);
        verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void testProcessAndUploadImageUri_WhenBase64_DecodesAndUploadsToS3() {
        byte[] sampleBytes = "test base64 image".getBytes(StandardCharsets.UTF_8);
        String base64DataUri = "data:image/png;base64," + Base64.getEncoder().encodeToString(sampleBytes);

        String resultUrl = s3ImageService.processAndUploadImageUri(base64DataUri, "profile-photo");

        assertNotNull(resultUrl);
        assertTrue(resultUrl.startsWith("https://poteranusha.s3.ap-south-2.amazonaws.com/profile-photo/"));
        assertTrue(resultUrl.endsWith(".png"));

        verify(s3Client, times(1)).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void testProcessAndUploadImageUri_WhenNullOrBlank_ReturnsNull() {
        assertNull(s3ImageService.processAndUploadImageUri(null, "aadhaar"));
        assertNull(s3ImageService.processAndUploadImageUri("", "aadhaar"));
        assertNull(s3ImageService.processAndUploadImageUri("   ", "aadhaar"));
        verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void testDeleteImage_WhenOurS3Url_CallsDeleteObject() {
        String s3Url = "https://poteranusha.s3.ap-south-2.amazonaws.com/bank-passbook/uuid-12345.jpeg";

        s3ImageService.deleteImage(s3Url);

        ArgumentCaptor<DeleteObjectRequest> captor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client, times(1)).deleteObject(captor.capture());

        DeleteObjectRequest captured = captor.getValue();
        assertEquals("poteranusha", captured.bucket());
        assertEquals("bank-passbook/uuid-12345.jpeg", captured.key());
    }

    @Test
    void testDeleteImage_WhenExternalUrl_DoesNotCallDeleteObject() {
        String externalUrl = "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcR";

        s3ImageService.deleteImage(externalUrl);

        verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));
    }
}
