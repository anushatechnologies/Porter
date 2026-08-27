package com.anushaporter.backend;

import com.anushaporter.backend.model.AppUser;
import com.anushaporter.backend.model.Driver;
import com.anushaporter.backend.repository.AppUserRepository;
import com.anushaporter.backend.repository.DriverRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import software.amazon.awssdk.services.s3.S3Client;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Optional;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = BackendApplication.class)
@Import(DriverPhotoUploadIntegrationTest.TestConfig.class)
public class DriverPhotoUploadIntegrationTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        public S3Client mockS3Client() {
            return Mockito.mock(S3Client.class);
        }
    }

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private DriverRepository driverRepository;

    @Autowired
    private AppUserRepository appUserRepository;

    private MockMvc mockMvc;
    private Driver testDriver;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();

        Optional<Driver> opt = driverRepository.findByPhone("9876543210");
        if (opt.isPresent()) {
            testDriver = opt.get();
        } else {
            Driver driver = new Driver();
            driver.setName("Ramesh Driver");
            driver.setPhone("9876543210");
            driver.setEmail("ramesh.driver@portertest.com");
            driver.setStatus("active");
            testDriver = driverRepository.save(driver);
        }
    }

    private byte[] createTestImage() throws Exception {
        BufferedImage image = new BufferedImage(200, 200, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics();
        g2d.setColor(Color.LIGHT_GRAY);
        g2d.fillRect(0, 0, 200, 200);
        g2d.setColor(Color.BLUE);
        g2d.fillOval(50, 50, 100, 100);
        g2d.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", baos);
        return baos.toByteArray();
    }

    @Test
    void testUploadDriverPhoto_Success() throws Exception {
        byte[] imageBytes = createTestImage();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "camera_selfie.jpg",
                "image/jpeg",
                imageBytes
        );

        mockMvc.perform(multipart("/api/driver/photo")
                        .file(file)
                        .param("driverId", testDriver.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.driverId", is(testDriver.getId().intValue())))
                .andExpect(jsonPath("$.url", containsString("/uploads/driver_photos/")))
                .andExpect(jsonPath("$.message", containsString("Driver photo uploaded successfully")));

        // Verify driver profile photo URI is updated in DB
        Driver updated = driverRepository.findById(testDriver.getId()).orElseThrow();
        assertTrue(updated.getProfilePhotoUri() != null && updated.getProfilePhotoUri().contains("/uploads/driver_photos/"));
    }

    @Test
    void testUploadDriverPhoto_ByPhone_AutoCreatesDriver() throws Exception {
        String uniquePhone = "9911223344";
        byte[] imageBytes = createTestImage();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "gallery_photo.jpg",
                "image/jpeg",
                imageBytes
        );

        mockMvc.perform(multipart("/api/driver/photo")
                        .file(file)
                        .param("phone", uniquePhone))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.url", containsString("/uploads/driver_photos/")));

        Optional<Driver> createdOpt = driverRepository.findByPhone(uniquePhone);
        assertTrue(createdOpt.isPresent());
        assertTrue(createdOpt.get().getProfilePhotoUri() != null);
    }

    @Test
    void testUploadDriverPhoto_SynchronizesWithAppUser() throws Exception {
        String testPhone = "9887766554";
        AppUser appUser = new AppUser();
        appUser.setName("Synced AppUser");
        appUser.setPhone(testPhone);
        appUser.setEmail("sync.test@porter.com");
        appUser.setRole("DRIVER");
        appUserRepository.save(appUser);

        byte[] imageBytes = createTestImage();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "synced_photo.jpg",
                "image/jpeg",
                imageBytes
        );

        mockMvc.perform(multipart("/api/driver/photo")
                        .file(file)
                        .param("phone", testPhone))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)));

        AppUser updatedUser = appUserRepository.findFirstByPhoneOrderByIdDesc(testPhone).orElseThrow();
        assertTrue(updatedUser.getProfilePhotoUri() != null && updatedUser.getProfilePhotoUri().contains("/uploads/driver_photos/"));
    }

    @Test
    void testUploadDriverPhoto_EmptyFile_Returns400() throws Exception {
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file",
                "empty.jpg",
                "image/jpeg",
                new byte[0]
        );

        mockMvc.perform(multipart("/api/driver/photo")
                        .file(emptyFile)
                        .param("driverId", testDriver.getId().toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", containsString("Photo file is required")));
    }

    @Test
    void testUploadDriverPhoto_AliasEndpoints() throws Exception {
        byte[] imageBytes = createTestImage();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "alias_photo.jpg",
                "image/jpeg",
                imageBytes
        );

        // Test alias: /api/driver/uploadPhoto
        mockMvc.perform(multipart("/api/driver/uploadPhoto")
                        .file(file)
                        .param("driverId", testDriver.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)));

        // Test alias: /driver/photo
        mockMvc.perform(multipart("/driver/photo")
                        .file(file)
                        .param("driverId", testDriver.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)));
    }
}
