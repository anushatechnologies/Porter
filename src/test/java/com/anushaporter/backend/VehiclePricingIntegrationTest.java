package com.anushaporter.backend;

import com.anushaporter.backend.model.PricingVehicle;
import com.anushaporter.backend.repository.PricingVehicleRepository;
import com.anushaporter.backend.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import org.mockito.Mockito;
import software.amazon.awssdk.services.s3.S3Client;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = BackendApplication.class)
@Import(VehiclePricingIntegrationTest.TestConfig.class)
public class VehiclePricingIntegrationTest {

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
    private PricingVehicleRepository vehicleRepository;

    @Autowired
    private JwtUtil jwtUtil;

    private String token;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        vehicleRepository.deleteAll();
        token = jwtUtil.generateToken("admin@anushaporter.com");
    }

    @Test
    void testPostPricingSavesAndReturnsAllFieldsWithoutNulls() throws Exception {
        String payload = """
        {
          "vehicleId": "scooter-model",
          "name": "Scooter Model",
          "baseFare": 100,
          "pricePerKm": 15,
          "minFare": 100,
          "maxFare": 5000,
          "freeDistance": 2,
          "minDistance": 2,
          "maxDistance": 500,
          "capacityKg": 500,
          "status": true,
          "priority": 1,
          "commissionPercentage": 10
        }
        """;

        mockMvc.perform(post("/api/pricing")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.vehicle.vehicleId", is("scooter-model")))
                .andExpect(jsonPath("$.vehicle.name", is("Scooter Model")))
                .andExpect(jsonPath("$.vehicle.baseFare", is(100.0)))
                .andExpect(jsonPath("$.vehicle.pricePerKm", is(15.0)))
                .andExpect(jsonPath("$.vehicle.minFare", is(100.0)))
                .andExpect(jsonPath("$.vehicle.maxFare", is(5000.0)))
                .andExpect(jsonPath("$.vehicle.freeDistance", is(2.0)))
                .andExpect(jsonPath("$.vehicle.minDistance", is(2.0)))
                .andExpect(jsonPath("$.vehicle.maxDistance", is(500.0)))
                .andExpect(jsonPath("$.vehicle.capacityKg", is(500.0)))
                .andExpect(jsonPath("$.vehicle.status", is(true)))
                .andExpect(jsonPath("$.vehicle.priority", is(1)))
                .andExpect(jsonPath("$.vehicle.commissionPercentage", is(10.0)));

        // Verify in DB directly
        PricingVehicle inDb = vehicleRepository.findFirstByVehicleIdIgnoreCase("scooter-model").orElseThrow();
        assertEquals("scooter-model", inDb.getVehicleId());
        assertEquals("Scooter Model", inDb.getName());
        assertEquals(100.0, inDb.getBaseFare());
        assertEquals(15.0, inDb.getPricePerKm());
        assertEquals(100.0, inDb.getMinFare());
        assertEquals(5000.0, inDb.getMaxFare());
        assertEquals(2.0, inDb.getFreeDistance());
        assertEquals(500.0, inDb.getCapacityKg());
        assertEquals(true, inDb.getStatus());
        assertEquals(1, inDb.getPriority());
    }

    @Test
    void testGetVehiclePricingByVehicleId() throws Exception {
        PricingVehicle vehicle = new PricingVehicle();
        vehicle.setVehicleId("scooter-model");
        vehicle.setName("Scooter Model");
        vehicle.setBaseFare(100.0);
        vehicle.setPricePerKm(15.0);
        vehicle.setMinFare(100.0);
        vehicle.setMaxFare(5000.0);
        vehicle.setFreeDistance(2.0);
        vehicle.setMinDistance(2.0);
        vehicle.setMaxDistance(500.0);
        vehicle.setCapacityKg(500.0);
        vehicle.setStatus(true);
        vehicle.setPriority(1);
        vehicle.setCommissionPercentage(10.0);
        vehicleRepository.save(vehicle);

        mockMvc.perform(get("/api/pricing/vehicle/scooter-model")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vehicleId", is("scooter-model")))
                .andExpect(jsonPath("$.name", is("Scooter Model")))
                .andExpect(jsonPath("$.baseFare", is(100.0)))
                .andExpect(jsonPath("$.pricePerKm", is(15.0)))
                .andExpect(jsonPath("$.minFare", is(100.0)))
                .andExpect(jsonPath("$.maxFare", is(5000.0)))
                .andExpect(jsonPath("$.freeDistance", is(2.0)))
                .andExpect(jsonPath("$.maxDistance", is(500.0)))
                .andExpect(jsonPath("$.capacityKg", is(500.0)))
                .andExpect(jsonPath("$.status", is(true)))
                .andExpect(jsonPath("$.priority", is(1)));
    }

    @Test
    void testPutVehiclePricingUpdatesExistingVehicle() throws Exception {
        PricingVehicle vehicle = new PricingVehicle();
        vehicle.setVehicleId("scooter-model");
        vehicle.setName("Scooter Model");
        vehicle.setBaseFare(100.0);
        vehicle.setPricePerKm(15.0);
        vehicle.setMinFare(100.0);
        vehicle.setMaxFare(5000.0);
        vehicle.setCapacityKg(500.0);
        vehicle.setStatus(true);
        vehicleRepository.save(vehicle);

        String updatePayload = """
        {
          "vehicleId": "scooter-model",
          "name": "Scooter Model Updated",
          "baseFare": 120,
          "pricePerKm": 18,
          "minFare": 120,
          "maxFare": 6000,
          "freeDistance": 3,
          "minDistance": 3,
          "maxDistance": 600,
          "capacityKg": 600,
          "status": true,
          "priority": 2,
          "commissionPercentage": 12
        }
        """;

        mockMvc.perform(put("/api/pricing/vehicle/scooter-model")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updatePayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.vehicle.vehicleId", is("scooter-model")))
                .andExpect(jsonPath("$.vehicle.name", is("Scooter Model Updated")))
                .andExpect(jsonPath("$.vehicle.baseFare", is(120.0)))
                .andExpect(jsonPath("$.vehicle.pricePerKm", is(18.0)))
                .andExpect(jsonPath("$.vehicle.maxFare", is(6000.0)))
                .andExpect(jsonPath("$.vehicle.capacityKg", is(600.0)))
                .andExpect(jsonPath("$.vehicle.commissionPercentage", is(12.0)));

        PricingVehicle inDb = vehicleRepository.findFirstByVehicleIdIgnoreCase("scooter-model").orElseThrow();
        assertEquals(120.0, inDb.getBaseFare());
        assertEquals(18.0, inDb.getPricePerKm());
        assertEquals(6000.0, inDb.getMaxFare());
        assertEquals(600.0, inDb.getCapacityKg());
    }

    @Test
    void testDeleteVehiclePricing() throws Exception {
        PricingVehicle vehicle = new PricingVehicle();
        vehicle.setVehicleId("scooter-model");
        vehicle.setName("Scooter Model");
        vehicle.setBaseFare(100.0);
        vehicleRepository.save(vehicle);

        // Delete by vehicleId
        mockMvc.perform(delete("/api/pricing/scooter-model")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)));

        // Verify deleted from DB
        assertFalse(vehicleRepository.findFirstByVehicleIdIgnoreCase("scooter-model").isPresent());

        // Subsequent GET returns 404
        mockMvc.perform(get("/api/pricing/vehicle/scooter-model")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }
}
