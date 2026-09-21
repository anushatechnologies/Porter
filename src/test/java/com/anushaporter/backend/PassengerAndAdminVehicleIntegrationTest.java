package com.anushaporter.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.anushaporter.backend.model.*;
import com.anushaporter.backend.repository.*;
import com.anushaporter.backend.service.AutoAssignmentService;
import com.anushaporter.backend.service.PassengerBookingService;
import com.anushaporter.backend.util.JwtUtil;
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

import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = BackendApplication.class)
@Import(PassengerAndAdminVehicleIntegrationTest.TestConfig.class)
public class PassengerAndAdminVehicleIntegrationTest {

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
    private OrderRepository orderRepository;

    @Autowired
    private DriverOfferRepository driverOfferRepository;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private AutoAssignmentService autoAssignmentService;

    @Autowired
    private PassengerBookingRepository passengerBookingRepository;

    @Autowired
    private PassengerVehicleCategoryRepository passengerVehicleCategoryRepository;

    @Autowired
    private VehicleTypeRepository vehicleTypeRepository;

    @Autowired
    private JwtUtil jwtUtil;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        driverOfferRepository.deleteAll();
        orderRepository.deleteAll();
        driverRepository.deleteAll();
        appUserRepository.deleteAll();
        autoAssignmentService.setTierDurationSeconds(1);

        if (vehicleTypeRepository.count() == 0) {
            VehicleType v = new VehicleType();
            v.setId("1");
            v.setName("2 Wheeler");
            v.setType("two_wheeler");
            v.setStatus("active");
            v.setServiceType("OUR_SERVICES");
            vehicleTypeRepository.save(v);
        }

        // Ensure category "AUTO" exists in passenger vehicle categories
        if (passengerVehicleCategoryRepository.findByCategoryCode("AUTO").isEmpty()) {
            PassengerVehicleCategory cat = PassengerVehicleCategory.builder()
                    .categoryCode("AUTO")
                    .displayName("Auto Rickshaw")
                    .passengerCapacity(3)
                    .luggageCapacity(2)
                    .baseFare(new java.math.BigDecimal("50.00"))
                    .minimumKm(new java.math.BigDecimal("1.5"))
                    .perKmRate(new java.math.BigDecimal("15.00"))
                    .minimumFare(new java.math.BigDecimal("50.00"))
                    .active(true)
                    .displayOrder(1)
                    .build();
            passengerVehicleCategoryRepository.save(cat);
        }
    }

    @Test
    void testAdminVehiclesEndpointReturnsVehicleCatalog() throws Exception {
        // Both /api/admin/vehicles and /api/admin/vehicle-types must return the vehicle catalog
        mockMvc.perform(get("/api/admin/vehicles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.vehicles", not(empty())));

        mockMvc.perform(get("/api/admin/vehicle-types"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.vehicles", not(empty())));
    }

    @Test
    void testPassengerAutoRideDispatchesToAutoDriverAndShowsInAvailableOrders() throws Exception {
        // 1. Setup an active online Auto driver
        Driver autoDriver = new Driver();
        autoDriver.setName("Ramesh Auto");
        autoDriver.setPhone("9988776655");
        autoDriver.setVehicle("3 Wheeler / Auto");
        autoDriver.setVehicleType("3 Wheeler / Auto");
        autoDriver.setVehicleNumber("TS09AU1234");
        autoDriver.setStatus("online");
        autoDriver.setKyc("approved");
        autoDriver.setVerificationStatus("approved");
        autoDriver.setLatitude(17.4485);
        autoDriver.setLongitude(78.3905);
        final Driver savedAutoDriver = driverRepository.save(autoDriver);

        // 2. Setup an active online Tata Ace driver (Freight truck)
        Driver truckDriver = new Driver();
        truckDriver.setName("Suresh Ace");
        truckDriver.setPhone("9988776656");
        truckDriver.setVehicle("Tata Ace");
        truckDriver.setVehicleType("Tata Ace");
        truckDriver.setVehicleNumber("TS09TC5678");
        truckDriver.setStatus("online");
        truckDriver.setKyc("approved");
        truckDriver.setVerificationStatus("approved");
        truckDriver.setLatitude(17.4487);
        truckDriver.setLongitude(78.3907);
        final Driver savedTruckDriver = driverRepository.save(truckDriver);

        // 3. Create a passenger booking with vehicle category "AUTO"
        String payload = "{"
                + "\"serviceType\":\"ONE_WAY\","
                + "\"vehicleCategoryCode\":\"AUTO\","
                + "\"pickupAddress\":\"Madhapur, Hyderabad\","
                + "\"pickupLat\":17.4486,"
                + "\"pickupLng\":78.3908,"
                + "\"dropAddress\":\"Hitech City, Hyderabad\","
                + "\"dropLat\":17.4500,"
                + "\"dropLng\":78.3920,"
                + "\"customerName\":\"John Doe\","
                + "\"customerPhone\":\"9123456789\","
                + "\"passengerCount\":2"
                + "}";

        String responseStr = mockMvc.perform(post("/api/passenger/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.bookingNumber").exists())
                .andReturn().getResponse().getContentAsString();

        String bookingNumber = objectMapper.readTree(responseStr).get("bookingNumber").asText();

        assertNotNull(bookingNumber);
        assertTrue(bookingNumber.startsWith("AP-CAR-"));

        // Wait brief moment for async auto-assignment
        Thread.sleep(600);

        // 4. Verify Auto driver received an offer, and freight truck driver did NOT
        List<DriverOffer> offers = driverOfferRepository.findByBookingId(bookingNumber);
        assertFalse(offers.isEmpty(), "AutoAssignment should have created offers for compatible Auto driver");
        assertTrue(offers.stream().anyMatch(o -> o.getDriverId().equals(savedAutoDriver.getId())),
                "Auto driver must receive the passenger auto offer");
        assertFalse(offers.stream().anyMatch(o -> o.getDriverId().equals(savedTruckDriver.getId())),
                "Tata Ace freight truck driver must NOT receive passenger auto offer");

        // 5. Verify Auto driver polling /driver/orders/available sees the ride
        String autoDriverToken = "Bearer " + jwtUtil.generateToken("9988776655");
        mockMvc.perform(get("/api/driver/orders/available?lat=17.4485&lng=78.3905")
                        .header("Authorization", autoDriverToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.orders[?(@.bookingId == '" + bookingNumber + "')]").exists());

        // 6. Verify Tata Ace truck driver polling /driver/orders/available does NOT see the passenger ride
        String truckDriverToken = "Bearer " + jwtUtil.generateToken("9988776656");
        mockMvc.perform(get("/api/driver/orders/available?lat=17.4487&lng=78.3907")
                        .header("Authorization", truckDriverToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.orders[?(@.bookingId == '" + bookingNumber + "')]").doesNotExist());
    }

    @Test
    void testPassengerBikeTaxiDispatchesToBikeDriver() throws Exception {
        // 1. Setup an active online 2-Wheeler / Bike driver
        Driver bikeDriver = new Driver();
        bikeDriver.setName("Vikram Bike");
        bikeDriver.setPhone("9988776657");
        bikeDriver.setVehicle("2 Wheeler");
        bikeDriver.setVehicleType("2 Wheeler");
        bikeDriver.setVehicleNumber("TS09BK9999");
        bikeDriver.setStatus("online");
        bikeDriver.setKyc("approved");
        bikeDriver.setVerificationStatus("approved");
        bikeDriver.setLatitude(17.4485);
        bikeDriver.setLongitude(78.3905);
        final Driver savedBikeDriver = driverRepository.save(bikeDriver);

        // Ensure category "BIKE" exists
        if (passengerVehicleCategoryRepository.findByCategoryCode("BIKE").isEmpty()) {
            PassengerVehicleCategory cat = PassengerVehicleCategory.builder()
                    .categoryCode("BIKE")
                    .displayName("Bike Taxi")
                    .passengerCapacity(1)
                    .luggageCapacity(1)
                    .baseFare(new java.math.BigDecimal("30.00"))
                    .minimumKm(new java.math.BigDecimal("1.0"))
                    .perKmRate(new java.math.BigDecimal("10.00"))
                    .minimumFare(new java.math.BigDecimal("30.00"))
                    .active(true)
                    .displayOrder(0)
                    .build();
            passengerVehicleCategoryRepository.save(cat);
        }

        // 2. Create a passenger booking with vehicle category "BIKE"
        String payload = "{"
                + "\"serviceType\":\"ONE_WAY\","
                + "\"vehicleCategoryCode\":\"BIKE\","
                + "\"pickupAddress\":\"Kondapur, Hyderabad\","
                + "\"pickupLat\":17.4486,"
                + "\"pickupLng\":78.3908,"
                + "\"dropAddress\":\"Gachibowli, Hyderabad\","
                + "\"dropLat\":17.4500,"
                + "\"dropLng\":78.3920,"
                + "\"customerName\":\"Jane Rider\","
                + "\"customerPhone\":\"9123456780\","
                + "\"passengerCount\":1"
                + "}";

        String responseStr = mockMvc.perform(post("/api/passenger/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.bookingNumber").exists())
                .andReturn().getResponse().getContentAsString();

        String bookingNumber = objectMapper.readTree(responseStr).get("bookingNumber").asText();

        Thread.sleep(600);

        // 3. Verify Bike driver received the offer
        List<DriverOffer> offers = driverOfferRepository.findByBookingId(bookingNumber);
        assertFalse(offers.isEmpty());
        assertTrue(offers.stream().anyMatch(o -> o.getDriverId().equals(savedBikeDriver.getId())));

        // 4. Verify Bike driver polling /driver/orders/available sees the ride
        String bikeDriverToken = "Bearer " + jwtUtil.generateToken("9988776657");
        mockMvc.perform(get("/api/driver/orders/available?lat=17.4485&lng=78.3905")
                        .header("Authorization", bikeDriverToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.orders[?(@.bookingId == '" + bookingNumber + "')]").exists());
    }
}
