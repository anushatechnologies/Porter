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
    void testVehicleCatalogExposesBothForBikeAutoAndPassengerOnlyForCab() throws Exception {
        mockMvc.perform(get("/api/vehicle-types?status=active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.vehicles[?(@.name == '2 Wheeler')].serviceType").value(hasItem("BOTH")))
                .andExpect(jsonPath("$.vehicles[?(@.name == '3 Wheeler / Auto')].serviceType").value(hasItem("BOTH")))
                .andExpect(jsonPath("$.vehicles[?(@.name == 'Cab')].serviceType").value(hasItem("PASSENGER")))
                .andExpect(jsonPath("$.vehicles[?(@.name == 'Tata Ace')].serviceType").value(hasItem("GOODS")));
    }

    @Test
    void testDriverRegistrationDefaultsServiceType() throws Exception {
        // 1. Register 2-Wheeler driver -> serviceType BOTH
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
                .andExpect(jsonPath("$.serviceType").value("BOTH"));

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
    void testBikeDriverReceivesBothGoodsAndPassengerOrdersWithExplicitLabels() throws Exception {
        // Online 2-Wheeler driver
        Driver bikeDriver = new Driver();
        bikeDriver.setName("Ramesh Bike");
        bikeDriver.setPhone("9111111111");
        bikeDriver.setEmail("rameshbike@example.com");
        bikeDriver.setVehicle("2 Wheeler");
        bikeDriver.setVehicleType("2 Wheeler");
        bikeDriver.setVehicleNumber("KA-01-BK-9999");
        bikeDriver.setStatus("online");
        bikeDriver.setKyc("approved");
        bikeDriver.setLatitude(12.9352);
        bikeDriver.setLongitude(77.6245);
        bikeDriver.setWalletBalance(500.0);
        bikeDriver.setServiceType("BOTH");
        bikeDriver = driverRepository.save(bikeDriver);

        // Online Cab driver (Passenger only)
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
        aceDriver.setServiceType("GOODS");
        aceDriver = driverRepository.save(aceDriver);

        // Case A: Book 2-Wheeler Goods order
        String goodsBookingId = "BK-GOODS-2W-1";
        Order goodsOrder = new Order();
        goodsOrder.setBookingId(goodsBookingId);
        goodsOrder.setServiceName("2 Wheeler");
        goodsOrder.setGoodsCategory("Books and Documents");
        goodsOrder.setServiceType("GOODS");
        goodsOrder.setAmount(100.0);
        goodsOrder.setDistanceKm(4.0);
        goodsOrder.setPickupLat(12.9352);
        goodsOrder.setPickupLng(77.6245);
        goodsOrder.setStatus("searching");
        orderRepository.save(goodsOrder);

        autoAssignmentService.startAutoAssignment(goodsBookingId);
        Thread.sleep(600);

        // 2-Wheeler driver should receive offer, Cab & Ace drivers should NOT
        List<DriverOfferResponse> bikeOffers1 = driverOfferService.getActiveOffersForDriver(bikeDriver.getId());
        List<DriverOfferResponse> cabOffers1 = driverOfferService.getActiveOffersForDriver(cabDriver.getId());
        List<DriverOfferResponse> aceOffers1 = driverOfferService.getActiveOffersForDriver(aceDriver.getId());

        assertEquals(1, bikeOffers1.size(), "Bike driver must receive 2-Wheeler goods offer");
        assertEquals(0, cabOffers1.size(), "Cab driver must NOT receive 2-Wheeler goods offer");
        assertEquals(0, aceOffers1.size(), "Tata Ace driver must NOT receive 2-Wheeler goods offer");
        assertEquals("GOODS", bikeOffers1.get(0).getServiceType());
        assertTrue(bikeOffers1.get(0).getServiceLabel().contains("Goods Delivery"));

        // Case B: Book 2-Wheeler Passenger ride (Bike Taxi)
        String passBookingId = "BK-PASS-2W-2";
        Order passOrder = new Order();
        passOrder.setBookingId(passBookingId);
        passOrder.setServiceName("2 Wheeler");
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

        List<DriverOfferResponse> bikeOffers2 = driverOfferService.getActiveOffersForDriver(bikeDriver.getId());
        assertEquals(2, bikeOffers2.size(), "Bike driver must receive both goods and passenger offers");

        DriverOfferResponse passengerOffer = bikeOffers2.stream()
                .filter(o -> passBookingId.equals(o.getBookingId()))
                .findFirst().orElseThrow();
        assertEquals("PASSENGER", passengerOffer.getServiceType());
        assertEquals(1, passengerOffer.getPassengerCount());
        assertEquals("Passenger Ride (1 Rider)", passengerOffer.getServiceLabel());

        // Case C: Book Cab Passenger ride
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
        assertEquals("Passenger Ride (Cab)", cabOffers3.get(0).getServiceLabel());
    }
}
