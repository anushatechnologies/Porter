package com.anushaporter.backend;

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

import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = BackendApplication.class)
@Import(DriverOnboardingAndOfferFixIntegrationTest.TestConfig.class)
public class DriverOnboardingAndOfferFixIntegrationTest {

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
    private DriverRepository driverRepository;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private VehicleTypeRepository vehicleTypeRepository;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;
    private String driverToken;
    private AppUser driverUser;
    private Driver driver;

    @BeforeEach
    public void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();

        String testPhone = "9988776655";
        driverUser = appUserRepository.findFirstByPhoneOrderByIdDesc(testPhone).orElseGet(() -> {
            AppUser u = new AppUser();
            u.setPhone(testPhone);
            u.setName("Test Driver Partner");
            u.setRole("Driver");
            return appUserRepository.save(u);
        });

        driverToken = jwtUtil.generateToken(driverUser.getPhone());

        driver = driverRepository.findByPhone(testPhone).orElseGet(() -> {
            Driver d = new Driver();
            d.setPhone(testPhone);
            d.setName("Test Driver Partner");
            d.setStatus("online");
            d.setKyc("approved");
            d.setServiceType("PASSENGER");
            d.setVehicle("Cab");
            d.setVehicleType("Cab");
            return driverRepository.save(d);
        });
    }

    @Test
    public void testDriverRegistrationSubmitAlias_Succeeds() throws Exception {
        String newPhone = "9988770011";
        AppUser newUser = new AppUser();
        newUser.setPhone(newPhone);
        newUser.setName("Fresh Driver Candidate");
        newUser.setRole("Driver");
        appUserRepository.save(newUser);
        String newToken = jwtUtil.generateToken(newPhone);

        Map<String, Object> payload = Map.of(
                "name", "Anusha Driver",
                "phone", newPhone,
                "serviceType", "PASSENGER",
                "vehicle", "Cab",
                "vehicleNumber", "AP28TV9999",
                "submit", true
        );

        mockMvc.perform(post("/api/driver/registration/submit")
                        .header("Authorization", "Bearer " + newToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.isRegistered", is(true)))
                .andExpect(jsonPath("$.registrationStep", is(5)));
    }

    @Test
    public void testPassengerVehicleTypes_IncludesBikeTaxiAndAutoTaxi() throws Exception {
        mockMvc.perform(get("/api/vehicle-types")
                        .param("status", "active")
                        .param("serviceType", "PASSENGER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.vehicles", hasSize(greaterThanOrEqualTo(3))))
                .andExpect(jsonPath("$.vehicles[*].serviceType", everyItem(is("PASSENGER"))))
                .andExpect(jsonPath("$.vehicles[*].id", hasItems("6", "pass_bike", "pass_auto")));
    }

    @Test
    public void testActiveOffersEndpointAndMultiKeyResponse_Succeeds() throws Exception {
        mockMvc.perform(get("/api/driver/offers/active")
                        .header("Authorization", "Bearer " + driverToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.offers", notNullValue()))
                .andExpect(jsonPath("$.availableOrders", notNullValue()))
                .andExpect(jsonPath("$.orders", notNullValue()))
                .andExpect(jsonPath("$.data", notNullValue()));
    }

    @Test
    public void testAvailableOrdersMultiKeyResponse_Succeeds() throws Exception {
        mockMvc.perform(get("/api/driver/orders/available")
                        .header("Authorization", "Bearer " + driverToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.availableOrders", notNullValue()))
                .andExpect(jsonPath("$.orders", notNullValue()))
                .andExpect(jsonPath("$.offers", notNullValue()))
                .andExpect(jsonPath("$.data", notNullValue()));
    }

    @Test
    public void testDeviceTokenFlexibleRegistration_Succeeds() throws Exception {
        // Test with "deviceToken" key on "/api/driver/device-token" alias
        Map<String, String> payload = Map.of("deviceToken", "test-push-token-12345");

        mockMvc.perform(post("/api/driver/device-token")
                        .header("Authorization", "Bearer " + driverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.message", containsString("registered")));

        AppUser refreshed = appUserRepository.findById(driverUser.getId()).orElseThrow();
        assertEquals("test-push-token-12345", refreshed.getFcmToken());
    }
}
