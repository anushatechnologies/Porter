package com.anushaporter.backend;

import com.anushaporter.backend.model.Driver;
import com.anushaporter.backend.model.Order;
import com.anushaporter.backend.model.ServiceableArea;
import com.anushaporter.backend.repository.DriverRepository;
import com.anushaporter.backend.repository.ServiceableAreaRepository;
import com.anushaporter.backend.service.DriverEligibilityService;
import com.anushaporter.backend.service.ServiceableAreaService;
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

import java.util.*;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = BackendApplication.class)
@Import(VehicleMatchingAndServiceableAreaIntegrationTest.TestConfig.class)
public class VehicleMatchingAndServiceableAreaIntegrationTest {

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
    private DriverEligibilityService driverEligibilityService;

    @Autowired
    private ServiceableAreaService serviceableAreaService;

    @Autowired
    private ServiceableAreaRepository serviceableAreaRepository;

    @Autowired
    private DriverRepository driverRepository;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private com.anushaporter.backend.repository.AppUserRepository appUserRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private String adminToken;

    @BeforeEach
    public void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        serviceableAreaRepository.deleteAll();
        driverRepository.deleteAll();
        appUserRepository.deleteAll();

        com.anushaporter.backend.model.AppUser adminUser = new com.anushaporter.backend.model.AppUser();
        adminUser.setEmail("admin.test@anushaporter.com");
        adminUser.setRole("Admin");
        adminUser.setName("Admin User");
        appUserRepository.save(adminUser);
        adminToken = "Bearer " + jwtUtil.generateToken("admin.test@anushaporter.com");

        serviceableAreaService.seedDefaultServiceableAreas();
    }

    @Test
    public void testVehicleCategoryNormalization() {
        assertEquals("TWO_WHEELER", driverEligibilityService.normalizeVehicleCategory("2 Wheeler"));
        assertEquals("TWO_WHEELER", driverEligibilityService.normalizeVehicleCategory("Bike"));
        assertEquals("TWO_WHEELER", driverEligibilityService.normalizeVehicleCategory("two_wheeler"));
        assertEquals("TWO_WHEELER", driverEligibilityService.normalizeVehicleCategory("scooter"));

        assertEquals("THREE_WHEELER", driverEligibilityService.normalizeVehicleCategory("3 Wheeler"));
        assertEquals("THREE_WHEELER", driverEligibilityService.normalizeVehicleCategory("3 Wheeler / Auto"));
        assertEquals("THREE_WHEELER", driverEligibilityService.normalizeVehicleCategory("auto_rickshaw"));

        assertEquals("TATA_ACE", driverEligibilityService.normalizeVehicleCategory("Tata Ace"));
        assertEquals("TATA_ACE", driverEligibilityService.normalizeVehicleCategory("tata_ace"));
        assertEquals("TATA_ACE", driverEligibilityService.normalizeVehicleCategory("Chota Hathi"));

        assertEquals("PICKUP_8FT", driverEligibilityService.normalizeVehicleCategory("Pickup 8ft"));
        assertEquals("PICKUP_8FT", driverEligibilityService.normalizeVehicleCategory("pickup"));

        assertEquals("TATA_407", driverEligibilityService.normalizeVehicleCategory("Tata 407"));
        assertEquals("TATA_407", driverEligibilityService.normalizeVehicleCategory("14ft Truck"));
    }

    @Test
    public void testTwoWheelerOrder_MatchesOnlyTwoWheelerDriver_RejectsOtherVehicles() {
        // Driver A: 2 Wheeler (Bike)
        Driver bikeDriver = new Driver();
        bikeDriver.setName("Bike Driver");
        bikeDriver.setPhone("9100000001");
        bikeDriver.setStatus("online");
        bikeDriver.setVehicleType("2 Wheeler");
        bikeDriver.setVehicle("Bike");
        bikeDriver.setKyc("approved");
        bikeDriver = driverRepository.save(bikeDriver);

        // Driver B: 3 Wheeler (Auto)
        Driver autoDriver = new Driver();
        autoDriver.setName("Auto Driver");
        autoDriver.setPhone("9100000002");
        autoDriver.setStatus("online");
        autoDriver.setVehicleType("3 Wheeler");
        autoDriver.setVehicle("Auto");
        autoDriver.setKyc("approved");
        autoDriver = driverRepository.save(autoDriver);

        // Driver C: Tata Ace
        Driver aceDriver = new Driver();
        aceDriver.setName("Ace Driver");
        aceDriver.setPhone("9100000003");
        aceDriver.setStatus("online");
        aceDriver.setVehicleType("Tata Ace");
        aceDriver.setVehicle("Tata Ace");
        aceDriver.setKyc("approved");
        aceDriver = driverRepository.save(aceDriver);

        // Order 1: Requesting 2-Wheeler
        Order order2W = new Order();
        order2W.setBookingId("ORD-2W-001");
        order2W.setServiceName("2 Wheeler");
        order2W.setPickupLat(17.4486);
        order2W.setPickupLng(78.3808);

        // 2-Wheeler driver must be ELIGIBLE
        assertTrue(driverEligibilityService.isEligible(bikeDriver, order2W, Collections.emptySet()),
                "2-Wheeler driver must be eligible for 2-Wheeler order");

        // 3-Wheeler and Tata Ace drivers must NOT be eligible
        assertFalse(driverEligibilityService.isEligible(autoDriver, order2W, Collections.emptySet()),
                "3-Wheeler driver must NOT receive 2-Wheeler order");
        assertFalse(driverEligibilityService.isEligible(aceDriver, order2W, Collections.emptySet()),
                "Tata Ace driver must NOT receive 2-Wheeler order");
    }

    @Test
    public void testThreeWheelerOrder_MatchesOnlyThreeWheelerDriver() {
        Driver bikeDriver = new Driver();
        bikeDriver.setName("Bike Driver");
        bikeDriver.setPhone("9100000011");
        bikeDriver.setStatus("online");
        bikeDriver.setVehicleType("2 Wheeler");
        bikeDriver.setKyc("approved");
        bikeDriver = driverRepository.save(bikeDriver);

        Driver autoDriver = new Driver();
        autoDriver.setName("Auto Driver");
        autoDriver.setPhone("9100000012");
        autoDriver.setStatus("online");
        autoDriver.setVehicleType("3 Wheeler");
        autoDriver.setKyc("approved");
        autoDriver = driverRepository.save(autoDriver);

        Order order3W = new Order();
        order3W.setBookingId("ORD-3W-001");
        order3W.setServiceName("3 Wheeler / Auto");

        assertTrue(driverEligibilityService.isEligible(autoDriver, order3W, Collections.emptySet()),
                "3-Wheeler driver must be eligible for 3-Wheeler order");
        assertFalse(driverEligibilityService.isEligible(bikeDriver, order3W, Collections.emptySet()),
                "2-Wheeler driver must NOT receive 3-Wheeler order");
    }

    @Test
    public void testAdminServiceableAreas_GetAndBulkUpdate() throws Exception {
        // 1. Admin gets list of areas for Hyderabad
        mockMvc.perform(get("/api/admin/serviceable-areas?city=Hyderabad")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.city", is("Hyderabad")))
                .andExpect(jsonPath("$.count", greaterThan(5)));

        // 2. Admin bulk-updates: only 500081 (Hitech City) and 500086 (Madhapur) should be active
        Map<String, Object> bulkPayload = new HashMap<>();
        bulkPayload.put("city", "Hyderabad");
        bulkPayload.put("activePincodes", Arrays.asList("500081", "500086"));

        mockMvc.perform(post("/api/admin/serviceable-areas/bulk-update")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bulkPayload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.enabledCount", is(2)));

        // Verify active areas endpoint only returns the 2 enabled areas
        mockMvc.perform(get("/api/serviceable-areas/active?city=Hyderabad"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.count", is(2)));
    }

    @Test
    public void testLocationValidation_ApprovesApprovedPincode_RejectsUnapprovedLocation() throws Exception {
        // Test Approved Pincode (500081 - Hitech City)
        Map<String, Object> approvedPayload = new HashMap<>();
        approvedPayload.put("pincode", "500081");
        approvedPayload.put("city", "Hyderabad");

        mockMvc.perform(post("/api/location/validate-serviceable")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(approvedPayload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.serviceable", is(true)))
                .andExpect(jsonPath("$.areaName", is("Hitech City")));

        // Test Unapproved Pincode (501218 - Shamshabad Airport Zone is inactive)
        Map<String, Object> unapprovedPayload = new HashMap<>();
        unapprovedPayload.put("pincode", "501218");
        unapprovedPayload.put("city", "Hyderabad");

        mockMvc.perform(post("/api/location/validate-serviceable")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(unapprovedPayload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.serviceable", is(false)))
                .andExpect(jsonPath("$.message", containsString("Porter service is not currently available")))
                .andExpect(jsonPath("$.approvedAreas", notNullValue()));
    }

    @Test
    public void testDriverProfileUpdate_VehicleChangeAndAvailableOrdersFiltering() throws Exception {
        // Create user & driver with 2 Wheeler
        String phone = "9100000099";
        com.anushaporter.backend.model.AppUser driverUser = new com.anushaporter.backend.model.AppUser();
        driverUser.setPhone(phone);
        driverUser.setName("Switching Driver");
        driverUser.setRole("Driver");
        appUserRepository.save(driverUser);
        String token = "Bearer " + jwtUtil.generateToken(phone);

        Driver driver = new Driver();
        driver.setName("Switching Driver");
        driver.setPhone(phone);
        driver.setStatus("online");
        driver.setVehicleType("2 Wheeler");
        driver.setVehicle("2 Wheeler");
        driver.setServiceType("OUR_SERVICES");
        driver.setKyc("approved");
        driver = driverRepository.save(driver);

        // Place 2 orders: 1 for 2 Wheeler and 1 for 3 Wheeler
        Order order2W = new Order();
        order2W.setBookingId("ORD-SW-2W");
        order2W.setServiceName("2 Wheeler");
        order2W.setStatus("searching");
        order2W.setPickupAddress("Hitech City");
        order2W.setDropAddress("Madhapur");
        order2W.setCreatedAt(java.time.LocalDateTime.now());
        driverEligibilityService = webApplicationContext.getBean(DriverEligibilityService.class);
        com.anushaporter.backend.repository.OrderRepository orderRepo = webApplicationContext.getBean(com.anushaporter.backend.repository.OrderRepository.class);
        orderRepo.save(order2W);

        Order order3W = new Order();
        order3W.setBookingId("ORD-SW-3W");
        order3W.setServiceName("3 Wheeler / Auto");
        order3W.setStatus("searching");
        order3W.setPickupAddress("Gachibowli");
        order3W.setDropAddress("Kondapur");
        order3W.setCreatedAt(java.time.LocalDateTime.now());
        orderRepo.save(order3W);

        // 1. Initial State: Driver is 2 Wheeler -> available orders must contain ONLY 2 Wheeler order
        mockMvc.perform(get("/api/driver/orders/available")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.count", is(1)))
                .andExpect(jsonPath("$.orders[0].bookingId", is("ORD-SW-2W")));

        // 2. Driver edits profile and updates vehicle to 3 Wheeler
        Map<String, Object> updatePayload = Map.of(
                "vehicleType", "3 Wheeler / Auto",
                "vehicle", "3 Wheeler / Auto",
                "serviceType", "OUR_SERVICES"
        );

        mockMvc.perform(put("/api/driver/profile")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updatePayload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)));

        // 3. After Update: Driver is 3 Wheeler -> available orders must now contain ONLY 3 Wheeler order!
        mockMvc.perform(get("/api/driver/orders/available")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.count", is(1)))
                .andExpect(jsonPath("$.orders[0].bookingId", is("ORD-SW-3W")));

        // Clean up orders
        orderRepo.delete(order2W);
        orderRepo.delete(order3W);
    }
}
