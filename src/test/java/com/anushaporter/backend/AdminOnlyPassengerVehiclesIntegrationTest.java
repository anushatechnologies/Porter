package com.anushaporter.backend;

import com.anushaporter.backend.model.PassengerVehicleCategory;
import com.anushaporter.backend.model.VehicleType;
import com.anushaporter.backend.repository.PassengerVehicleCategoryRepository;
import com.anushaporter.backend.repository.VehicleTypeRepository;
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

import java.math.BigDecimal;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = BackendApplication.class)
@Import(AdminOnlyPassengerVehiclesIntegrationTest.TestConfig.class)
public class AdminOnlyPassengerVehiclesIntegrationTest {

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
    private VehicleTypeRepository vehicleTypeRepository;

    @Autowired
    private PassengerVehicleCategoryRepository passengerVehicleCategoryRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        passengerVehicleCategoryRepository.deleteAll();
        vehicleTypeRepository.deleteAll();
    }

    @Test
    void testPassengerEndpoints_ReturnEmpty_WhenNoAdminVehiclesAdded() throws Exception {
        // When database is clean, passenger endpoints should NOT return hardcoded vehicles
        mockMvc.perform(get("/api/passenger/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.vehicles", hasSize(0)));

        mockMvc.perform(get("/api/vehicle-types")
                        .param("status", "active")
                        .param("serviceType", "PASSENGER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.vehicles", hasSize(0)));
    }

    @Test
    void testAdminCreatedVehicle_ShowsInPassengerAndCanBeDeactivated() throws Exception {
        // 1. Admin adds a new passenger vehicle via Admin Portal
        Map<String, Object> adminPayload = new java.util.LinkedHashMap<>();
        adminPayload.put("id", "admin_luxury_sedan");
        adminPayload.put("name", "Luxury Sedan");
        adminPayload.put("type", "luxury_sedan");
        adminPayload.put("serviceType", "PASSENGER");
        adminPayload.put("description", "Premium 4-seater luxury car");
        adminPayload.put("capacity", "4 Passengers");
        adminPayload.put("capacityKg", 200);
        adminPayload.put("baseFare", 250.0);
        adminPayload.put("baseKm", 2.0);
        adminPayload.put("perKmRate", 25.0);
        adminPayload.put("status", "active");
        adminPayload.put("priority", 1);

        mockMvc.perform(post("/api/admin/vehicle-types")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(adminPayload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true));

        // 2. Verify it shows up in Passenger endpoints
        mockMvc.perform(get("/api/passenger/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.vehicles", hasSize(1)))
                .andExpect(jsonPath("$.vehicles[0].name").value("Luxury Sedan"));

        mockMvc.perform(get("/api/vehicle-types")
                        .param("status", "active")
                        .param("serviceType", "PASSENGER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.vehicles", hasSize(1)))
                .andExpect(jsonPath("$.vehicles[0].name").value("Luxury Sedan"));

        // 3. Admin deactivates / deletes the vehicle
        mockMvc.perform(delete("/api/admin/vehicle-types/admin_luxury_sedan"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // 4. Verify it no longer appears in Passenger endpoints
        mockMvc.perform(get("/api/passenger/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.vehicles", hasSize(0)));

        mockMvc.perform(get("/api/vehicle-types")
                        .param("status", "active")
                        .param("serviceType", "PASSENGER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.vehicles", hasSize(0)));
    }

    @Test
    void testAdminCreatedPassengerCategory_ShowsInPassengerApp() throws Exception {
        // 1. Admin adds a category via /api/admin/passenger/vehicle-categories
        PassengerVehicleCategory cat = PassengerVehicleCategory.builder()
                .categoryCode("ELECTRIC_CAB")
                .displayName("Electric City Cab")
                .passengerCapacity(4)
                .luggageCapacity(2)
                .baseFare(new BigDecimal("120.00"))
                .minimumKm(new BigDecimal("2.0"))
                .perKmRate(new BigDecimal("14.00"))
                .minimumFare(new BigDecimal("120.00"))
                .active(true)
                .displayOrder(1)
                .build();

        mockMvc.perform(post("/api/admin/passenger/vehicle-categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cat)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists());

        // 2. Fetch categories as passenger
        mockMvc.perform(get("/api/passenger/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.vehicles", hasSize(1)))
                .andExpect(jsonPath("$.vehicles[0].displayName").value("Electric City Cab"));

        // 3. Admin deletes the category via DELETE endpoint
        var savedCat = passengerVehicleCategoryRepository.findByCategoryCode("ELECTRIC_CAB").orElseThrow();
        mockMvc.perform(delete("/api/admin/passenger/vehicle-categories/" + savedCat.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // 4. Passenger endpoint now returns 0 active vehicles
        mockMvc.perform(get("/api/passenger/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.vehicles", hasSize(0)));
    }
}
