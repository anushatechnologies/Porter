package com.anushaporter.backend;

import com.anushaporter.backend.dto.DriverOfferResponse;
import com.anushaporter.backend.model.*;
import com.anushaporter.backend.repository.*;
import com.anushaporter.backend.service.DriverOfferService;
import com.anushaporter.backend.util.JwtUtil;
import com.fasterxml.jackson.databind.JsonNode;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import software.amazon.awssdk.services.s3.S3Client;

import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = BackendApplication.class)
@Import(DriverCoordinatesAndAddressIntegrationTest.TestConfig.class)
public class DriverCoordinatesAndAddressIntegrationTest {

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
    private OrderRepository orderRepository;

    @Autowired
    private DriverRepository driverRepository;

    @Autowired
    private PassengerBookingRepository passengerBookingRepository;

    @Autowired
    private DriverOfferRepository driverOfferRepository;

    @Autowired
    private DriverOfferService driverOfferService;

    @Autowired
    private JwtUtil jwtUtil;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        driverOfferRepository.deleteAll();
        orderRepository.deleteAll();
        passengerBookingRepository.deleteAll();
        driverRepository.deleteAll();
    }

    @Test
    void testOrderEntityCoordinatesAndAddressAccessors() throws Exception {
        Order order = new Order();
        order.setPickupAddress("Inorbit Mall, Hitech City");
        order.setDropAddress("Mindspace Building 12, Madhapur");
        order.setPickupLat(17.4198132);
        order.setPickupLng(78.3749805);
        order.setDropLat(17.4485001);
        order.setDropLng(78.3908002);

        // Verify transient getters
        assertEquals("Inorbit Mall, Hitech City", order.getPickup());
        assertEquals("Mindspace Building 12, Madhapur", order.getDrop());
        assertEquals(17.4198132, order.getPickupLatitude());
        assertEquals(78.3749805, order.getPickupLongitude());
        assertEquals(17.4485001, order.getDropLatitude());
        assertEquals(78.3908002, order.getDropLongitude());

        // Verify Jackson serialization produces both key naming conventions
        String json = objectMapper.writeValueAsString(order);
        JsonNode root = objectMapper.readTree(json);

        assertEquals("Inorbit Mall, Hitech City", root.path("pickupAddress").asText());
        assertEquals("Inorbit Mall, Hitech City", root.path("pickup").asText());
        assertEquals("Mindspace Building 12, Madhapur", root.path("dropAddress").asText());
        assertEquals("Mindspace Building 12, Madhapur", root.path("drop").asText());
        assertEquals(17.4198132, root.path("pickupLat").asDouble());
        assertEquals(17.4198132, root.path("pickupLatitude").asDouble());
        assertEquals(78.3749805, root.path("pickupLng").asDouble());
        assertEquals(78.3749805, root.path("pickupLongitude").asDouble());
        assertEquals(17.4485001, root.path("dropLat").asDouble());
        assertEquals(17.4485001, root.path("dropLatitude").asDouble());
        assertEquals(78.3908002, root.path("dropLng").asDouble());
        assertEquals(78.3908002, root.path("dropLongitude").asDouble());
    }

    @Test
    void testDriverOfferResponseFieldSerialization() throws Exception {
        DriverOfferResponse dto = new DriverOfferResponse();
        dto.setPickupAddress("Gachibowli Stadium");
        dto.setDropAddress("Jubilee Hills Checkpost");
        dto.setPickup("Gachibowli Stadium");
        dto.setDrop("Jubilee Hills Checkpost");
        dto.setPickupLat(17.4450);
        dto.setPickupLng(78.3480);
        dto.setPickupLatitude(17.4450);
        dto.setPickupLongitude(78.3480);
        dto.setDropLat(17.4280);
        dto.setDropLng(78.4070);
        dto.setDropLatitude(17.4280);
        dto.setDropLongitude(78.4070);

        String json = objectMapper.writeValueAsString(dto);
        JsonNode node = objectMapper.readTree(json);

        assertEquals("Gachibowli Stadium", node.path("pickup").asText());
        assertEquals("Gachibowli Stadium", node.path("pickupAddress").asText());
        assertEquals("Jubilee Hills Checkpost", node.path("drop").asText());
        assertEquals("Jubilee Hills Checkpost", node.path("dropAddress").asText());
        assertEquals(17.4450, node.path("pickupLat").asDouble());
        assertEquals(17.4450, node.path("pickupLatitude").asDouble());
        assertEquals(78.3480, node.path("pickupLng").asDouble());
        assertEquals(78.3480, node.path("pickupLongitude").asDouble());
        assertEquals(17.4280, node.path("dropLat").asDouble());
        assertEquals(17.4280, node.path("dropLatitude").asDouble());
        assertEquals(78.4070, node.path("dropLng").asDouble());
        assertEquals(78.4070, node.path("dropLongitude").asDouble());
    }

    @Test
    void testDriverActiveOrderPreservesHumanReadableAddressAndReturnsCoordinates() throws Exception {
        Driver driver = new Driver();
        driver.setName("Suresh Kumar");
        driver.setEmail("suresh.driver@example.com");
        driver.setPhone("9876500001");
        driver.setVehicleType("Tata Ace");
        driver.setStatus("online");
        driver.setVerificationStatus("VERIFIED");
        driver = driverRepository.save(driver);

        String token = jwtUtil.generateToken(driver.getEmail());

        Order order = new Order();
        order.setBookingId("ORD-ACTIVE-001");
        order.setUserEmail("customer@example.com");
        order.setDriverId(driver.getId().toString());
        order.setDriverPhone(driver.getPhone());
        order.setStatus("accepted");
        order.setPickupAddress("DLF Cyber City, Gachibowli");
        order.setDropAddress("Mindspace, Hitech City");
        order.setPickupLat(17.4485);
        order.setPickupLng(78.3741);
        order.setDropLat(17.4520);
        order.setDropLng(78.3800);
        order.setAmount(350.0);
        orderRepository.save(order);

        mockMvc.perform(get("/api/driver/active-order")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.order.hasActiveOrder", is(true)))
                // Verify the human-readable address is PRESERVED and NOT overwritten by raw coordinates
                .andExpect(jsonPath("$.order.pickup", is("DLF Cyber City, Gachibowli")))
                .andExpect(jsonPath("$.order.drop", is("Mindspace, Hitech City")))
                .andExpect(jsonPath("$.order.pickupAddress", is("DLF Cyber City, Gachibowli")))
                .andExpect(jsonPath("$.order.dropAddress", is("Mindspace, Hitech City")))
                // Verify both coordinate naming schemes are populated
                .andExpect(jsonPath("$.order.pickupLat", is(17.4485)))
                .andExpect(jsonPath("$.order.pickupLng", is(78.3741)))
                .andExpect(jsonPath("$.order.dropLat", is(17.4520)))
                .andExpect(jsonPath("$.order.dropLng", is(78.3800)))
                .andExpect(jsonPath("$.order.pickupLatitude", is(17.4485)))
                .andExpect(jsonPath("$.order.pickupLongitude", is(78.3741)))
                .andExpect(jsonPath("$.order.dropLatitude", is(17.4520)))
                .andExpect(jsonPath("$.order.dropLongitude", is(78.3800)));
    }

    @Test
    void testDriverActiveOrderSyncsFromPassengerBookingWhenOrderCoordinatesMissing() throws Exception {
        Driver driver = new Driver();
        driver.setName("Mahesh Babu");
        driver.setEmail("mahesh.driver@example.com");
        driver.setPhone("9876500002");
        driver.setVehicleType("Auto Rickshaw");
        driver.setStatus("online");
        driver.setVerificationStatus("VERIFIED");
        driver = driverRepository.save(driver);

        String token = jwtUtil.generateToken(driver.getEmail());

        PassengerBooking pb = new PassengerBooking();
        pb.setBookingNumber("PB-SYNC-COORD-001");
        pb.setCustomerPhone("9988776655");
        pb.setServiceType("ONE_WAY");
        pb.setVehicleCategoryCode("SEDAN");
        pb.setPassengerCount(1);
        pb.setStatus(PassengerBookingStatus.DRIVER_ASSIGNED);
        pb.setPickupAddress("Banjara Hills Road No 12");
        pb.setDropAddress("Secunderabad Station");
        pb.setPickupLatitude(17.4150);
        pb.setPickupLongitude(78.4350);
        pb.setDropLatitude(17.4344);
        pb.setDropLongitude(78.5013);
        pb.setPricingVersionId("v1");
        passengerBookingRepository.save(pb);

        // Order created with missing coordinates
        Order order = new Order();
        order.setBookingId("PB-SYNC-COORD-001");
        order.setDriverId(driver.getId().toString());
        order.setDriverEmail(driver.getEmail());
        order.setStatus("in_progress");
        order.setPickupAddress("Banjara Hills Road No 12");
        order.setDropAddress("Secunderabad Station");
        order.setPickupLat(null);
        order.setPickupLng(null);
        order.setDropLat(null);
        order.setDropLng(null);
        orderRepository.save(order);

        mockMvc.perform(get("/api/driver/active-order")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.order.hasActiveOrder", is(true)))
                // Verify coordinates were dynamically synced from PassengerBooking
                .andExpect(jsonPath("$.order.pickupLatitude", is(17.4150)))
                .andExpect(jsonPath("$.order.pickupLongitude", is(78.4350)))
                .andExpect(jsonPath("$.order.dropLatitude", is(17.4344)))
                .andExpect(jsonPath("$.order.dropLongitude", is(78.5013)))
                .andExpect(jsonPath("$.order.pickup", is("Banjara Hills Road No 12")))
                .andExpect(jsonPath("$.order.drop", is("Secunderabad Station")));
    }

    @Test
    void testDriverAvailableOrdersReturnsAddressesAndCoordinatesWithoutHardcodedFallbacks() throws Exception {
        Driver driver = new Driver();
        driver.setName("Kiran V");
        driver.setEmail("kiran.driver@example.com");
        driver.setPhone("9876500003");
        driver.setVehicleType("Tata Ace");
        driver.setStatus("online");
        driver.setVerificationStatus("VERIFIED");
        driver.setLatitude(17.4200);
        driver.setLongitude(78.3800);
        driver = driverRepository.save(driver);

        String token = jwtUtil.generateToken(driver.getEmail());

        Order availableOrder = new Order();
        availableOrder.setBookingId("ORD-AVAIL-001");
        availableOrder.setStatus("searching");
        availableOrder.setPickupAddress("GVK One Mall, Banjara Hills");
        availableOrder.setDropAddress("KIMS Hospital, Secunderabad");
        availableOrder.setPickupLat(17.4225);
        availableOrder.setPickupLng(78.4480);
        availableOrder.setDropLat(17.4360);
        availableOrder.setDropLng(78.4870);
        availableOrder.setAmount(420.0);
        availableOrder.setServiceName("Tata Ace");
        availableOrder.setServiceType("GOODS");
        availableOrder.setCreatedAt(LocalDateTime.now());
        orderRepository.save(availableOrder);

        mockMvc.perform(get("/api/driver/available-orders?lat=17.4200&lng=78.3800&radiusKm=25")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.orders[0].pickup", is("GVK One Mall, Banjara Hills")))
                .andExpect(jsonPath("$.orders[0].drop", is("KIMS Hospital, Secunderabad")))
                .andExpect(jsonPath("$.orders[0].pickupAddress", is("GVK One Mall, Banjara Hills")))
                .andExpect(jsonPath("$.orders[0].dropAddress", is("KIMS Hospital, Secunderabad")))
                // Ensure real order coordinates are returned, NOT hardcoded 17.4483
                .andExpect(jsonPath("$.orders[0].pickupLat", is(17.4225)))
                .andExpect(jsonPath("$.orders[0].pickupLng", is(78.4480)))
                .andExpect(jsonPath("$.orders[0].pickupLatitude", is(17.4225)))
                .andExpect(jsonPath("$.orders[0].pickupLongitude", is(78.4480)))
                .andExpect(jsonPath("$.orders[0].dropLatitude", is(17.4360)))
                .andExpect(jsonPath("$.orders[0].dropLongitude", is(78.4870)));
    }

    @Test
    void testDriverOfferServiceProvidesBothCoordinateConventions() {
        Driver driver = new Driver();
        driver.setName("Ramesh");
        driver.setEmail("ramesh.test@example.com");
        driver.setPhone("9876500004");
        driver.setVehicleType("2 Wheeler");
        driver.setStatus("online");
        driver.setVerificationStatus("VERIFIED");
        driver = driverRepository.save(driver);

        Order order = new Order();
        order.setBookingId("ORD-OFFER-001");
        order.setStatus("searching");
        order.setPickupAddress("IKEA Hyderabad, HITEC City");
        order.setDropAddress("Financial District, Nanakramguda");
        order.setPickupLat(17.4410);
        order.setPickupLng(78.3750);
        order.setDropLat(17.4190);
        order.setDropLng(78.3490);
        order.setAmount(180.0);
        order.setServiceName("2 Wheeler");
        order.setServiceType("GOODS");
        order = orderRepository.save(order);

        DriverOffer offer = new DriverOffer();
        offer.setBookingId(order.getBookingId());
        offer.setOrderId(order.getId());
        offer.setDriverId(driver.getId());
        offer.setStatus(DriverOfferStatus.OFFERED);
        offer.setOfferedFare(180.0);
        offer.setOfferedAt(LocalDateTime.now());
        offer.setExpiresAt(LocalDateTime.now().plusSeconds(60));
        driverOfferRepository.save(offer);

        List<DriverOfferResponse> responses = driverOfferService.getActiveOffersForDriver(driver.getId());
        assertFalse(responses.isEmpty());

        DriverOfferResponse res = responses.get(0);
        assertEquals("IKEA Hyderabad, HITEC City", res.getPickup());
        assertEquals("IKEA Hyderabad, HITEC City", res.getPickupAddress());
        assertEquals("Financial District, Nanakramguda", res.getDrop());
        assertEquals("Financial District, Nanakramguda", res.getDropAddress());
        assertEquals(17.4410, res.getPickupLat());
        assertEquals(17.4410, res.getPickupLatitude());
        assertEquals(78.3750, res.getPickupLng());
        assertEquals(78.3750, res.getPickupLongitude());
        assertEquals(17.4190, res.getDropLat());
        assertEquals(17.4190, res.getDropLatitude());
        assertEquals(78.3490, res.getDropLng());
        assertEquals(78.3490, res.getDropLongitude());
    }
}
