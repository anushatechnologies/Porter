package com.anushaporter.backend;

import com.anushaporter.backend.model.*;
import com.anushaporter.backend.repository.*;
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
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = BackendApplication.class)
@Import(PassengerOrderDistanceResolutionIntegrationTest.TestConfig.class)
public class PassengerOrderDistanceResolutionIntegrationTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        public S3Client mockS3Client() {
            return Mockito.mock(S3Client.class);
        }

        @Bean
        @Primary
        public S3Presigner mockS3Presigner() {
            return Mockito.mock(S3Presigner.class);
        }
    }

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private DriverRepository driverRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PassengerBookingRepository passengerBookingRepository;

    @Autowired
    private JwtUtil jwtUtil;

    private MockMvc mockMvc;

    @BeforeEach
    public void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @Test
    public void testExistingCompletedOrderWithNullDistanceIsHealedInHistory() throws Exception {
        String phone = "9112233445";
        Driver driver = driverRepository.findByPhone(phone).orElseGet(() -> {
            Driver d = new Driver();
            d.setName("Distance Test Driver");
            d.setPhone(phone);
            d.setEmail("distance_driver@test.com");
            d.setStatus("APPROVED");
            return driverRepository.save(d);
        });

        String driverToken = jwtUtil.generateToken(driver.getPhone());

        // Simulate existing completed ride like #BK_AP-CAR-260930170... where backend previously saved nulls
        String bookingNumber = "AP-CAR-260930170999";
        orderRepository.findByBookingId(bookingNumber).ifPresent(orderRepository::delete);
        passengerBookingRepository.findByBookingNumber(bookingNumber).ifPresent(passengerBookingRepository::delete);

        // 1. PassengerBooking table has the calculated pricing distance
        PassengerBooking pb = PassengerBooking.builder()
                .bookingNumber(bookingNumber)
                .customerId(101L)
                .customerName("Passenger Tester")
                .customerPhone("9888877771")
                .customerEmail("pass@test.com")
                .serviceType("ONE_WAY")
                .vehicleCategoryCode("SEDAN")
                .pickupAddress("Hitec City, Hyderabad")
                .pickupLatitude(17.4483)
                .pickupLongitude(78.3915)
                .dropAddress("Secunderabad Railway Station, Hyderabad")
                .dropLatitude(17.4399)
                .dropLongitude(78.4983)
                .distanceKm(new BigDecimal("15.40"))
                .durationMinutes(32)
                .pricingVersionId("PV-TEST")
                .status(PassengerBookingStatus.TRIP_COMPLETED)
                .paymentStatus("PAID")
                .build();
        passengerBookingRepository.save(pb);

        // 2. Order table had null distance (reproducing the exact issue)
        Order order = new Order();
        order.setBookingId(bookingNumber);
        order.setDriverId(String.valueOf(driver.getId()));
        order.setDriverPhone(driver.getPhone());
        order.setDriverEmail(driver.getEmail());
        order.setDriverName(driver.getName());
        order.setServiceType("PASSENGER");
        order.setServiceName("SEDAN");
        order.setStatus("completed");
        order.setPaymentStatus("PAID");
        order.setAmount(450.0);
        order.setPickupAddress(pb.getPickupAddress());
        order.setDropAddress(pb.getDropAddress());
        order.setPickupLat(pb.getPickupLatitude());
        order.setPickupLng(pb.getPickupLongitude());
        order.setDropLat(pb.getDropLatitude());
        order.setDropLng(pb.getDropLongitude());
        order.setDistanceKm(null);       // Repro: null distance
        order.setDistanceMeters(null);   // Repro: null distance
        order.setDurationSeconds(null);  // Repro: null duration
        order.setCreatedAt(LocalDateTime.now().minusHours(1));
        orderRepository.save(order);

        // 3. Call GET /api/drivers/me/orders
        mockMvc.perform(get("/api/drivers/me/orders")
                .header("Authorization", "Bearer " + driverToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.orders[?(@.bookingId == '" + bookingNumber + "')].distanceKm").value(hasItem(15.4)))
                .andExpect(jsonPath("$.orders[?(@.bookingId == '" + bookingNumber + "')].distanceMeters").value(hasItem(15400)))
                .andExpect(jsonPath("$.orders[?(@.bookingId == '" + bookingNumber + "')].durationSeconds").value(hasItem(1920)));

        // 4. Verify order in DB has been permanently healed and saved
        Order healed = orderRepository.findByBookingId(bookingNumber).orElseThrow();
        assertNotNull(healed.getDistanceKm(), "Database record distanceKm must be healed");
        assertEquals(15.4, healed.getDistanceKm(), 0.01);
        assertEquals(15400, healed.getDistanceMeters());
        assertEquals(1920, healed.getDurationSeconds());
    }

    @Test
    public void testActiveOrderWithMissingDistanceIsHealedAndNeverNull() throws Exception {
        String phone = "9112233446";
        Driver driver = driverRepository.findByPhone(phone).orElseGet(() -> {
            Driver d = new Driver();
            d.setName("Active Test Driver");
            d.setPhone(phone);
            d.setEmail("active_driver@test.com");
            d.setStatus("APPROVED");
            return driverRepository.save(d);
        });

        String driverToken = jwtUtil.generateToken(driver.getPhone());
        String bookingNumber = "AP-CAR-260930170888";

        orderRepository.findByBookingId(bookingNumber).ifPresent(orderRepository::delete);
        passengerBookingRepository.findByBookingNumber(bookingNumber).ifPresent(passengerBookingRepository::delete);

        // PassengerBooking with known distance
        PassengerBooking pb = PassengerBooking.builder()
                .bookingNumber(bookingNumber)
                .customerId(102L)
                .customerName("Active Rider")
                .customerPhone("9888877772")
                .serviceType("ONE_WAY")
                .vehicleCategoryCode("HATCHBACK")
                .pickupAddress("Madhapur, Hyderabad")
                .pickupLatitude(17.4485)
                .pickupLongitude(78.3908)
                .dropAddress("Banjara Hills, Hyderabad")
                .dropLatitude(17.4156)
                .dropLongitude(78.4350)
                .distanceKm(new BigDecimal("8.50"))
                .durationMinutes(18)
                .pricingVersionId("PV-ACTIVE")
                .status(PassengerBookingStatus.TRIP_STARTED)
                .build();
        passengerBookingRepository.save(pb);

        Order order = new Order();
        order.setBookingId(bookingNumber);
        order.setDriverId(String.valueOf(driver.getId()));
        order.setDriverPhone(driver.getPhone());
        order.setDriverEmail(driver.getEmail());
        order.setServiceType("PASSENGER");
        order.setStatus("in_transit");
        order.setAmount(220.0);
        order.setPickupAddress(pb.getPickupAddress());
        order.setDropAddress(pb.getDropAddress());
        order.setDistanceKm(null);
        order.setDistanceMeters(null);
        order.setCreatedAt(LocalDateTime.now());
        orderRepository.save(order);

        // GET /api/drivers/me/orders/active
        mockMvc.perform(get("/api/drivers/me/orders/active")
                .header("Authorization", "Bearer " + driverToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.hasActiveOrder").value(true))
                .andExpect(jsonPath("$.distanceKm").value(8.5))
                .andExpect(jsonPath("$.distanceMeters").value(8500))
                .andExpect(jsonPath("$.durationSeconds").value(1080));

        Order dbOrder = orderRepository.findByBookingId(bookingNumber).orElseThrow();
        assertEquals(8.5, dbOrder.getDistanceKm(), 0.01);
        assertEquals(8500, dbOrder.getDistanceMeters());
    }

    @Test
    public void testTripCompletionAcceptsActualGpsDistance() throws Exception {
        String phone = "9112233447";
        Driver driver = driverRepository.findByPhone(phone).orElseGet(() -> {
            Driver d = new Driver();
            d.setName("Completion Test Driver");
            d.setPhone(phone);
            d.setEmail("completion_driver@test.com");
            d.setStatus("APPROVED");
            return driverRepository.save(d);
        });

        String driverToken = jwtUtil.generateToken(driver.getPhone());
        String bookingNumber = "AP-CAR-260930170777";

        orderRepository.findByBookingId(bookingNumber).ifPresent(orderRepository::delete);

        Order order = new Order();
        order.setBookingId(bookingNumber);
        order.setDriverId(String.valueOf(driver.getId()));
        order.setDriverPhone(driver.getPhone());
        order.setDriverEmail(driver.getEmail());
        order.setServiceType("PASSENGER");
        order.setStatus("in_transit");
        order.setAmount(300.0);
        order.setOtpVerified(true);
        order.setCreatedAt(LocalDateTime.now());
        orderRepository.save(order);

        String payload = """
            {
                "bookingId": "%s",
                "paymentMethod": "CASH",
                "amount": 300.0,
                "actualDistanceKm": 12.8
            }
        """.formatted(bookingNumber);

        mockMvc.perform(post("/api/driver/orders/" + bookingNumber + "/complete")
                .header("Authorization", "Bearer " + driverToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        Order completed = orderRepository.findByBookingId(bookingNumber).orElseThrow();
        assertEquals("completed", completed.getStatus());
        assertNotNull(completed.getDistanceKm());
        assertEquals(12.8, completed.getDistanceKm(), 0.01);
        assertEquals(12800, completed.getDistanceMeters());
    }
}
