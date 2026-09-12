package com.anushaporter.backend;

import com.anushaporter.backend.dto.DriverOfferResponse;
import com.anushaporter.backend.model.*;
import com.anushaporter.backend.repository.*;
import com.anushaporter.backend.service.AutoAssignmentService;
import com.anushaporter.backend.service.DriverOfferService;
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

import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = BackendApplication.class)
@Import(PassengerAndGoodsUnifiedDriverDispatchTest.TestConfig.class)
public class PassengerAndGoodsUnifiedDriverDispatchTest {

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
    private NotificationRepository notificationRepository;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private DriverOfferService driverOfferService;

    @Autowired
    private AutoAssignmentService autoAssignmentService;

    @Autowired
    private JwtUtil jwtUtil;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();

        driverOfferRepository.deleteAll();
        notificationRepository.deleteAll();
        orderRepository.deleteAll();
        driverRepository.deleteAll();
        appUserRepository.deleteAll();

        if (autoAssignmentService != null) {
            autoAssignmentService.setTierDurationSeconds(30);
        }
    }

    @Test
    void testVehicleCatalogExposesOurServicesAndPassengerDistinctly() throws Exception {
        mockMvc.perform(get("/api/vehicle-types?status=active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.vehicles[?(@.name == '2 Wheeler')].serviceType").value(hasItem("OUR_SERVICES")))
                .andExpect(jsonPath("$.vehicles[?(@.name == '3 Wheeler / Auto')].serviceType").value(hasItem("OUR_SERVICES")))
                .andExpect(jsonPath("$.vehicles[?(@.name == 'Cab')].serviceType").value(hasItem("PASSENGER")))
                .andExpect(jsonPath("$.vehicles[?(@.name == 'Tata Ace')].serviceType").value(hasItem("OUR_SERVICES")));
    }

    @Test
    void testDriverRegistrationDefaultsServiceType() throws Exception {
        // 1. Register 2-Wheeler driver -> serviceType OUR_SERVICES
        AppUser bikeUser = new AppUser();
        bikeUser.setName("Bike Driver");
        bikeUser.setPhone("9888811111");
        bikeUser.setEmail("bikedriver@example.com");
        bikeUser.setRole("Driver");
        appUserRepository.save(bikeUser);
        String bikeToken = jwtUtil.generateToken("bikedriver@example.com");

        mockMvc.perform(post("/api/drivers/register")
                        .header("Authorization", "Bearer " + bikeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Bike Driver\",\"vehicle\":\"2 Wheeler\",\"vehicleNumber\":\"KA01BK1111\",\"step\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.serviceType").value("OUR_SERVICES"));

        // 2. Register Cab driver -> serviceType PASSENGER
        AppUser cabUser = new AppUser();
        cabUser.setName("Cab Driver");
        cabUser.setPhone("9888822222");
        cabUser.setEmail("cabdriver@example.com");
        cabUser.setRole("Driver");
        appUserRepository.save(cabUser);
        String cabToken = jwtUtil.generateToken("cabdriver@example.com");

        mockMvc.perform(post("/api/drivers/register")
                        .header("Authorization", "Bearer " + cabToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Cab Driver\",\"vehicle\":\"Cab\",\"vehicleNumber\":\"KA02CAB2222\",\"step\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.serviceType").value("PASSENGER"));
    }

    @Test
    void testSeparateGoodsAndPassengerDispatch_NoCrossDispatchWhenVehicleTypeSimilar() throws Exception {
        // Online 2-Wheeler driver for OUR_SERVICES (Goods)
        Driver goodsBikeDriver = new Driver();
        goodsBikeDriver.setName("Ramesh Goods Bike");
        goodsBikeDriver.setPhone("9111111111");
        goodsBikeDriver.setEmail("rameshbike@example.com");
        goodsBikeDriver.setVehicle("2 Wheeler");
        goodsBikeDriver.setVehicleType("2 Wheeler");
        goodsBikeDriver.setVehicleNumber("KA-01-BK-9999");
        goodsBikeDriver.setStatus("online");
        goodsBikeDriver.setKyc("approved");
        goodsBikeDriver.setLatitude(12.9352);
        goodsBikeDriver.setLongitude(77.6245);
        goodsBikeDriver.setWalletBalance(500.0);
        goodsBikeDriver.setServiceType("OUR_SERVICES");
        goodsBikeDriver = driverRepository.save(goodsBikeDriver);

        // Online 2-Wheeler driver for PASSENGER (Bike Taxi)
        Driver passBikeDriver = new Driver();
        passBikeDriver.setName("Pooja Pass Bike");
        passBikeDriver.setPhone("9111111112");
        passBikeDriver.setEmail("poojapass@example.com");
        passBikeDriver.setVehicle("Bike Taxi");
        passBikeDriver.setVehicleType("Bike Taxi");
        passBikeDriver.setVehicleNumber("KA-01-PT-1111");
        passBikeDriver.setStatus("online");
        passBikeDriver.setKyc("approved");
        passBikeDriver.setLatitude(12.9352);
        passBikeDriver.setLongitude(77.6245);
        passBikeDriver.setWalletBalance(500.0);
        passBikeDriver.setServiceType("PASSENGER");
        passBikeDriver = driverRepository.save(passBikeDriver);

        // Online Cab driver (Passenger)
        Driver cabDriver = new Driver();
        cabDriver.setName("Suresh Cab");
        cabDriver.setPhone("9222222222");
        cabDriver.setEmail("sureshcab@example.com");
        cabDriver.setVehicle("Cab");
        cabDriver.setVehicleType("Cab");
        cabDriver.setVehicleNumber("KA-02-CB-8888");
        cabDriver.setStatus("online");
        cabDriver.setKyc("approved");
        cabDriver.setLatitude(12.9352);
        cabDriver.setLongitude(77.6245);
        cabDriver.setWalletBalance(500.0);
        cabDriver.setServiceType("PASSENGER");
        cabDriver = driverRepository.save(cabDriver);

        // Online Tata Ace driver (Goods only)
        Driver aceDriver = new Driver();
        aceDriver.setName("Mahesh Ace");
        aceDriver.setPhone("9333333333");
        aceDriver.setEmail("maheshace@example.com");
        aceDriver.setVehicle("Tata Ace");
        aceDriver.setVehicleType("Tata Ace");
        aceDriver.setVehicleNumber("KA-03-AC-7777");
        aceDriver.setStatus("online");
        aceDriver.setKyc("approved");
        aceDriver.setLatitude(12.9352);
        aceDriver.setLongitude(77.6245);
        aceDriver.setWalletBalance(500.0);
        aceDriver.setServiceType("OUR_SERVICES");
        aceDriver = driverRepository.save(aceDriver);

        // Case A: Book 2-Wheeler Goods order -> Only Goods Bike driver must receive
        String goodsBookingId = "BK-GOODS-2W-1";
        Order goodsOrder = new Order();
        goodsOrder.setBookingId(goodsBookingId);
        goodsOrder.setServiceName("2 Wheeler");
        goodsOrder.setGoodsCategory("Books and Documents");
        goodsOrder.setServiceType("OUR_SERVICES");
        goodsOrder.setAmount(100.0);
        goodsOrder.setDistanceKm(4.0);
        goodsOrder.setPickupLat(12.9352);
        goodsOrder.setPickupLng(77.6245);
        goodsOrder.setStatus("searching");
        orderRepository.save(goodsOrder);

        autoAssignmentService.startAutoAssignment(goodsBookingId);
        Thread.sleep(600);

        List<DriverOfferResponse> goodsBikeOffers = driverOfferService.getActiveOffersForDriver(goodsBikeDriver.getId());
        List<DriverOfferResponse> passBikeOffers1 = driverOfferService.getActiveOffersForDriver(passBikeDriver.getId());
        List<DriverOfferResponse> cabOffers1 = driverOfferService.getActiveOffersForDriver(cabDriver.getId());
        List<DriverOfferResponse> aceOffers1 = driverOfferService.getActiveOffersForDriver(aceDriver.getId());

        assertEquals(1, goodsBikeOffers.size(), "Goods Bike driver must receive 2-Wheeler goods offer");
        assertEquals(0, passBikeOffers1.size(), "Passenger Bike Taxi driver must NOT receive Goods offer");
        assertEquals(0, cabOffers1.size(), "Cab driver must NOT receive 2-Wheeler goods offer");
        assertEquals(0, aceOffers1.size(), "Tata Ace driver must NOT receive 2-Wheeler goods offer");

        // Case B: Book Passenger ride (Bike Taxi) -> Only Passenger Bike driver must receive
        String passBookingId = "BK-PASS-2W-2";
        Order passOrder = new Order();
        passOrder.setBookingId(passBookingId);
        passOrder.setServiceName("Bike Taxi");
        passOrder.setServiceType("PASSENGER");
        passOrder.setPassengerCount(1);
        passOrder.setAmount(60.0);
        passOrder.setDistanceKm(3.0);
        passOrder.setPickupLat(12.9352);
        passOrder.setPickupLng(77.6245);
        passOrder.setStatus("searching");
        orderRepository.save(passOrder);

        autoAssignmentService.startAutoAssignment(passBookingId);
        Thread.sleep(600);

        List<DriverOfferResponse> passBikeOffers2 = driverOfferService.getActiveOffersForDriver(passBikeDriver.getId());
        assertEquals(1, passBikeOffers2.size(), "Passenger Bike Taxi driver must receive Passenger offer");
        assertEquals(passBookingId, passBikeOffers2.get(0).getBookingId());

        // Goods driver offer count must still be 1 (not received passenger ride)
        List<DriverOfferResponse> goodsBikeOffersAfter = driverOfferService.getActiveOffersForDriver(goodsBikeDriver.getId());
        assertEquals(1, goodsBikeOffersAfter.size(), "Goods driver must NOT receive Passenger ride");

        // Case C: Book Cab Passenger ride -> Only Cab driver receives
        String cabBookingId = "BK-PASS-CAB-3";
        Order cabOrder = new Order();
        cabOrder.setBookingId(cabBookingId);
        cabOrder.setServiceName("Cab");
        cabOrder.setServiceType("PASSENGER");
        cabOrder.setPassengerCount(4);
        cabOrder.setAmount(250.0);
        cabOrder.setDistanceKm(8.0);
        cabOrder.setPickupLat(12.9352);
        cabOrder.setPickupLng(77.6245);
        cabOrder.setStatus("searching");
        orderRepository.save(cabOrder);

        autoAssignmentService.startAutoAssignment(cabBookingId);
        Thread.sleep(600);

        List<DriverOfferResponse> cabOffers3 = driverOfferService.getActiveOffersForDriver(cabDriver.getId());
        assertEquals(1, cabOffers3.size(), "Cab driver must receive Cab passenger offer");
        assertEquals("PASSENGER", cabOffers3.get(0).getServiceType());
    }

    @Test
    void testPassengerStartTripWithValidAndInvalidOtp() throws Exception {
        Driver driver = new Driver();
        driver.setName("Test Driver");
        driver.setEmail("testdriver12@example.com");
        driver.setPhone("9999900001");
        driver.setVehicle("Cab");
        driver.setVehicleType("Cab");
        driver.setStatus("online");
        driver = driverRepository.save(driver);

        String driverToken = jwtUtil.generateToken(driver.getEmail());

        Order order = new Order();
        order.setBookingId("BK-PASS-TEST-1");
        order.setServiceType("PASSENGER");
        order.setStartOtp("4321");
        order.setDeliveryOtp("4321");
        order.setStatus("assigned");
        order.setDriverId(driver.getId().toString());
        order.setAmount(150.0);
        orderRepository.save(order);

        // 1. Wrong OTP -> should return 400 Bad Request
        mockMvc.perform(post("/api/driver/orders/BK-PASS-TEST-1/start-trip")
                        .header("Authorization", "Bearer " + driverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"otp\":\"9999\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(containsString("Incorrect Start Ride OTP")));

        // 2. Correct OTP -> should return 200 OK and status IN_TRANSIT
        mockMvc.perform(post("/api/driver/orders/BK-PASS-TEST-1/start-trip")
                        .header("Authorization", "Bearer " + driverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"otp\":\"4321\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.status").value("IN_TRANSIT"));

        Order updated = orderRepository.findByBookingId("BK-PASS-TEST-1").orElseThrow();
        assertEquals("in_transit", updated.getStatus().toLowerCase());
        assertTrue(Boolean.TRUE.equals(updated.getOtpVerified()));
    }

    @Test
    void testPassengerRideCompletionWithoutDropOffDeliveryOtp() throws Exception {
        Driver driver = new Driver();
        driver.setName("Passenger Driver");
        driver.setEmail("passdriver@example.com");
        driver.setPhone("9999900002");
        driver.setVehicle("2 Wheeler");
        driver.setVehicleType("2 Wheeler");
        driver.setStatus("online");
        driver = driverRepository.save(driver);

        String driverToken = jwtUtil.generateToken(driver.getEmail());

        Order order = new Order();
        order.setBookingId("BK-PASS-COMP-2");
        order.setServiceType("PASSENGER");
        order.setStartOtp("1234");
        order.setDeliveryOtp("1234");
        order.setStatus("in_transit");
        order.setOtpVerified(true);
        order.setDriverId(driver.getId().toString());
        order.setAmount(80.0);
        orderRepository.save(order);

        // At drop-off, driver completes via PUT /api/orders/{id}/status without sending deliveryOtp
        mockMvc.perform(put("/api/orders/BK-PASS-COMP-2/status")
                        .header("Authorization", "Bearer " + driverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"completed\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        Order completed = orderRepository.findByBookingId("BK-PASS-COMP-2").orElseThrow();
        assertEquals("completed", completed.getStatus().toLowerCase());
    }
}
