package com.anushaporter.backend;

import com.anushaporter.backend.model.Driver;
import com.anushaporter.backend.repository.DriverRepository;
import com.anushaporter.backend.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.mockito.Mockito;
import software.amazon.awssdk.services.s3.S3Client;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;

@SpringBootTest(classes = BackendApplication.class)
@Import(DriverStatusIntegrationTest.TestConfig.class)
public class DriverStatusIntegrationTest {

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

    private Driver testDriver;
    private String jwtToken;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        driverRepository.deleteAll();

        testDriver = new Driver();
        testDriver.setName("Supriya Rao");
        testDriver.setEmail("supriya@example.com");
        testDriver.setPhone("9876543210");
        testDriver.setVehicleNumber("TS09AB1234");
        testDriver.setVehicleType("Tata Ace");
        testDriver.setStatus("online");
        testDriver.setKyc("verified");
        testDriver = driverRepository.save(testDriver);

        jwtToken = jwtUtil.generateToken(testDriver.getEmail());
    }

    @Test
    void testOptionAJwtStatusToggleOfflineThenOnline() throws Exception {
        // 1. Toggle to OFFLINE via PUT /api/drivers/me/status
        mockMvc.perform(put("/api/drivers/me/status")
                .header("Authorization", "Bearer " + jwtToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\": \"offline\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.status", is("offline")));

        // Verify in DB directly
        Driver inDb = driverRepository.findById(testDriver.getId()).orElseThrow();
        assertEquals("offline", inDb.getStatus());

        // 2. Verify via GET /api/drivers/me
        mockMvc.perform(get("/api/drivers/me")
                .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("offline")))
                .andExpect(jsonPath("$.email", is("supriya@example.com")));

        // 3. Toggle back to ONLINE via PUT /api/drivers/me/status
        mockMvc.perform(put("/api/drivers/me/status")
                .header("Authorization", "Bearer " + jwtToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\": \"online\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.status", is("online")));

        // Verify in DB directly
        inDb = driverRepository.findById(testDriver.getId()).orElseThrow();
        assertEquals("online", inDb.getStatus());

        // 4. Verify via Admin GET /api/drivers
        mockMvc.perform(get("/api/drivers")
                .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.email == 'supriya@example.com')].status", hasItem("online")));
    }

    @Test
    void testOptionBEmailStatusToggle() throws Exception {
        // Toggle to OFFLINE with URL-encoded email
        mockMvc.perform(put("/api/drivers/email/supriya%40example.com/status")
                .header("Authorization", "Bearer " + jwtToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\": \"offline\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.status", is("offline")));

        Driver inDb = driverRepository.findById(testDriver.getId()).orElseThrow();
        assertEquals("offline", inDb.getStatus());

        // Toggle back to ONLINE with regular email
        mockMvc.perform(put("/api/drivers/email/supriya@example.com/status")
                .header("Authorization", "Bearer " + jwtToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\": \"online\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.status", is("online")));

        inDb = driverRepository.findById(testDriver.getId()).orElseThrow();
        assertEquals("online", inDb.getStatus());
    }

    @Test
    void testOptionCIdStatusToggle() throws Exception {
        // Toggle via numeric ID
        mockMvc.perform(put("/api/drivers/" + testDriver.getId() + "/status")
                .header("Authorization", "Bearer " + jwtToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\": \"offline\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.status", is("offline")));

        Driver inDb = driverRepository.findById(testDriver.getId()).orElseThrow();
        assertEquals("offline", inDb.getStatus());

        // Toggle via "DRV-" prefixed ID
        mockMvc.perform(put("/api/drivers/DRV-" + testDriver.getId() + "/status")
                .header("Authorization", "Bearer " + jwtToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\": \"online\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.status", is("online")));

        inDb = driverRepository.findById(testDriver.getId()).orElseThrow();
        assertEquals("online", inDb.getStatus());
    }

    @Test
    void testPhoneBasedJwtTokenResolution() throws Exception {
        // JWT generated with driver phone number
        String phoneToken = jwtUtil.generateToken(testDriver.getPhone());

        mockMvc.perform(put("/api/drivers/me/status")
                .header("Authorization", "Bearer " + phoneToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\": \"offline\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.status", is("offline")));

        Driver inDb = driverRepository.findById(testDriver.getId()).orElseThrow();
        assertEquals("offline", inDb.getStatus());
    }

    @Test
    void testStatusVariantsAndSuspended() throws Exception {
        // Upper case OFFLINE
        mockMvc.perform(put("/api/drivers/me/status")
                .header("Authorization", "Bearer " + jwtToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\": \"OFFLINE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.status", is("offline")));

        // Boolean false
        mockMvc.perform(put("/api/drivers/me/status")
                .header("Authorization", "Bearer " + jwtToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"online\": false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.status", is("offline")));

        // Boolean true
        mockMvc.perform(put("/api/drivers/me/status")
                .header("Authorization", "Bearer " + jwtToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"online\": true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.status", is("online")));

        // Suspended
        mockMvc.perform(put("/api/drivers/me/status")
                .header("Authorization", "Bearer " + jwtToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\": \"suspended\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.status", is("suspended")));

        Driver inDb = driverRepository.findById(testDriver.getId()).orElseThrow();
        assertEquals("suspended", inDb.getStatus());
    }
}
