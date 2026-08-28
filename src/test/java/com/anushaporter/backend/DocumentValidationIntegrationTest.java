package com.anushaporter.backend;

import com.anushaporter.backend.dto.ValidationReason;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import software.amazon.awssdk.services.s3.S3Client;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = BackendApplication.class)
@Import(DocumentValidationIntegrationTest.TestConfig.class)
public class DocumentValidationIntegrationTest {

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
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        this.objectMapper = new ObjectMapper();
    }

    private byte[] createImageWithText(String... lines) throws IOException {
        BufferedImage image = new BufferedImage(1000, 600, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 1000, 600);
        g.setColor(Color.BLACK);
        g.setFont(new Font("Arial", Font.BOLD, 28));

        int y = 60;
        for (String line : lines) {
            g.drawString(line, 50, y);
            y += 45;
        }
        g.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", baos);
        return baos.toByteArray();
    }

    @Test
    void testValidatePanEndpoint_ValidPan_Returns200() throws Exception {
        byte[] panImage = createImageWithText(
                "INCOME TAX DEPARTMENT",
                "GOVT. OF INDIA",
                "PERMANENT ACCOUNT NUMBER",
                "ABCDE1234F"
        );

        MockMultipartFile file = new MockMultipartFile("file", "pan.png", "image/png", panImage);

        mockMvc.perform(multipart("/api/documents/validate")
                        .file(file)
                        .param("type", "PAN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid", is(true)))
                .andExpect(jsonPath("$.status", is(200)))
                .andExpect(jsonPath("$.documentType", is("PAN")))
                .andExpect(jsonPath("$.extractedData.panNumber", is("ABCDE1234F")));
    }

    @Test
    void testValidateAadhaarEndpoint_ValidAadhaar_Returns200() throws Exception {
        byte[] aadhaarImage = createImageWithText(
                "GOVERNMENT OF INDIA",
                "UNIQUE IDENTIFICATION AUTHORITY OF INDIA",
                "DOB: 01/01/1990",
                "1234 5678 9012"
        );

        MockMultipartFile file = new MockMultipartFile("file", "aadhaar.png", "image/png", aadhaarImage);

        mockMvc.perform(multipart("/api/documents/validate")
                        .file(file)
                        .param("type", "AADHAAR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid", is(true)))
                .andExpect(jsonPath("$.status", is(200)))
                .andExpect(jsonPath("$.documentType", is("AADHAAR")));
    }

    @Test
    void testValidatePanEndpoint_Mismatch_RcUploaded_Returns422() throws Exception {
        byte[] rcImage = createImageWithText(
                "CERTIFICATE OF REGISTRATION",
                "FORM 23",
                "REGISTERING AUTHORITY",
                "Regn No: MH12AB1234",
                "Chassis No: MA3EWB2S880012345"
        );

        MockMultipartFile file = new MockMultipartFile("file", "rc.png", "image/png", rcImage);

        mockMvc.perform(multipart("/api/documents/validate")
                        .file(file)
                        .param("type", "PAN"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.valid", is(false)))
                .andExpect(jsonPath("$.status", is(422)))
                .andExpect(jsonPath("$.documentType", is("PAN")))
                .andExpect(jsonPath("$.reason", is(ValidationReason.DOCUMENT_TYPE_MISMATCH)))
                .andExpect(jsonPath("$.message", containsString("PAN")));
    }

    @Test
    void testValidateRcEndpoint_ValidRc_Returns200() throws Exception {
        byte[] rcImage = createImageWithText(
                "CERTIFICATE OF REGISTRATION",
                "FORM 23",
                "REGISTERING AUTHORITY",
                "Regn No: MH12AB1234",
                "Chassis No: MA3EWB2S880012345"
        );

        MockMultipartFile file = new MockMultipartFile("file", "rc.png", "image/png", rcImage);

        mockMvc.perform(multipart("/api/documents/validate")
                        .file(file)
                        .param("type", "RC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid", is(true)))
                .andExpect(jsonPath("$.status", is(200)))
                .andExpect(jsonPath("$.documentType", is("RC")));
    }

    @Test
    void testValidateEndpoint_MissingType_Returns400() throws Exception {
        byte[] testImage = createImageWithText("Sample text");
        MockMultipartFile file = new MockMultipartFile("file", "test.png", "image/png", testImage);

        mockMvc.perform(multipart("/api/documents/validate")
                        .file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.message", containsString("Missing required parameter 'type'")));
    }

    @Test
    void testValidateEndpoint_InvalidDocType_Returns400() throws Exception {
        byte[] testImage = createImageWithText("Sample text");
        MockMultipartFile file = new MockMultipartFile("file", "test.png", "image/png", testImage);

        mockMvc.perform(multipart("/api/documents/validate")
                        .file(file)
                        .param("type", "PASSPORT_INTERNATIONAL"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.message", containsString("Unknown document type")));
    }

    @Test
    void testValidateJsonBase64Payload_ValidPan_Returns200() throws Exception {
        byte[] panImage = createImageWithText(
                "INCOME TAX DEPARTMENT",
                "GOVT. OF INDIA",
                "PERMANENT ACCOUNT NUMBER",
                "ABCDE1234F"
        );
        String base64Str = "data:image/png;base64," + Base64.getEncoder().encodeToString(panImage);

        Map<String, String> payload = Map.of(
                "type", "PAN",
                "image", base64Str
        );

        mockMvc.perform(post("/api/documents/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid", is(true)))
                .andExpect(jsonPath("$.status", is(200)))
                .andExpect(jsonPath("$.documentType", is("PAN")));
    }
}
