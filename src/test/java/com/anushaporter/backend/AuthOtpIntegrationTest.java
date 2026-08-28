package com.anushaporter.backend;

import com.anushaporter.backend.model.AppUser;
import com.anushaporter.backend.repository.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import software.amazon.awssdk.services.s3.S3Client;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = BackendApplication.class)
public class AuthOtpIntegrationTest {

    @Configuration
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
    private AppUserRepository userRepository;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        userRepository.deleteAll();
    }

    @Test
    void testSendOtpSuccess() throws Exception {
        String requestBody = "{\"phone\": \"9876543210\", \"mode\": \"login\"}";

        mockMvc.perform(post("/api/auth/send-otp")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.message", containsString("OTP sent")))
                .andExpect(jsonPath("$.phone", is("9876543210")));
    }

    @Test
    void testResendOtpSuccess() throws Exception {
        String requestBody = "{\"phone\": \"9876543210\"}";

        mockMvc.perform(post("/api/auth/resend-otp")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.message", containsString("OTP resent")));
    }

    @Test
    void testVerifyOtpLoginMode() throws Exception {
        String requestBody = "{\"phone\": \"9876543210\", \"otp\": \"123456\", \"mode\": \"login\"}";

        mockMvc.perform(post("/api/auth/verify-otp")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.user.phone", is("9876543210")))
                .andExpect(jsonPath("$.user.isPhoneVerified", is(true)));
    }

    @Test
    void testVerifyOtpSignupModeCreatesUser() throws Exception {
        String requestBody = "{\"phone\": \"9123456789\", \"otp\": \"123456\", \"mode\": \"signup\", \"name\": \"Jane Doe\"}";

        mockMvc.perform(post("/api/auth/verify-otp")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.user.name", is("Jane Doe")))
                .andExpect(jsonPath("$.user.phone", is("9123456789")));
    }
}
