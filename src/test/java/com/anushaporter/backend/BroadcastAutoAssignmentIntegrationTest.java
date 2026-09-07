package com.anushaporter.backend;

import com.anushaporter.backend.dto.DriverOfferResponse;
import com.anushaporter.backend.model.*;
import com.anushaporter.backend.repository.*;
import com.anushaporter.backend.service.*;
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
import java.util.*;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = BackendApplication.class)
@Import(BroadcastAutoAssignmentIntegrationTest.TestConfig.class)
public class BroadcastAutoAssignmentIntegrationTest {

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
    private GlobalSettingsRepository globalSettingsRepository;

    @Autowired
    private DriverOfferService driverOfferService;

    @Autowired
    private DriverWalletService driverWalletService;

    @Autowired
    private AutoAssignmentService autoAssignmentService;

    @Autowired
    private JwtUtil jwtUtil;

    private MockMvc mockMvc;
    private Driver driver1;
    private Driver driver2;
    private Driver driver3;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();

        driverOfferRepository.deleteAll();
        notificationRepository.deleteAll();
        orderRepository.deleteAll();
        driverRepository.deleteAll();
        if (globalSettingsRepository != null) {
            globalSettingsRepository.deleteAll();
        }

        // Driver 1: Active, ₹0 wallet balance (Allowed to go online under 0-min policy)
        driver1 = new Driver();
        driver1.setName("Rider One");
        driver1.setEmail("rider1@example.com");
        driver1.setPhone("9111111111");
        driver1.setStatus("online");
        driver1.setWalletBalance(0.0);
        driver1.setLatitude(17.4486);
        driver1.setLongitude(78.3908);
        driver1.setVehicle("Tata Ace");
        driver1.setVehicleType("Tata Ace");
        driver1.setVehicleNumber("TS 09 R 1111");
        driver1.setKyc("approved");
        driver1.setVerificationStatus("approved");
        driver1 = driverRepository.save(driver1);

        // Driver 2: Active, ₹250 wallet balance
        driver2 = new Driver();
        driver2.setName("Rider Two");
        driver2.setEmail("rider2@example.com");
        driver2.setPhone("9222222222");
        driver2.setStatus("online");
        driver2.setWalletBalance(250.0);
        driver2.setLatitude(17.4510);
        driver2.setLongitude(78.3920);
        driver2.setVehicle("Tata Ace");
        driver2.setVehicleType("Tata Ace");
        driver2.setVehicleNumber("TS 09 R 2222");
        driver2.setKyc("approved");
        driver2.setVerificationStatus("approved");
        driver2 = driverRepository.save(driver2);

        // Driver 3: Active, ₹500 wallet balance
        driver3 = new Driver();
        driver3.setName("Rider Three");
        driver3.setEmail("rider3@example.com");
        driver3.setPhone("9333333333");
        driver3.setStatus("online");
        driver3.setWalletBalance(500.0);
        driver3.setLatitude(17.4550);
        driver3.setLongitude(78.3950);
        driver3.setVehicle("Tata Ace");
        driver3.setVehicleType("Tata Ace");
        driver3.setVehicleNumber("TS 09 R 3333");
        driver3.setKyc("approved");
        driver3.setVerificationStatus("approved");
        driver3 = driverRepository.save(driver3);
    }

    @Test
    void testZeroWalletBalanceDriverCanGoOnlineAndAcceptRides() throws Exception {
        // Driver 1 starts offline with 0 wallet balance
        driver1.setStatus("offline");
        driver1 = driverRepository.save(driver1);

        String token1 = jwtUtil.generateToken(driver1.getEmail());

        // Toggle online via PUT /api/drivers/me/status
        mockMvc.perform(put("/api/drivers/me/status")
                .header("Authorization", "Bearer " + token1)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\": \"online\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.status", is("online")));

        Driver fresh = driverRepository.findById(driver1.getId()).orElseThrow();
        assertEquals("online", fresh.getStatus());
        assertTrue(driverWalletService.canDriverAcceptRide(fresh));
    }

    @Test
    void testBroadcastOffersToAllActiveDriversSimultaneously() {
        String bookingId = "BK-BROADCAST-" + System.currentTimeMillis();
        Order order = new Order();
        order.setBookingId(bookingId);
        order.setStatus("searching");
        order.setAmount(450.0);
        order.setPickupAddress("Madhapur Metro");
        order.setDropAddress("Gachibowli Flyover");
        order.setPickupLat(17.4486);
        order.setPickupLng(78.3908);
        order.setServiceName("Tata Ace");
        order = orderRepository.save(order);

        List<Driver> activeDrivers = List.of(driver1, driver2, driver3);

        // Broadcast offer to all 3 active drivers at once (Rapido/Swiggy style)
        List<DriverOffer> createdOffers = driverOfferService.broadcastOffersToActiveDrivers(order, activeDrivers, 45);

        assertEquals(3, createdOffers.size(), "Offers should be created for all 3 active drivers");

        // Verify DB persistence of offers
        List<DriverOffer> dbOffers = driverOfferRepository.findByBookingId(bookingId);
        assertEquals(3, dbOffers.size());
        for (DriverOffer offer : dbOffers) {
            assertEquals(DriverOfferStatus.OFFERED, offer.getStatus());
        }

        // Verify that Driver 1 (₹0 balance), Driver 2, and Driver 3 all have active offers
        List<DriverOfferResponse> offersD1 = driverOfferService.getActiveOffersForDriver(driver1.getId());
        List<DriverOfferResponse> offersD2 = driverOfferService.getActiveOffersForDriver(driver2.getId());
        List<DriverOfferResponse> offersD3 = driverOfferService.getActiveOffersForDriver(driver3.getId());

        assertEquals(1, offersD1.size());
        assertEquals(1, offersD2.size());
        assertEquals(1, offersD3.size());
    }

    @Test
    void testAcceptanceStopsNotificationsAndMarksOthersTooLate() throws Exception {
        String bookingId = "BK-RACE-" + System.currentTimeMillis();
        Order order = new Order();
        order.setBookingId(bookingId);
        order.setStatus("searching");
        order.setAmount(300.0);
        order.setPickupAddress("Inorbit Mall");
        order.setDropAddress("IKEA Hyderabad");
        order.setPickupLat(17.4486);
        order.setPickupLng(78.3908);
        order.setServiceName("Tata Ace");
        order = orderRepository.save(order);

        // Broadcast offers to Driver 1 (₹0 balance), Driver 2, and Driver 3
        driverOfferService.broadcastOffersToActiveDrivers(order, List.of(driver1, driver2, driver3), 45);

        // Verify notification records exist for drivers
        List<Notification> initialNotifs = notificationRepository.findAll();
        assertFalse(initialNotifs.isEmpty(), "Notifications should be generated for offered drivers");

        // Driver 1 (₹0 balance) accepts the ride via API
        String token1 = jwtUtil.generateToken(driver1.getEmail());
        mockMvc.perform(post("/api/driver/offers/" + bookingId + "/respond")
                .header("Authorization", "Bearer " + token1)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"accept\": true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.status", is("ASSIGNED")));

        // Verify Order is assigned to Driver 1
        Order updatedOrder = orderRepository.findByBookingId(bookingId).orElseThrow();
        assertEquals("assigned", updatedOrder.getStatus().toLowerCase());
        assertEquals(driver1.getId().toString(), updatedOrder.getDriverId());

        // Verify Driver 1's offer is ACCEPTED
        DriverOffer offerD1 = driverOfferRepository.findFirstByBookingIdAndDriverIdOrderByIdDesc(bookingId, driver1.getId()).orElseThrow();
        assertEquals(DriverOfferStatus.ACCEPTED, offerD1.getStatus());

        // Verify Driver 2 and Driver 3 offers are marked TOO_LATE
        DriverOffer offerD2 = driverOfferRepository.findFirstByBookingIdAndDriverIdOrderByIdDesc(bookingId, driver2.getId()).orElseThrow();
        DriverOffer offerD3 = driverOfferRepository.findFirstByBookingIdAndDriverIdOrderByIdDesc(bookingId, driver3.getId()).orElseThrow();
        assertEquals(DriverOfferStatus.TOO_LATE, offerD2.getStatus());
        assertEquals(DriverOfferStatus.TOO_LATE, offerD3.getStatus());

        // Verify competing notifications were dismissed (marked read)
        List<Notification> competingNotifs = notificationRepository.findAll().stream()
                .filter(n -> bookingId.equals(n.getBookingId()) && !driver1.getId().equals(n.getUserId()))
                .toList();
        for (Notification n : competingNotifs) {
            assertTrue(Boolean.TRUE.equals(n.getReadStatus()), "Competing driver notifications must be marked read/dismissed");
        }

        // If Driver 2 now attempts to accept, they receive 409 / TOO_LATE
        String token2 = jwtUtil.generateToken(driver2.getEmail());
        mockMvc.perform(post("/api/driver/offers/" + bookingId + "/respond")
                .header("Authorization", "Bearer " + token2)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"accept\": true}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.status", is("TOO_LATE")));
    }

    @Test
    void testDriverRejectionDismissesOnlyTheirNotification() throws Exception {
        String bookingId = "BK-REJECT-" + System.currentTimeMillis();
        Order order = new Order();
        order.setBookingId(bookingId);
        order.setStatus("searching");
        order.setAmount(280.0);
        order.setPickupAddress("Kondapur");
        order.setDropAddress("Gachibowli");
        order.setPickupLat(17.4486);
        order.setPickupLng(78.3908);
        order.setServiceName("Tata Ace");
        order = orderRepository.save(order);

        driverOfferService.broadcastOffersToActiveDrivers(order, List.of(driver1, driver2), 45);

        // Driver 2 rejects offer
        String token2 = jwtUtil.generateToken(driver2.getEmail());
        mockMvc.perform(post("/api/driver/offers/" + bookingId + "/respond")
                .header("Authorization", "Bearer " + token2)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"accept\": false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.status", is("REJECTED")));

        DriverOffer offerD2 = driverOfferRepository.findFirstByBookingIdAndDriverIdOrderByIdDesc(bookingId, driver2.getId()).orElseThrow();
        assertEquals(DriverOfferStatus.REJECTED, offerD2.getStatus());

        // Driver 1's offer is still OFFERED
        DriverOffer offerD1 = driverOfferRepository.findFirstByBookingIdAndDriverIdOrderByIdDesc(bookingId, driver1.getId()).orElseThrow();
        assertEquals(DriverOfferStatus.OFFERED, offerD1.getStatus());

        // Driver 1 accepts
        String token1 = jwtUtil.generateToken(driver1.getEmail());
        mockMvc.perform(post("/api/driver/offers/" + bookingId + "/respond")
                .header("Authorization", "Bearer " + token1)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"accept\": true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.status", is("ASSIGNED")));
    }

    @Test
    void testOrderCancellationDismissesAllActiveOffersAndNotifications() throws Exception {
        String bookingId = "BK-CANCEL-" + System.currentTimeMillis();
        Order order = new Order();
        order.setBookingId(bookingId);
        order.setStatus("searching");
        order.setUserEmail("customer@example.com");
        order.setAmount(320.0);
        order.setPickupAddress("Jubilee Hills");
        order.setDropAddress("Banjara Hills");
        order.setPickupLat(17.4486);
        order.setPickupLng(78.3908);
        order.setServiceName("Tata Ace");
        order = orderRepository.save(order);

        driverOfferService.broadcastOffersToActiveDrivers(order, List.of(driver1, driver2), 45);

        String customerToken = jwtUtil.generateToken("customer@example.com");

        // Customer cancels booking
        mockMvc.perform(post("/api/bookings/" + bookingId + "/cancel")
                .header("Authorization", "Bearer " + customerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\": \"Changed plans\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.status", is("cancelled")));

        // Verify offers are marked TOO_LATE / cancelled
        DriverOffer offerD1 = driverOfferRepository.findFirstByBookingIdAndDriverIdOrderByIdDesc(bookingId, driver1.getId()).orElseThrow();
        DriverOffer offerD2 = driverOfferRepository.findFirstByBookingIdAndDriverIdOrderByIdDesc(bookingId, driver2.getId()).orElseThrow();
        assertEquals(DriverOfferStatus.TOO_LATE, offerD1.getStatus());
        assertEquals(DriverOfferStatus.TOO_LATE, offerD2.getStatus());

        // Verify notifications are marked read
        List<Notification> allNotifs = notificationRepository.findAll();
        for (Notification n : allNotifs) {
            if (bookingId.equals(n.getBookingId())) {
                assertTrue(Boolean.TRUE.equals(n.getReadStatus()), "All notifications for cancelled booking must be dismissed");
            }
        }
    }

    @Test
    void testTieredExpandingRadiusAutoAssignment() throws Exception {
        // Set tier duration to 2 seconds for this test
        autoAssignmentService.setTierDurationSeconds(2);

        // Driver 1: within Tier 1 (0-5 km) -> distance 0 km
        driver1.setLatitude(17.4486);
        driver1.setLongitude(78.3908);
        driverRepository.save(driver1);

        // Driver 2: in Tier 2 (5-10 km) -> distance ~7.8 km (17.5186 - 17.4486 = 0.07 deg lat)
        driver2.setLatitude(17.5186);
        driver2.setLongitude(78.3908);
        driverRepository.save(driver2);

        String bookingId = "BK-TIERED-" + System.currentTimeMillis();
        Order order = new Order();
        order.setBookingId(bookingId);
        order.setStatus("searching");
        order.setAmount(350.0);
        order.setPickupAddress("Madhapur");
        order.setDropAddress("Gachibowli");
        order.setPickupLat(17.4486);
        order.setPickupLng(78.3908);
        order.setServiceName("Tata Ace");
        order = orderRepository.save(order);

        // Start auto-assignment asynchronously
        java.util.concurrent.CompletableFuture<Boolean> future = autoAssignmentService.startAutoAssignment(bookingId);

        // Within Tier 1 window (<= 5km): Driver 1 gets offer, Driver 2 does not yet
        Thread.sleep(800);
        List<DriverOfferResponse> offersD1 = driverOfferService.getActiveOffersForDriver(driver1.getId());
        List<DriverOfferResponse> offersD2 = driverOfferService.getActiveOffersForDriver(driver2.getId());

        assertEquals(1, offersD1.size(), "Driver 1 (within 5km) must receive Tier 1 offer");
        assertEquals(0, offersD2.size(), "Driver 2 (at 7.8km) must NOT receive Tier 1 offer");

        // Wait for Tier 1 to expire (2s) and expand to Tier 2 (<= 10km)
        Thread.sleep(1700);
        offersD2 = driverOfferService.getActiveOffersForDriver(driver2.getId());
        assertEquals(1, offersD2.size(), "Driver 2 must receive offer once expanded to Tier 2 (<= 10km)");

        // Driver 2 accepts in Tier 2
        String token2 = jwtUtil.generateToken(driver2.getEmail());
        mockMvc.perform(post("/api/driver/offers/" + bookingId + "/respond")
                .header("Authorization", "Bearer " + token2)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"accept\": true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.status", is("ASSIGNED")));

        // Verify assignment and competing dismissal
        Order assignedOrder = orderRepository.findByBookingId(bookingId).orElseThrow();
        assertEquals(driver2.getId().toString(), assignedOrder.getDriverId());

        DriverOffer offerD1 = driverOfferRepository.findFirstByBookingIdAndDriverIdOrderByIdDesc(bookingId, driver1.getId()).orElseThrow();
        assertEquals(DriverOfferStatus.TOO_LATE, offerD1.getStatus());

        // Restore default tier duration
        autoAssignmentService.setTierDurationSeconds(60);
    }
}
