package com.anushaporter.backend;

import com.anushaporter.backend.dto.TripStatusUpdateRequest;
import com.anushaporter.backend.model.*;
import com.anushaporter.backend.repository.*;
import com.anushaporter.backend.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import software.amazon.awssdk.services.s3.S3Client;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = BackendApplication.class)
@Import(DriverCancellationLifecycleIntegrationTest.TestConfig.class)
public class DriverCancellationLifecycleIntegrationTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        public S3Client mockS3Client() {
            return Mockito.mock(S3Client.class);
        }
    }

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PassengerBookingRepository passengerBookingRepository;

    @Autowired
    private DriverOfferRepository driverOfferRepository;

    @Autowired
    private DriverRepository driverRepository;

    @Autowired
    private TripStateMachineService tripStateMachineService;

    @Autowired
    private DriverOfferService driverOfferService;

    private Driver driverA;
    private Driver driverB;

    @BeforeEach
    void setUp() {
        driverA = createDriver("Driver A", "driverA_" + UUID.randomUUID() + "@test.com", "9876543210", "TS09AB1001");
        driverB = createDriver("Driver B", "driverB_" + UUID.randomUUID() + "@test.com", "9876543211", "TS09AB1002");
    }

    private Driver createDriver(String name, String email, String phone, String vehicleNumber) {
        Driver d = new Driver();
        d.setName(name);
        d.setEmail(email);
        d.setPhone(phone);
        d.setVehicleNumber(vehicleNumber);
        d.setVehicleType("CAB");
        d.setStatus("online");
        d.setKyc("approved");
        d.setVerificationStatus("approved");
        d.setLatitude(17.4486);
        d.setLongitude(78.3908);
        return driverRepository.save(d);
    }

    @Test
    void testPassengerBookingStatusTerminalRule() {
        assertFalse(PassengerBookingStatus.CANCELLED_BY_DRIVER.isTerminal(),
                "CANCELLED_BY_DRIVER must not be terminal so reassignment tracking stays active.");
        assertTrue(PassengerBookingStatus.TRIP_COMPLETED.isTerminal());
        assertTrue(PassengerBookingStatus.CANCELLED_BY_CUSTOMER.isTerminal());
        assertTrue(PassengerBookingStatus.CANCELLED_BY_ADMIN.isTerminal());
        assertTrue(PassengerBookingStatus.EXPIRED.isTerminal());

        assertTrue(PassengerBookingStatus.DRIVER_ACCEPTED.canTransitionTo(PassengerBookingStatus.DRIVER_SEARCHING));
        assertTrue(PassengerBookingStatus.DRIVER_ARRIVING.canTransitionTo(PassengerBookingStatus.DRIVER_SEARCHING));
        assertTrue(PassengerBookingStatus.DRIVER_ARRIVED.canTransitionTo(PassengerBookingStatus.DRIVER_SEARCHING));
    }

    @Test
    void testDriverCancellationLifecycleAndStateSync() {
        String bookingId = "PB-" + System.currentTimeMillis();

        // 1. Create Order
        Order order = new Order();
        order.setBookingId(bookingId);
        order.setStatus(BookingStatus.SEARCHING.name());
        order.setUserEmail("customer@test.com");
        order.setAmount(250.0);
        order.setServiceType("PASSENGER");
        order = orderRepository.save(order);

        // 2. Create PassengerBooking
        PassengerBooking pb = new PassengerBooking();
        pb.setBookingNumber(bookingId);
        pb.setServiceType("DAILY_RIDE");
        pb.setVehicleCategoryCode("CAB");
        pb.setStatus(PassengerBookingStatus.DRIVER_SEARCHING);
        pb.setCustomerPhone("9123456789");
        pb.setCustomerEmail("customer@test.com");
        pb.setPickupAddress("Hitec City, Hyderabad");
        pb.setDropAddress("Gachibowli, Hyderabad");
        pb.setPricingVersionId("PV-TEST-1");
        pb = passengerBookingRepository.save(pb);

        // 3. Driver A accepts
        DriverOffer offerA = new DriverOffer();
        offerA.setBookingId(bookingId);
        offerA.setOrderId(order.getId());
        offerA.setDriverId(driverA.getId());
        offerA.setStatus(DriverOfferStatus.OFFERED);
        offerA.setExpiresAt(LocalDateTime.now().plusMinutes(2));
        driverOfferRepository.save(offerA);

        Map<String, Object> acceptRes = driverOfferService.respondToOffer(bookingId, driverA.getId(), true);
        assertTrue((Boolean) acceptRes.get("success"), "Driver A acceptance should succeed");

        // Verify Driver A is assigned in Order and PassengerBooking
        Order assignedOrder = orderRepository.findByBookingId(bookingId).orElseThrow();
        assertEquals(BookingStatus.ASSIGNED.name(), assignedOrder.getStatus());
        assertEquals(String.valueOf(driverA.getId()), assignedOrder.getDriverId());
        assertEquals(driverA.getName(), assignedOrder.getDriverName());

        PassengerBooking assignedPb = passengerBookingRepository.findByBookingNumber(bookingId).orElseThrow();
        assertEquals(PassengerBookingStatus.DRIVER_ASSIGNED, assignedPb.getStatus());
        assertEquals(driverA.getId(), assignedPb.getDriverId());
        assertEquals(driverA.getName(), assignedPb.getDriverName());

        // 4. Driver A cancels the trip
        TripStatusUpdateRequest cancelReq = new TripStatusUpdateRequest();
        cancelReq.setTargetStatus(BookingStatus.DRIVER_CANCELLED);
        cancelReq.setCancellationReason("Flat tire near pickup");

        Map<String, Object> cancelRes = tripStateMachineService.updateTripStatus(bookingId, cancelReq, String.valueOf(driverA.getId()));
        assertTrue((Boolean) cancelRes.get("success"), "Driver cancel should return success");
        assertEquals(BookingStatus.SEARCHING.name(), cancelRes.get("status"));

        // Verify Order is reset to SEARCHING and Driver A info is wiped
        Order resetOrder = orderRepository.findByBookingId(bookingId).orElseThrow();
        assertEquals(BookingStatus.SEARCHING.name(), resetOrder.getStatus());
        assertNull(resetOrder.getDriverId(), "Order driverId must be null after driver cancel");
        assertNull(resetOrder.getDriverName(), "Order driverName must be null after driver cancel");
        assertNull(resetOrder.getDriverEmail(), "Order driverEmail must be null after driver cancel");
        assertNull(resetOrder.getDriverPhone(), "Order driverPhone must be null after driver cancel");
        assertNull(resetOrder.getDriverVehicleNumber(), "Order driverVehicleNumber must be null after driver cancel");
        assertTrue(resetOrder.getCancellationReason().contains("Flat tire"));

        // Verify PassengerBooking is reset to DRIVER_SEARCHING and Driver A info is wiped
        PassengerBooking resetPb = passengerBookingRepository.findByBookingNumber(bookingId).orElseThrow();
        assertEquals(PassengerBookingStatus.DRIVER_SEARCHING, resetPb.getStatus());
        assertNull(resetPb.getDriverId(), "PassengerBooking driverId must be null after driver cancel");
        assertNull(resetPb.getDriverName(), "PassengerBooking driverName must be null after driver cancel");
        assertNull(resetPb.getDriverPhone(), "PassengerBooking driverPhone must be null after driver cancel");
        assertNull(resetPb.getVehicleNumber(), "PassengerBooking vehicleNumber must be null after driver cancel");
        assertNull(resetPb.getVehicleModel(), "PassengerBooking vehicleModel must be null after driver cancel");
        assertNull(resetPb.getDriverAssignedAt(), "PassengerBooking driverAssignedAt must be null after driver cancel");

        // Verify Driver A's offer is marked CANCELLED
        DriverOffer updatedOfferA = driverOfferRepository.findFirstByBookingIdAndDriverIdOrderByIdDesc(bookingId, driverA.getId()).orElseThrow();
        assertEquals(DriverOfferStatus.CANCELLED, updatedOfferA.getStatus());

        // Verify Driver A is in findExcludedDriverIdsForBooking
        List<Long> excluded = driverOfferRepository.findExcludedDriverIdsForBooking(bookingId);
        assertTrue(excluded.contains(driverA.getId()), "Driver A must be permanently excluded from this booking");

        // 5. Driver B can now be offered and accept atomically
        DriverOffer offerB = new DriverOffer();
        offerB.setBookingId(bookingId);
        offerB.setOrderId(order.getId());
        offerB.setDriverId(driverB.getId());
        offerB.setStatus(DriverOfferStatus.OFFERED);
        offerB.setExpiresAt(LocalDateTime.now().plusMinutes(2));
        driverOfferRepository.save(offerB);

        Map<String, Object> acceptResB = driverOfferService.respondToOffer(bookingId, driverB.getId(), true);
        assertTrue((Boolean) acceptResB.get("success"), "Driver B acceptance should succeed on reassignment");

        Order reassignedOrder = orderRepository.findByBookingId(bookingId).orElseThrow();
        assertEquals(BookingStatus.ASSIGNED.name(), reassignedOrder.getStatus());
        assertEquals(String.valueOf(driverB.getId()), reassignedOrder.getDriverId());
        assertEquals(driverB.getName(), reassignedOrder.getDriverName());

        PassengerBooking reassignedPb = passengerBookingRepository.findByBookingNumber(bookingId).orElseThrow();
        assertEquals(driverB.getId(), reassignedPb.getDriverId());
        assertEquals(driverB.getName(), reassignedPb.getDriverName());
    }
}
