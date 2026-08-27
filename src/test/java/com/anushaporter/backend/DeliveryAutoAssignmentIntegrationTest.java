package com.anushaporter.backend;

import com.anushaporter.backend.dto.TripStatusUpdateRequest;
import com.anushaporter.backend.dto.VehicleRecommendationRequest;
import com.anushaporter.backend.dto.VehicleRecommendationResponse;
import com.anushaporter.backend.model.*;
import com.anushaporter.backend.repository.DriverOfferRepository;
import com.anushaporter.backend.repository.DriverRepository;
import com.anushaporter.backend.repository.OrderRepository;
import com.anushaporter.backend.service.*;
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

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = BackendApplication.class)
@Import(DeliveryAutoAssignmentIntegrationTest.TestConfig.class)
public class DeliveryAutoAssignmentIntegrationTest {

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
    private DriverOfferRepository driverOfferRepository;

    @Autowired
    private VehicleRecommendationService vehicleRecommendationService;

    @Autowired
    private DriverEligibilityService driverEligibilityService;

    @Autowired
    private DriverRankingService driverRankingService;

    @Autowired
    private DriverOfferService driverOfferService;

    @Autowired
    private AutoAssignmentService autoAssignmentService;

    @Autowired
    private TripStateMachineService tripStateMachineService;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;
    private Driver driverA;
    private Driver driverB;
    private Driver driverC;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();

        // Create 3 active test drivers near Hyderabad Hitech City (17.4486, 78.3908)
        driverA = createOrUpdateDriver("9876500001", "Driver Alpha", "online", 17.4500, 78.3920, 4.9, 80, 500.0, "Tata Ace");
        driverB = createOrUpdateDriver("9876500002", "Driver Beta", "online", 17.4550, 78.3950, 4.7, 45, 350.0, "Tata Ace");
        driverC = createOrUpdateDriver("9876500003", "Driver Gamma", "offline", 17.4600, 78.4000, 4.2, 10, 200.0, "Tata Ace");
    }

    private Driver createOrUpdateDriver(String phone, String name, String status, double lat, double lng, double rating, int trips, double wallet, String vehicle) {
        Driver d = driverRepository.findByPhone(phone).orElse(new Driver());
        d.setName(name);
        d.setPhone(phone);
        d.setEmail(name.toLowerCase().replace(" ", "") + "@test.com");
        d.setStatus(status);
        d.setLatitude(lat);
        d.setLongitude(lng);
        d.setRating(String.valueOf(rating));
        d.setTrips(trips);
        d.setWalletBalance(wallet);
        d.setVehicle(vehicle);
        d.setVehicleType(vehicle);
        d.setVehicleNumber("TS 09 TEST " + phone.substring(6));
        d.setKyc("approved");
        d.setVerificationStatus("approved");
        return driverRepository.save(d);
    }

    @Test
    void testVehicleRecommendation() {
        // Small 15kg document parcel -> Should recommend 2 Wheeler
        VehicleRecommendationRequest smallReq = new VehicleRecommendationRequest();
        smallReq.setWeightKg(15.0);
        smallReq.setDistanceKm(5.0);
        smallReq.setGoodsCategory("documents");

        VehicleRecommendationResponse smallResp = vehicleRecommendationService.recommendVehicle(smallReq);
        assertTrue(smallResp.isSuccess());
        assertNotNull(smallResp.getRecommendedVehicleId());
        assertTrue(smallResp.getCapacityKg() >= 15);

        // 600kg payload -> Should recommend Tata Ace (750kg)
        VehicleRecommendationRequest heavyReq = new VehicleRecommendationRequest();
        heavyReq.setWeightKg(600.0);
        heavyReq.setDistanceKm(10.0);
        heavyReq.setGoodsCategory("furniture");

        VehicleRecommendationResponse heavyResp = vehicleRecommendationService.recommendVehicle(heavyReq);
        assertTrue(heavyResp.isSuccess());
        assertTrue(heavyResp.getCapacityKg() >= 600);
    }

    @Test
    void testDriverEligibilityAndRanking() {
        Order order = new Order();
        order.setServiceName("Tata Ace");
        order.setPickupLat(17.4486);
        order.setPickupLng(78.3908);

        // Driver A is online and eligible
        assertTrue(driverEligibilityService.isEligible(driverA, order, Set.of()));

        // Driver C is offline -> ineligible
        assertFalse(driverEligibilityService.isEligible(driverC, order, Set.of()));

        // Driver with empty wallet balance -> ineligible
        Driver zeroWalletDriver = createOrUpdateDriver("9876500004", "Driver Zero", "online", 17.4490, 78.3910, 4.8, 20, 0.0, "Tata Ace");
        assertFalse(driverEligibilityService.isEligible(zeroWalletDriver, order, Set.of()));

        // Ranking
        List<DriverRankingService.RankedDriver> ranked = driverRankingService.rankDrivers(
                List.of(driverA, driverB), order.getPickupLat(), order.getPickupLng(), 5.0, 3
        );
        assertEquals(2, ranked.size());
        // Driver A is closer and higher rated, should be rank #1
        assertEquals(driverA.getId(), ranked.get(0).getDriver().getId());
    }

    @Test
    void testFirstAcceptWinsAtomicAssignment() throws Exception {
        String bookingId = "TEST-ATOMIC-" + System.currentTimeMillis();
        Order order = new Order();
        order.setBookingId(bookingId);
        order.setStatus(BookingStatus.SEARCHING.name());
        order.setAmount(350.0);
        order.setPickupAddress("Madhapur");
        order.setDropAddress("Gachibowli");
        order.setPickupLat(17.4486);
        order.setPickupLng(78.3908);
        orderRepository.save(order);

        // Dispatch offers to Driver A and Driver B
        List<DriverRankingService.RankedDriver> candidates = List.of(
                new DriverRankingService.RankedDriver(driverA, 0.5, 90.0),
                new DriverRankingService.RankedDriver(driverB, 1.2, 80.0)
        );
        driverOfferService.createAndDispatchOffers(order, candidates, 3.0, 30);

        // Simulate concurrent acceptance
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch latch = new CountDownLatch(1);

        Future<Map<String, Object>> futureA = executor.submit(() -> {
            latch.await();
            return driverOfferService.respondToOffer(bookingId, driverA.getId(), true);
        });

        Future<Map<String, Object>> futureB = executor.submit(() -> {
            latch.await();
            return driverOfferService.respondToOffer(bookingId, driverB.getId(), true);
        });

        latch.countDown(); // Trigger both threads simultaneously

        Map<String, Object> resultA = futureA.get(5, TimeUnit.SECONDS);
        Map<String, Object> resultB = futureB.get(5, TimeUnit.SECONDS);

        boolean aWon = Boolean.TRUE.equals(resultA.get("success"));
        boolean bWon = Boolean.TRUE.equals(resultB.get("success"));

        // Exactly one driver must win
        assertTrue(aWon ^ bWon, "Exactly one driver must win the atomic assignment");

        // The losing driver must receive TOO_LATE
        if (aWon) {
            assertEquals("ASSIGNED", resultA.get("status"));
            assertEquals("TOO_LATE", resultB.get("status"));
        } else {
            assertEquals("ASSIGNED", resultB.get("status"));
            assertEquals("TOO_LATE", resultA.get("status"));
        }

        // Verify database state
        Order updated = orderRepository.findByBookingId(bookingId).orElseThrow();
        assertEquals("ASSIGNED", updated.getStatus());
        assertNotNull(updated.getDriverId());
    }

    @Test
    void testTripStateMachineTransitions() {
        String bookingId = "TEST-TRIP-" + System.currentTimeMillis();
        Order order = new Order();
        order.setBookingId(bookingId);
        order.setStatus(BookingStatus.ASSIGNED.name());
        order.setDriverId(driverA.getId().toString());
        order.setDriverName(driverA.getName());
        orderRepository.save(order);

        // 1. ASSIGNED -> DRIVER_EN_ROUTE (Valid)
        TripStatusUpdateRequest req1 = new TripStatusUpdateRequest();
        req1.setTargetStatus(BookingStatus.DRIVER_EN_ROUTE);
        Map<String, Object> res1 = tripStateMachineService.updateTripStatus(bookingId, req1, driverA.getId().toString());
        assertTrue(Boolean.TRUE.equals(res1.get("success")));
        assertEquals(BookingStatus.DRIVER_EN_ROUTE.name(), res1.get("status"));

        // 2. DRIVER_EN_ROUTE -> DRIVER_ARRIVED (Valid)
        TripStatusUpdateRequest req2 = new TripStatusUpdateRequest();
        req2.setTargetStatus(BookingStatus.DRIVER_ARRIVED);
        Map<String, Object> res2 = tripStateMachineService.updateTripStatus(bookingId, req2, driverA.getId().toString());
        assertTrue(Boolean.TRUE.equals(res2.get("success")));
        assertEquals(BookingStatus.DRIVER_ARRIVED.name(), res2.get("status"));

        // 3. DRIVER_ARRIVED -> PICKED_UP (Valid)
        TripStatusUpdateRequest req3 = new TripStatusUpdateRequest();
        req3.setTargetStatus(BookingStatus.PICKED_UP);
        Map<String, Object> res3 = tripStateMachineService.updateTripStatus(bookingId, req3, driverA.getId().toString());
        assertTrue(Boolean.TRUE.equals(res3.get("success")));

        // 4. Illegal Jump: PICKED_UP -> COMPLETED directly without delivery (Invalid)
        TripStatusUpdateRequest invalidReq = new TripStatusUpdateRequest();
        invalidReq.setTargetStatus(BookingStatus.COMPLETED);
        Map<String, Object> invalidRes = tripStateMachineService.updateTripStatus(bookingId, invalidReq, driverA.getId().toString());
        assertFalse(Boolean.TRUE.equals(invalidRes.get("success")));
        assertEquals("INVALID_TRANSITION", invalidRes.get("error"));

        // 5. PICKED_UP -> IN_TRANSIT (Valid)
        TripStatusUpdateRequest req4 = new TripStatusUpdateRequest();
        req4.setTargetStatus(BookingStatus.IN_TRANSIT);
        Map<String, Object> res4 = tripStateMachineService.updateTripStatus(bookingId, req4, driverA.getId().toString());
        assertTrue(Boolean.TRUE.equals(res4.get("success")));

        // 6. IN_TRANSIT -> DELIVERED (Valid)
        TripStatusUpdateRequest req5 = new TripStatusUpdateRequest();
        req5.setTargetStatus(BookingStatus.DELIVERED);
        Map<String, Object> res5 = tripStateMachineService.updateTripStatus(bookingId, req5, driverA.getId().toString());
        assertTrue(Boolean.TRUE.equals(res5.get("success")));

        // 7. DELIVERED -> COMPLETED (Valid)
        TripStatusUpdateRequest req6 = new TripStatusUpdateRequest();
        req6.setTargetStatus(BookingStatus.COMPLETED);
        Map<String, Object> res6 = tripStateMachineService.updateTripStatus(bookingId, req6, driverA.getId().toString());
        assertTrue(Boolean.TRUE.equals(res6.get("success")));
        assertEquals(BookingStatus.COMPLETED.name(), res6.get("status"));
    }

    @Test
    void testDriverCancellationAndReassignment() {
        String bookingId = "TEST-CANCEL-" + System.currentTimeMillis();
        Order order = new Order();
        order.setBookingId(bookingId);
        order.setStatus(BookingStatus.ASSIGNED.name());
        order.setDriverId(driverA.getId().toString());
        order.setDriverName(driverA.getName());
        orderRepository.save(order);

        // Driver cancels
        TripStatusUpdateRequest cancelReq = new TripStatusUpdateRequest();
        cancelReq.setTargetStatus(BookingStatus.DRIVER_CANCELLED);
        cancelReq.setCancellationReason("Flat tyre on the way");

        Map<String, Object> cancelRes = tripStateMachineService.updateTripStatus(bookingId, cancelReq, driverA.getId().toString());
        assertTrue(Boolean.TRUE.equals(cancelRes.get("success")));
        assertEquals(BookingStatus.SEARCHING.name(), cancelRes.get("status"));

        Order orderAfterCancel = orderRepository.findByBookingId(bookingId).orElseThrow();
        assertNull(orderAfterCancel.getDriverId());
    }

    @Test
    void testHttpEndpoints_VehicleRecommendationAndAutoAssign() throws Exception {
        // Test POST /api/vehicles/recommend
        mockMvc.perform(post("/api/vehicles/recommend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"weightKg\": 500, \"distanceKm\": 8, \"goodsCategory\": \"commercial\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.recommendedVehicleName", notNullValue()));

        // Create booking
        String testBookingId = "ANP-INT-" + System.currentTimeMillis();
        Order order = new Order();
        order.setBookingId(testBookingId);
        order.setStatus("searching");
        order.setUserEmail("customer@test.com");
        order.setAmount(400.0);
        orderRepository.save(order);

        // Test POST /api/bookings/{bookingId}/auto-assign
        mockMvc.perform(post("/api/bookings/" + testBookingId + "/auto-assign"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.status", is("SEARCHING")));
    }
}
