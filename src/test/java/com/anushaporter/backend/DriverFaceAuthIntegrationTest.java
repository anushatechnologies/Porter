package com.anushaporter.backend;

import com.anushaporter.backend.model.Driver;
import com.anushaporter.backend.model.DriverFaceEmbedding;
import com.anushaporter.backend.repository.DriverFaceEmbeddingRepository;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = BackendApplication.class)
@Import(DriverFaceAuthIntegrationTest.TestConfig.class)
public class DriverFaceAuthIntegrationTest {

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
    private DriverFaceEmbeddingRepository faceEmbeddingRepository;

    private MockMvc mockMvc;
    private Driver testDriver;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();

        // Create or get a test driver
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

    private byte[] createSyntheticFaceImage(int width, int height, Color bgColor, Color skinColor) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics();
        g2d.setColor(bgColor);
        g2d.fillRect(0, 0, width, height);

        if (skinColor != null) {
            // Draw realistic face oval
            g2d.setColor(skinColor);
            int faceW = width * 5 / 8;
            int faceH = height * 6 / 8;
            int faceX = (width - faceW) / 2;
            int faceY = (height - faceH) / 2;
            g2d.fillOval(faceX, faceY, faceW, faceH);

            // Draw eyes
            g2d.setColor(new Color(60, 40, 20));
            int eyeW = faceW / 6;
            int eyeH = faceH / 10;
            g2d.fillOval(faceX + faceW / 4 - eyeW / 2, faceY + faceH / 3, eyeW, eyeH);
            g2d.fillOval(faceX + (3 * faceW / 4) - eyeW / 2, faceY + faceH / 3, eyeW, eyeH);

            // Draw nose & mouth
            g2d.setColor(new Color(180, 110, 80));
            g2d.drawLine(faceX + faceW / 2, faceY + faceH / 3 + eyeH, faceX + faceW / 2, faceY + faceH * 3 / 5);
            g2d.fillRoundRect(faceX + faceW / 3, faceY + faceH * 7 / 10, faceW / 3, faceH / 12, 4, 4);
        }
        g2d.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", baos);
        return baos.toByteArray();
    }

    @Test
    void testRegisterFace_CompleteFlow() throws Exception {
        // 1. Generate realistic driver face image
        byte[] faceBytes = createSyntheticFaceImage(240, 240, Color.LIGHT_GRAY, new Color(225, 175, 135));

        MockMultipartFile registerFile = new MockMultipartFile(
                "file",
                "driver_face.jpg",
                "image/jpeg",
                faceBytes
        );

        // 2. Register Driver Face: POST /driver/registerFace
        mockMvc.perform(multipart("/driver/registerFace")
                        .file(registerFile)
                        .param("driverId", testDriver.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.authenticated", is(true)))
                .andExpect(jsonPath("$.driverId", is(testDriver.getId().intValue())))
                .andExpect(jsonPath("$.livenessScore", notNullValue()))
                .andExpect(jsonPath("$.photoUrl", notNullValue()));

        // Verify DB persistence
        Optional<DriverFaceEmbedding> stored = faceEmbeddingRepository
                .findFirstByDriverIdAndStatusOrderByIdDesc(testDriver.getId(), "ACTIVE");
        assertTrue(stored.isPresent(), "DriverFaceEmbedding must be persisted in database");
        assertTrue(stored.get().getEmbeddingVector() != null && !stored.get().getEmbeddingVector().isBlank(),
                "Biometric embedding vector must not be blank");
    }

    @Test
    void testRegisterFace_NoFaceDetected_Returns400() throws Exception {
        byte[] noFaceBytes = createSyntheticFaceImage(200, 200, Color.BLUE, null);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "scenery.jpg",
                "image/jpeg",
                noFaceBytes
        );

        mockMvc.perform(multipart("/driver/registerFace")
                        .file(file)
                        .param("driverId", testDriver.getId().toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", containsString("No human face detected")));
    }

    @Test
    void testRegisterFace_AutoProvisionNewDriver_Success() throws Exception {
        byte[] faceBytes = createSyntheticFaceImage(200, 200, Color.LIGHT_GRAY, new Color(220, 170, 130));

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "face.jpg",
                "image/jpeg",
                faceBytes
        );

        mockMvc.perform(multipart("/driver/registerFace")
                        .file(file)
                        .param("phone", "9123456789"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.authenticated", is(true)));
    }
}
