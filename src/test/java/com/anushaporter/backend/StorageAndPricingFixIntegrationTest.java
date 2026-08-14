package com.anushaporter.backend;

import com.anushaporter.backend.model.Driver;
import com.anushaporter.backend.model.PricingVehicle;
import com.anushaporter.backend.model.Vehicle;
import com.anushaporter.backend.repository.DriverRepository;
import com.anushaporter.backend.repository.PricingVehicleRepository;
import com.anushaporter.backend.repository.VehicleRepository;
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
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = BackendApplication.class)
@Import(StorageAndPricingFixIntegrationTest.TestConfig.class)
public class StorageAndPricingFixIntegrationTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        public S3Client mockS3Client() {
            return Mockito.mock(S3Client.class);
        }

        @Bean
        @Primary
        public S3Presigner mockS3Presigner() {
            return Mockito.mock(S3Presigner.class);
        }
    }

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @Autowired
    private DriverRepository driverRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private PricingVehicleRepository pricingVehicleRepository;

    @Autowired
    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        driverRepository.deleteAll();
        vehicleRepository.deleteAll();
        pricingVehicleRepository.deleteAll();
    }

    @Test
    void testGetVehiclesReturnsMergedPricingFields() throws Exception {
        // 1. Setup PricingVehicle in pricing table
        PricingVehicle pvBike = new PricingVehicle();
        pvBike.setVehicleId("bike");
        pvBike.setName("Bike");
        pvBike.setBaseFare(100.0);
        pvBike.setPricePerKm(15.0);
        pvBike.setMinFare(100.0);
        pvBike.setMaxFare(5000.0);
        pvBike.setFreeDistance(2.0);
        pvBike.setCapacityKg(500.0);
        pvBike.setStatus(true);
        pvBike.setPriority(1);
        pricingVehicleRepository.save(pvBike);

        PricingVehicle pvScooter = new PricingVehicle();
        pvScooter.setVehicleId("scooter-model");
        pvScooter.setName("Scooter Model");
        pvScooter.setBaseFare(120.0);
        pvScooter.setPricePerKm(18.0);
        pvScooter.setMinFare(120.0);
        pvScooter.setMaxFare(5000.0);
        pvScooter.setFreeDistance(2.0);
        pvScooter.setCapacityKg(500.0);
        pvScooter.setStatus(true);
        pvScooter.setPriority(2);
        pricingVehicleRepository.save(pvScooter);

        // 2. Setup Fleet Vehicle in vehicle inventory table
        Vehicle vBike = new Vehicle();
        vBike.setModel("Bike Model");
        vBike.setType("Bike");
        vBike.setPlate("TS09AB1111");
        vBike.setOwner("Ramesh");
        vBike.setCapacity("500 kg");
        vehicleRepository.save(vBike);

        // 3. Perform GET /api/vehicles
        mockMvc.perform(get("/api/vehicles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].vehicleId", is("bike")))
                .andExpect(jsonPath("$[0].baseFare", is(100.0)))
                .andExpect(jsonPath("$[0].pricePerKm", is(15.0)))
                .andExpect(jsonPath("$[0].capacityKg", is(500.0)))
                .andExpect(jsonPath("$[1].vehicleId", is("scooter-model")))
                .andExpect(jsonPath("$[1].baseFare", is(120.0)))
                .andExpect(jsonPath("$[1].pricePerKm", is(18.0)));
    }

    @Test
    void testDriverDocumentSanitizationPreventsDoublePrefixing() throws Exception {
        Driver driver = new Driver();
        driver.setName("Vasu");
        driver.setEmail("vasu@driver.com");
        driver.setPhone("9876543210");
        driver.setStatus("online");
        // Simulated corrupted double-prefix URL
        driver.setLicenseUri("https://api.anushaporter.comhttps://poteranusha.s3.ap-south-2.amazonaws.com/license/ba4c8473-c1dc-417d-b456-bfe7222be8a8.jpeg");
        driver.setRcUri("https://api.anushaporter.comhttps://poteranusha.s3.ap-south-2.amazonaws.com/rc/f1234567-c1dc-417d-b456-bfe7222be8a8.jpeg");
        driver = driverRepository.save(driver);

        String token = jwtUtil.generateToken(driver.getEmail());

        // GET /api/drivers must sanitize and strip out the duplicate hostname
        mockMvc.perform(get("/api/drivers")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name", is("Vasu")))
                .andExpect(jsonPath("$[0].licenseUri", is("https://poteranusha.s3.ap-south-2.amazonaws.com/license/ba4c8473-c1dc-417d-b456-bfe7222be8a8.jpeg")))
                .andExpect(jsonPath("$[0].rcUri", is("https://poteranusha.s3.ap-south-2.amazonaws.com/rc/f1234567-c1dc-417d-b456-bfe7222be8a8.jpeg")));

        // GET /api/drivers/me must also return sanitized URI
        mockMvc.perform(get("/api/drivers/me")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['licenseUri']", is("https://poteranusha.s3.ap-south-2.amazonaws.com/license/ba4c8473-c1dc-417d-b456-bfe7222be8a8.jpeg")));
    }

    @Test
    void testDriverStatusPersistenceAcrossEndpoints() throws Exception {
        Driver driver = new Driver();
        driver.setName("Supriya");
        driver.setEmail("supriya@driver.com");
        driver.setPhone("9876543211");
        driver.setStatus("online");
        driver = driverRepository.save(driver);

        String token = jwtUtil.generateToken(driver.getEmail());

        // 1. Driver App calls PUT /api/driver/status with { "status": "offline" }
        mockMvc.perform(put("/api/driver/status")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\": \"offline\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.status", is("offline")));

        // Verify status in DB is offline
        Driver inDb = driverRepository.findById(driver.getId()).orElseThrow();
        assertEquals("offline", inDb.getStatus());

        // Verify GET /api/drivers immediately returns offline
        mockMvc.perform(get("/api/drivers")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status", is("offline")));

        // 2. Driver App calls PATCH /api/drivers/me/status with { "online": true }
        mockMvc.perform(patch("/api/drivers/me/status")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"online\": true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.status", is("online")));

        inDb = driverRepository.findById(driver.getId()).orElseThrow();
        assertEquals("online", inDb.getStatus());
    }
}
