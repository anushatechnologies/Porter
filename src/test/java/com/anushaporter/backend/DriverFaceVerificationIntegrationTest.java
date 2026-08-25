package com.anushaporter.backend;

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

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = BackendApplication.class)
@Import(DriverFaceVerificationIntegrationTest.TestConfig.class)
public class DriverFaceVerificationIntegrationTest {

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

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    private byte[] createTestImage(int width, int height, Color bgColor, Color skinColor) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics();
        g2d.setColor(bgColor);
        g2d.fillRect(0, 0, width, height);

        if (skinColor != null) {
            // Draw a face oval in center
            g2d.setColor(skinColor);
            g2d.fillOval(width / 4, height / 5, width / 2, (height * 3) / 5);
        }
        g2d.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", baos);
        return baos.toByteArray();
    }

    @Test
    void testVerifyFace_Success_SingleFace() throws Exception {
        // Human skin tone in RGB (e.g. RGB(230, 180, 140))
        byte[] validSelfieBytes = createTestImage(200, 200, Color.LIGHT_GRAY, new Color(230, 180, 140));

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "selfie.jpg",
                "image/jpeg",
                validSelfieBytes
        );

        mockMvc.perform(multipart("/api/drivers/verify-face").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.faceCount", is(1)))
                .andExpect(jsonPath("$.message", containsString("Human Face Verified")))
                .andExpect(jsonPath("$.url", notNullValue()));
    }

    @Test
    void testVerifyFace_EmptyFile_Returns400() throws Exception {
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file",
                "selfie.jpg",
                "image/jpeg",
                new byte[0]
        );

        mockMvc.perform(multipart("/api/drivers/verify-face").file(emptyFile))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.faceCount", is(0)))
                .andExpect(jsonPath("$.message", containsString("No human face was detected")));
    }

    @Test
    void testVerifyFace_DarkBlankImage_Returns400WithIsBlank() throws Exception {
        // Completely black / dark photo
        byte[] darkImageBytes = createTestImage(200, 200, Color.BLACK, null);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "dark_selfie.jpg",
                "image/jpeg",
                darkImageBytes
        );

        mockMvc.perform(multipart("/api/drivers/verify-face").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.faceCount", is(0)))
                .andExpect(jsonPath("$.isBlank", is(true)))
                .andExpect(jsonPath("$.message", containsString("photo is too dark or blurry")));
    }

    @Test
    void testVerifyFace_NoFace_Returns400NoFace() throws Exception {
        // Solid green background without skin pixels
        byte[] noFaceBytes = createTestImage(200, 200, Color.BLUE, null);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "scenery.jpg",
                "image/jpeg",
                noFaceBytes
        );

        mockMvc.perform(multipart("/api/drivers/verify-face").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.faceCount", is(0)))
                .andExpect(jsonPath("$.message", containsString("No human face was detected")));
    }
}
