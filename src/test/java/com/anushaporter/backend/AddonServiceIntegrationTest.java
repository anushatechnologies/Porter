package com.anushaporter.backend;

import com.anushaporter.backend.model.AddonService;
import com.anushaporter.backend.repository.AddonServiceRepository;
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

import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = BackendApplication.class)
@Import(AddonServiceIntegrationTest.TestConfig.class)
public class AddonServiceIntegrationTest {

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
    private AddonServiceRepository addonServiceRepository;

    @Autowired
    private JwtUtil jwtUtil;

    private ObjectMapper objectMapper = new ObjectMapper();
    private String adminToken;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        adminToken = "Bearer " + jwtUtil.generateToken("admin@porter.com");
    }

    @Test
    void testGetAddons_CustomerApp_ReturnsActiveAddons() throws Exception {
        mockMvc.perform(get("/api/addons"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.total", greaterThanOrEqualTo(3)))
                .andExpect(jsonPath("$.addons[?(@.addonId == 'addon_load_assist')].perItemRate", hasItem(7.0)))
                .andExpect(jsonPath("$.addons[?(@.addonId == 'addon_installation')].price", hasItem(300.0)));
    }

    @Test
    void testGetAddons_FilterByCategory_TruckAndPackers() throws Exception {
        // 1. Truck Load Assist add-ons
        mockMvc.perform(get("/api/addons?category=truck"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.addons", not(empty())))
                .andExpect(jsonPath("$.addons[0].category", is("truck")));

        // 2. Packers add-ons
        mockMvc.perform(get("/api/addons?category=packers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.addons", not(empty())))
                .andExpect(jsonPath("$.addons[0].category", is("packers")));
    }

    @Test
    void testGetAddons_FilterByCapacity_500Kg() throws Exception {
        mockMvc.perform(get("/api/addons?category=truck&capacityKg=500"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.addons[?(@.addonId == 'addon_load_assist')]", not(empty())));
    }

    @Test
    void testAdminAddonCrud() throws Exception {
        // 1. Admin List Addons
        mockMvc.perform(get("/api/admin/addons").header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.total", greaterThanOrEqualTo(1)));

        // 2. Admin Create Addon
        AddonService newAddon = new AddonService();
        newAddon.setAddonId("addon_express_delivery_test");
        newAddon.setName("Express Priority Move");
        newAddon.setCategory("packers");
        newAddon.setServiceType("express");
        newAddon.setPrice(499.0);
        newAddon.setIsActive(true);

        mockMvc.perform(post("/api/admin/addons")
                .header("Authorization", adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(newAddon)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.addon.addonId", is("addon_express_delivery_test")));

        // 3. Admin Update Addon
        newAddon.setPrice(599.0);
        mockMvc.perform(put("/api/admin/addons/addon_express_delivery_test")
                .header("Authorization", adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(newAddon)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.addon.price", is(599.0)));

        // 4. Admin Toggle Status
        mockMvc.perform(patch("/api/admin/addons/addon_express_delivery_test/status")
                .header("Authorization", adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("isActive", false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isActive", is(false)));

        // 5. Admin Delete Addon
        mockMvc.perform(delete("/api/admin/addons/addon_express_delivery_test")
                .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)));
    }
}
