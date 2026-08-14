package com.anushaporter.backend;

import com.anushaporter.backend.model.Driver;
import com.anushaporter.backend.model.Order;
import com.anushaporter.backend.repository.DriverRepository;
import com.anushaporter.backend.repository.OrderRepository;
import com.anushaporter.backend.repository.RatingRepository;
import com.anushaporter.backend.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import org.mockito.Mockito;
import software.amazon.awssdk.services.s3.S3Client;

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = BackendApplication.class)
@Import(RatingIntegrationTest.TestConfig.class)
public class RatingIntegrationTest {

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
    private RatingRepository ratingRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private DriverRepository driverRepository;

    @Autowired
    private JwtUtil jwtUtil;

    private Driver testDriver;
    private String customerToken;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        ratingRepository.deleteAll();
        orderRepository.deleteAll();
        driverRepository.deleteAll();

        testDriver = new Driver();
        testDriver.setName("Rajesh Sharma");
        testDriver.setEmail("rajesh.driver@anushaporter.com");
        testDriver.setPhone("9876543210");
        testDriver.setVehicleNumber("TS09AB1234");
        testDriver.setStatus("online");
        testDriver.setRating("4.5");
        testDriver = driverRepository.save(testDriver);

        customerToken = jwtUtil.generateToken("customer@example.com");
    }

    @Test
    void testSubmitRatingSuccessfully() throws Exception {
        Order order = new Order();
        order.setBookingId("BK_1786691980998");
        order.setUserEmail("customer@example.com");
        order.setReceiverName("Ananya Verma");
        order.setDriverId(testDriver.getId().toString());
        order.setDriverName(testDriver.getName());
        order.setDriverEmail(testDriver.getEmail());
        order.setStatus("delivered");
        order.setAmount(450.0);
        order.setCreatedAt(LocalDateTime.now());
        order = orderRepository.save(order);

        String jsonPayload = """
        {
          "bookingId": "BK_1786691980998",
          "rating": 5,
          "review": "Very polite driver and reached on time!",
          "feedback": [
            "On Time Delivery ⚡",
            "Polite & Helpful 🤝",
            "Careful with Goods 📦"
          ],
          "driverId": "%s"
        }
        """.formatted(testDriver.getId());

        mockMvc.perform(post("/api/bookings/BK_1786691980998/rate")
                .header("Authorization", "Bearer " + customerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.message", is("Driver rating submitted successfully")))
                .andExpect(jsonPath("$.data.bookingId", is("BK_1786691980998")))
                .andExpect(jsonPath("$.data.rating", is(5)))
                .andExpect(jsonPath("$.data.review", is("Very polite driver and reached on time!")))
                .andExpect(jsonPath("$.data.feedback", hasSize(3)))
                .andExpect(jsonPath("$.data.newDriverAverageRating", is(5.0)));

        // Verify Driver entity rating in DB
        Driver updatedDriver = driverRepository.findById(testDriver.getId()).orElseThrow();
        assertEquals("5.0", updatedDriver.getRating());

        // Verify GET /api/bookings/BK_1786691980998/rate
        mockMvc.perform(get("/api/bookings/BK_1786691980998/rate")
                .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.rating.rating", is(5)));
    }

    @Test
    void testInvalidRatingValueReturns400() throws Exception {
        Order order = new Order();
        order.setBookingId("BK_1786691980999");
        order.setStatus("delivered");
        order = orderRepository.save(order);

        // Rating = 6 (Out of range)
        String jsonPayload = """
        {
          "bookingId": "BK_1786691980999",
          "rating": 6
        }
        """;

        mockMvc.perform(post("/api/bookings/BK_1786691980999/rate")
                .header("Authorization", "Bearer " + customerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", is("Rating must be an integer between 1 and 5")));
    }

    @Test
    void testOrderNotDeliveredYetReturns400() throws Exception {
        Order order = new Order();
        order.setBookingId("BK_1786691981000");
        order.setStatus("in_transit");
        order = orderRepository.save(order);

        String jsonPayload = """
        {
          "bookingId": "BK_1786691981000",
          "rating": 5
        }
        """;

        mockMvc.perform(post("/api/bookings/BK_1786691981000/rate")
                .header("Authorization", "Bearer " + customerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", is("You can only rate an order after it has been delivered")));
    }

    @Test
    void testDuplicateRatingReturns409Conflict() throws Exception {
        Order order = new Order();
        order.setBookingId("BK_1786691981001");
        order.setStatus("completed");
        order.setDriverId(testDriver.getId().toString());
        order = orderRepository.save(order);

        String jsonPayload = """
        {
          "bookingId": "BK_1786691981001",
          "rating": 4,
          "review": "Good service"
        }
        """;

        // First rating submission -> 200 OK
        mockMvc.perform(post("/api/bookings/BK_1786691981001/rate")
                .header("Authorization", "Bearer " + customerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)));

        // Second rating submission for same booking -> 409 Conflict
        mockMvc.perform(post("/api/bookings/BK_1786691981001/rate")
                .header("Authorization", "Bearer " + customerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonPayload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", is("This order has already been rated")));
    }

    @Test
    void testAverageRatingRecalculationAcrossMultipleOrders() throws Exception {
        // Order 1 -> Rating 5
        Order order1 = new Order();
        order1.setBookingId("BK_TRIP_1");
        order1.setStatus("delivered");
        order1.setDriverId(testDriver.getId().toString());
        order1 = orderRepository.save(order1);

        mockMvc.perform(post("/api/bookings/BK_TRIP_1/rate")
                .header("Authorization", "Bearer " + customerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rating\": 5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.newDriverAverageRating", is(5.0)));

        // Order 2 -> Rating 3 -> Average should be (5 + 3) / 2 = 4.0
        Order order2 = new Order();
        order2.setBookingId("BK_TRIP_2");
        order2.setStatus("delivered");
        order2.setDriverId(testDriver.getId().toString());
        order2 = orderRepository.save(order2);

        mockMvc.perform(post("/api/bookings/BK_TRIP_2/rate")
                .header("Authorization", "Bearer " + customerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rating\": 3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.newDriverAverageRating", is(4.0)));

        Driver updated = driverRepository.findById(testDriver.getId()).orElseThrow();
        assertEquals("4.0", updated.getRating());
    }
}
