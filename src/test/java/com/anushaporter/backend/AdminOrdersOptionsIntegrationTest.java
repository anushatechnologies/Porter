package com.anushaporter.backend;

import com.anushaporter.backend.model.Order;
import com.anushaporter.backend.model.PassengerBooking;
import com.anushaporter.backend.model.PassengerBookingStatus;
import com.anushaporter.backend.repository.OrderRepository;
import com.anushaporter.backend.repository.PassengerBookingRepository;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import software.amazon.awssdk.services.s3.S3Client;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = BackendApplication.class)
@Import(AdminOrdersOptionsIntegrationTest.TestConfig.class)
public class AdminOrdersOptionsIntegrationTest {

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
    private OrderRepository orderRepository;

    @Autowired
    private PassengerBookingRepository passengerBookingRepository;

    @Autowired
    private JwtUtil jwtUtil;

    private String adminToken;

    private final String TEST_PASSENGER_BOOKING_NO = "PB-TEST-ADM-1001";
    private final String TEST_PACKERS_ORDER_ID = "PM-TEST-ADM-2002";
    private final String TEST_GOODS_ORDER_ID = "ORD-TEST-ADM-3003";

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        adminToken = "Bearer " + jwtUtil.generateToken("admin@porter.com");

        // Clean up any test records
        passengerBookingRepository.findByBookingNumber(TEST_PASSENGER_BOOKING_NO)
                .ifPresent(passengerBookingRepository::delete);
        orderRepository.findByBookingId(TEST_PACKERS_ORDER_ID).ifPresent(orderRepository::delete);
        orderRepository.findByBookingId(TEST_GOODS_ORDER_ID).ifPresent(orderRepository::delete);

        // 1. Seed Passenger Booking
        PassengerBooking pb = new PassengerBooking();
        pb.setBookingNumber(TEST_PASSENGER_BOOKING_NO);
        pb.setServiceType("ONE_WAY");
        pb.setCustomerName("Aditi Sharma");
        pb.setCustomerPhone("9876543210");
        pb.setCustomerEmail("aditi@example.com");
        pb.setVehicleCategoryCode("CAB_SEDAN");
        pb.setPickupAddress("Banjara Hills Road 12, Hyderabad");
        pb.setDropAddress("Rajiv Gandhi International Airport, Shamshabad");
        pb.setPickupLatitude(17.4156);
        pb.setPickupLongitude(78.4350);
        pb.setDropLatitude(17.2403);
        pb.setDropLongitude(78.4294);
        pb.setDistanceKm(new BigDecimal("32.5"));
        pb.setPricingVersionId("PV-TEST-ADM");
        pb.setFareBreakdown(com.anushaporter.backend.model.BookingFareBreakdown.builder()
                .totalFare(new BigDecimal("650.00"))
                .baseFare(new BigDecimal("100.00"))
                .build());
        pb.setStatus(PassengerBookingStatus.DRIVER_ASSIGNED);
        pb.setPaymentStatus("PAID");
        pb.setPaymentMethod("UPI");
        pb.setPassengerCount(2);
        pb.setStartOtp("4321");
        pb.setDriverName("Ramesh Kumar");
        pb.setDriverPhone("9988776655");
        pb.setVehicleNumber("TS09UB1234");
        pb.setCreatedAt(LocalDateTime.now().minusHours(1));
        passengerBookingRepository.save(pb);

        // 2. Seed Packers & Movers Order
        Order pm = new Order();
        pm.setBookingId(TEST_PACKERS_ORDER_ID);
        pm.setServiceType("PACKERS_MOVERS");
        pm.setServiceName("Complete 2BHK House Shifting");
        pm.setReceiverName("Vikram Singh");
        pm.setReceiverPhone("9123456789");
        pm.setUserEmail("vikram@example.com");
        pm.setPickupAddress("Madhapur 100ft Road, Hyderabad");
        pm.setDropAddress("Kondapur Main Road, Hyderabad");
        pm.setAmount(4800.0);
        pm.setStatus("ASSIGNED");
        pm.setPaymentStatus("PARTIALLY_PAID");
        pm.setPaymentMethod("CARD");
        pm.setHouseSize("2BHK");
        pm.setHeavyItems("Fridge, Washing Machine, King Bed");
        pm.setScheduledDate("2026-09-15");
        pm.setScheduledSlot("10:00 AM - 1:00 PM");
        pm.setDriverName("Suresh Packers Crew");
        pm.setDriverPhone("9811223344");
        pm.setDriverVehicleNumber("TS07UA5678");
        pm.setCreatedAt(LocalDateTime.now().minusHours(2));
        orderRepository.save(pm);

        // 3. Seed Goods / Freight Order
        Order goods = new Order();
        goods.setBookingId(TEST_GOODS_ORDER_ID);
        goods.setServiceType("GOODS");
        goods.setServiceName("Tata Ace 7ft Delivery");
        goods.setReceiverName("Warehouse Manager");
        goods.setReceiverPhone("9000112233");
        goods.setUserEmail("goods@example.com");
        goods.setPickupAddress("Sanathnagar Industrial Area");
        goods.setDropAddress("Cherlapally IDA");
        goods.setAmount(1200.0);
        goods.setStatus("COMPLETED");
        goods.setPaymentStatus("PAID");
        goods.setPaymentMethod("CASH");
        goods.setCreatedAt(LocalDateTime.now().minusHours(3));
        orderRepository.save(goods);
    }

    @Test
    void testAdminGetOrdersFilterPassenger() throws Exception {
        mockMvc.perform(get("/api/admin/orders")
                        .param("serviceType", "passenger")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.serviceType").value("passenger"))
                .andExpect(jsonPath("$.orders", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.orders[*].serviceCategory", everyItem(is("passenger"))))
                .andExpect(jsonPath("$.orders[?(@.bookingId == '" + TEST_PASSENGER_BOOKING_NO + "')].customerName")
                        .value(hasItem("Aditi Sharma")))
                .andExpect(jsonPath("$.orders[?(@.bookingId == '" + TEST_PASSENGER_BOOKING_NO + "')].vehicleCategory")
                        .value(hasItem("CAB_SEDAN")))
                .andExpect(jsonPath("$.orders[?(@.bookingId == '" + TEST_PASSENGER_BOOKING_NO + "')].passengerCount")
                        .value(hasItem(2)));
    }

    @Test
    void testAdminGetOrdersFilterPackersMovers() throws Exception {
        mockMvc.perform(get("/api/admin/orders")
                        .param("serviceType", "packers_movers")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.serviceType").value("packers_movers"))
                .andExpect(jsonPath("$.orders", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.orders[*].serviceCategory", everyItem(is("packers_movers"))))
                .andExpect(jsonPath("$.orders[?(@.bookingId == '" + TEST_PACKERS_ORDER_ID + "')].customerName")
                        .value(hasItem("Vikram Singh")))
                .andExpect(jsonPath("$.orders[?(@.bookingId == '" + TEST_PACKERS_ORDER_ID + "')].houseSize")
                        .value(hasItem("2BHK")))
                .andExpect(jsonPath("$.orders[?(@.bookingId == '" + TEST_PACKERS_ORDER_ID + "')].heavyItems")
                        .value(hasItem("Fridge, Washing Machine, King Bed")));
    }

    @Test
    void testAdminGetOrdersFilterPassengersAndMoversCombined() throws Exception {
        mockMvc.perform(get("/api/admin/orders")
                        .param("serviceType", "passengers_and_movers")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.serviceType").value("passengers_and_movers"))
                .andExpect(jsonPath("$.orders", hasSize(greaterThanOrEqualTo(2))))
                // Should only contain passenger or packers_movers, never goods
                .andExpect(jsonPath("$.orders[*].serviceCategory", everyItem(isOneOf("passenger", "packers_movers"))))
                .andExpect(jsonPath("$.orders[?(@.bookingId == '" + TEST_PASSENGER_BOOKING_NO + "')]").exists())
                .andExpect(jsonPath("$.orders[?(@.bookingId == '" + TEST_PACKERS_ORDER_ID + "')]").exists())
                .andExpect(jsonPath("$.orders[?(@.bookingId == '" + TEST_GOODS_ORDER_ID + "')]").doesNotExist());
    }

    @Test
    void testDedicatedPassengerOrdersEndpoint() throws Exception {
        mockMvc.perform(get("/api/admin/orders/passenger")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.orders[*].serviceCategory", everyItem(is("passenger"))))
                .andExpect(jsonPath("$.orders[?(@.bookingId == '" + TEST_PASSENGER_BOOKING_NO + "')]").exists());
    }

    @Test
    void testDedicatedPackersMoversOrdersEndpoint() throws Exception {
        mockMvc.perform(get("/api/admin/orders/packers-movers")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.orders[*].serviceCategory", everyItem(is("packers_movers"))))
                .andExpect(jsonPath("$.orders[?(@.bookingId == '" + TEST_PACKERS_ORDER_ID + "')]").exists());
    }

    @Test
    void testDedicatedPassengersAndMoversEndpoint() throws Exception {
        mockMvc.perform(get("/api/admin/orders/passengers-and-movers")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.orders[*].serviceCategory", everyItem(isOneOf("passenger", "packers_movers"))))
                .andExpect(jsonPath("$.orders[?(@.bookingId == '" + TEST_PASSENGER_BOOKING_NO + "')]").exists())
                .andExpect(jsonPath("$.orders[?(@.bookingId == '" + TEST_PACKERS_ORDER_ID + "')]").exists())
                .andExpect(jsonPath("$.orders[?(@.bookingId == '" + TEST_GOODS_ORDER_ID + "')]").doesNotExist());
    }

    @Test
    void testAdminOrdersSearchAndStatusFiltering() throws Exception {
        // Search by Passenger Customer Name
        mockMvc.perform(get("/api/admin/orders")
                        .param("search", "Aditi")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orders[?(@.bookingId == '" + TEST_PASSENGER_BOOKING_NO + "')]").exists());

        // Search by Packers Customer Phone
        mockMvc.perform(get("/api/admin/orders")
                        .param("search", "9123456789")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orders[?(@.bookingId == '" + TEST_PACKERS_ORDER_ID + "')]").exists());

        // Status filter: DRIVER_ASSIGNED
        mockMvc.perform(get("/api/admin/orders")
                        .param("status", "DRIVER_ASSIGNED")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orders[?(@.bookingId == '" + TEST_PASSENGER_BOOKING_NO + "')]").exists());
    }
}
