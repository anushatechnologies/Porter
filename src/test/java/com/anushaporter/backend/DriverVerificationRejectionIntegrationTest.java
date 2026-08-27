package com.anushaporter.backend;

import com.anushaporter.backend.model.Driver;
import com.anushaporter.backend.repository.DriverRepository;
import com.anushaporter.backend.util.JwtUtil;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import software.amazon.awssdk.services.s3.S3Client;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = BackendApplication.class)
@Import(DriverVerificationRejectionIntegrationTest.TestConfig.class)
public class DriverVerificationRejectionIntegrationTest {

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

    @Autowired
    private DriverRepository driverRepository;

    @Autowired
    private JwtUtil jwtUtil;

    private ObjectMapper objectMapper = new ObjectMapper();
    private String adminToken;
    private Driver testDriver;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        adminToken = "Bearer " + jwtUtil.generateToken("admin@porter.com");

        testDriver = driverRepository.findByPhone("9988776655").orElseGet(() -> {
            Driver d = new Driver();
            d.setName("Ravi Kumar");
            d.setPhone("9988776655");
            d.setEmail("ravi.kumar.test@porter.com");
            d.setKyc("pending");
            d.setVehicle("Tata Ace");
            d.setVehicleType("Tata Ace");
            return driverRepository.save(d);
        });
    }

    @Test
    void testDriverRejectionWithRequiredDocuments() throws Exception {
        Map<String, Object> payload = Map.of(
                "rejectionReason", "Invalid RC or Expired License",
                "rejectedDocuments", List.of("rc", "license"),
                "notes", "Please re-upload clear photos of your front RC and driving license.",
                "requireReupload", true
        );

        mockMvc.perform(post("/api/admin/drivers/" + testDriver.getId() + "/reject")
                .header("Authorization", adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.verificationStatus", is("REJECTED_REQUIRES_REUPLOAD")))
                .andExpect(jsonPath("$.rejectionReason", is("Invalid RC or Expired License")))
                .andExpect(jsonPath("$.rejectedDocuments", is("rc,license")));

        Driver updated = driverRepository.findById(testDriver.getId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals("REJECTED_REQUIRES_REUPLOAD", updated.getVerificationStatus());
        org.junit.jupiter.api.Assertions.assertEquals("rejected", updated.getKyc());
    }
}
