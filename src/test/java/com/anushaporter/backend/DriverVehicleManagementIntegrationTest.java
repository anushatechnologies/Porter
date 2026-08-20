package com.anushaporter.backend;

import com.anushaporter.backend.config.DriverVehicleDataCorrectionRunner;
import com.anushaporter.backend.model.AppUser;
import com.anushaporter.backend.model.Driver;
import com.anushaporter.backend.model.VehicleType;
import com.anushaporter.backend.repository.AppUserRepository;
import com.anushaporter.backend.repository.DriverRepository;
import com.anushaporter.backend.repository.VehicleTypeRepository;
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

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = BackendApplication.class)
@Import(DriverVehicleManagementIntegrationTest.TestConfig.class)
public class DriverVehicleManagementIntegrationTest {

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
    private VehicleTypeRepository vehicleTypeRepository;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DriverVehicleDataCorrectionRunner migrationRunner;

    private String testToken;
    private String testPhone = "9014397044";
    private String testEmail = "bollipellisupriya123@gmail.com";

    @BeforeEach
    public void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        appUserRepository.deleteAll();
        driverRepository.deleteAll();

        AppUser user = new AppUser();
        user.setEmail(testEmail);
        user.setPhone(testPhone);
        user.setName("Supriya Bollipelli");
        user.setRole("driver");
        appUserRepository.save(user);

        testToken = "Bearer " + jwtUtil.generateToken(testEmail);
    }

    @Test
    public void testDriverRegistration_populatesBothVehicleAndVehicleType() throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("name", "Supriya Bollipelli");
        payload.put("email", testEmail);
        payload.put("phone", testPhone);
        payload.put("dob", "17/05/1999");
        payload.put("gender", "Female");
        payload.put("addressLine1", "Kurichedu Rd");
        payload.put("city", "Hyderabad");
        payload.put("state", "Telangana");
        payload.put("pincode", "523304");
        payload.put("vehicleType", "Scooter");
        payload.put("vehicleNumber", "TG63737383882");
        payload.put("rcNumber", "RC5363728929299");
        payload.put("aadhaarNumber", "555588966665");
        payload.put("licenseNumber", "DL63737382882828");
        payload.put("bankName", "SBI");
        payload.put("accountHolderName", "Supriya");
        payload.put("accountNumber", "488446646494949499");
        payload.put("ifscCode", "SBIN0006788");

        mockMvc.perform(post("/api/drivers/register")
                        .header("Authorization", testToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.vehicle").value("Scooter"))
                .andExpect(jsonPath("$.vehicleType").value("Scooter"));

        Driver saved = driverRepository.findByPhone(testPhone).orElseThrow();
        assertEquals("Scooter", saved.getVehicle());
        assertEquals("Scooter", saved.getVehicleType());
    }

    @Test
    public void testGetAllDrivers_returnsVehicleAndVehicleType() throws Exception {
        Driver d = new Driver();
        d.setName("Test Driver");
        d.setPhone("9876543210");
        d.setEmail("testdriver@example.com");
        d.setVehicle(null);
        d.setVehicleType("Tata Ace");
        d.setVehicleNumber("TS09AB1234");
        d.setKyc("verified");
        d.setStatus("offline");
        driverRepository.save(d);

        mockMvc.perform(get("/api/drivers")
                        .header("Authorization", testToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].vehicle").value("Tata Ace"))
                .andExpect(jsonPath("$[0].vehicleType").value("Tata Ace"));
    }

    @Test
    public void testGetDriverById_returnsUnifiedVehicleFields() throws Exception {
        Driver d = new Driver();
        d.setName("Ramesh");
        d.setPhone("9988776655");
        d.setEmail("ramesh@example.com");
        d.setVehicle("2 Wheeler");
        d.setVehicleType(null);
        d.setVehicleNumber("TS07XY9999");
        d.setKyc("verified");
        d.setStatus("online");
        Driver saved = driverRepository.save(d);

        mockMvc.perform(get("/api/drivers/" + saved.getId())
                        .header("Authorization", testToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vehicle").value("2 Wheeler"))
                .andExpect(jsonPath("$.vehicleType").value("2 Wheeler"));
    }

    @Test
    public void testVehicleDataMigrationRunner_fixesExistingNullVehicleRecords() {
        Driver d1 = new Driver();
        d1.setName("Legacy Driver 1");
        d1.setPhone("9111111111");
        d1.setVehicle(null);
        d1.setVehicleType("Scooter");
        driverRepository.save(d1);

        Driver d2 = new Driver();
        d2.setName("Legacy Driver 2");
        d2.setPhone("9222222222");
        d2.setVehicle("Tata Ace");
        d2.setVehicleType(null);
        driverRepository.save(d2);

        Driver d3 = new Driver();
        d3.setName("Legacy Driver 3");
        d3.setPhone("9333333333");
        d3.setVehicle(null);
        d3.setVehicleType(null);
        driverRepository.save(d3);

        migrationRunner.run();

        Driver updated1 = driverRepository.findByPhone("9111111111").orElseThrow();
        assertEquals("Scooter", updated1.getVehicle());
        assertEquals("Scooter", updated1.getVehicleType());

        Driver updated2 = driverRepository.findByPhone("9222222222").orElseThrow();
        assertEquals("Tata Ace", updated2.getVehicle());
        assertEquals("Tata Ace", updated2.getVehicleType());

        Driver updated3 = driverRepository.findByPhone("9333333333").orElseThrow();
        assertEquals("Scooter", updated3.getVehicle());
        assertEquals("Scooter", updated3.getVehicleType());
    }

    @Test
    public void testDynamicVehicleTypes_CRUDAndActiveFilter() throws Exception {
        // 1. Fetch active vehicle types (public / apps)
        mockMvc.perform(get("/api/vehicle-types?status=active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.vehicles").isArray())
                .andExpect(jsonPath("$.vehicles[0].baseFare").exists())
                .andExpect(jsonPath("$.vehicles[0].perKmRate").exists());

        // 2. Create new dynamic vehicle type (Admin)
        Map<String, Object> newVehicle = new HashMap<>();
        newVehicle.put("id", "veh_electric_99");
        newVehicle.put("name", "Electric Scooter");
        newVehicle.put("type", "ev_scooter");
        newVehicle.put("capacity", "Load: Up to 30kg");
        newVehicle.put("capacityKg", 30);
        newVehicle.put("dimensions", "Ideal for quick green deliveries");
        newVehicle.put("iconName", "bike");
        newVehicle.put("baseFare", 35.0);
        newVehicle.put("baseKm", 1.0);
        newVehicle.put("perKmRate", 10.0);
        newVehicle.put("status", "active");
        newVehicle.put("priority", 10);

        mockMvc.perform(post("/api/admin/vehicle-types")
                        .header("Authorization", testToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newVehicle)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.vehicle.name").value("Electric Scooter"))
                .andExpect(jsonPath("$.vehicle.baseFare").value(35.0));

        // 3. Update vehicle category (Admin)
        Map<String, Object> updatePayload = new HashMap<>();
        updatePayload.put("baseFare", 38.0);
        updatePayload.put("perKmRate", 11.0);

        mockMvc.perform(put("/api/admin/vehicle-types/veh_electric_99")
                        .header("Authorization", testToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updatePayload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vehicle.baseFare").value(38.0));

        // 4. Toggle status to inactive (Admin)
        mockMvc.perform(patch("/api/admin/vehicle-types/veh_electric_99/status")
                        .header("Authorization", testToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "inactive"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("inactive"));

        // 5. Verify it is saved as inactive
        VehicleType vt = vehicleTypeRepository.findById("veh_electric_99").orElseThrow();
        assertEquals("inactive", vt.getStatus());
    }
}
