package com.anushaporter.backend;

import com.anushaporter.backend.model.Order;
import com.anushaporter.backend.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import software.amazon.awssdk.services.s3.S3Client;

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = BackendApplication.class)
public class CustomerAppBugFixesIntegrationTest {

    @Configuration
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

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        orderRepository.deleteAll();
    }

    @Test
    void testSendOtpContainsResendCooldown30AndExpiresIn300() throws Exception {
        String requestBody = "{\"phone\": \"9876543210\", \"mode\": \"login\"}";

        mockMvc.perform(post("/api/auth/send-otp")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.expiresIn", is(300)))
                .andExpect(jsonPath("$.resendCooldown", is(30)));
    }

    @Test
    void testResendOtpContainsResendCooldown30AndExpiresIn300() throws Exception {
        String requestBody = "{\"phone\": \"9876543210\"}";

        mockMvc.perform(post("/api/auth/resend-otp")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.expiresIn", is(300)))
                .andExpect(jsonPath("$.resendCooldown", is(30)));
    }

    @Test
    void testGetCancellationReasonsReturnsCanonicalList() throws Exception {
        mockMvc.perform(get("/api/bookings/cancellation-reasons"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.reasons", hasSize(7)))
                .andExpect(jsonPath("$.reasons[0].title", is("Driver is taking too long")))
                .andExpect(jsonPath("$.reasons[1].title", is("I found another vehicle")))
                .andExpect(jsonPath("$.reasons[2].title", is("Booking by mistake")))
                .andExpect(jsonPath("$.reasons[3].title", is("Change of plans")))
                .andExpect(jsonPath("$.reasons[4].title", is("Driver requested cancellation")))
                .andExpect(jsonPath("$.reasons[5].title", is("Price issue")))
                .andExpect(jsonPath("$.reasons[6].title", is("Other")))
                .andExpect(jsonPath("$.reasons[6].requiresText", is(true)));
    }

    @Test
    void testGetActiveBookingWhenNone() throws Exception {
        mockMvc.perform(get("/api/bookings/active?phone=9998887776"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.hasActiveBooking", is(false)));
    }

    @Test
    void testGetActiveBookingWhenSearching() throws Exception {
        Order order = new Order();
        order.setBookingId("BK-TEST-ACTIVE-1");
        order.setUserEmail("9998887776@customer.porter.in");
        order.setStatus("searching");
        order.setServiceName("Tata Ace");
        order.setPickupAddress("Hitech City, Hyderabad");
        order.setDropAddress("Gachibowli, Hyderabad");
        order.setCreatedAt(LocalDateTime.now());
        orderRepository.save(order);

        mockMvc.perform(get("/api/bookings/active?phone=9998887776"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.hasActiveBooking", is(true)))
                .andExpect(jsonPath("$.bookingId", is("BK-TEST-ACTIVE-1")))
                .andExpect(jsonPath("$.status", is("searching")));
    }

    @Test
    void testCancelOrderDuringSearch() throws Exception {
        Order order = new Order();
        order.setBookingId("BK-CANCEL-SEARCH-1");
        order.setUserEmail("9998887776@customer.porter.in");
        order.setStatus("searching");
        order.setServiceName("2 Wheeler");
        order.setCreatedAt(LocalDateTime.now());
        orderRepository.save(order);

        String cancelPayload = "{\"cancelledBy\": \"CUSTOMER\", \"reason\": \"Customer cancelled during driver search\"}";

        mockMvc.perform(put("/api/bookings/BK-CANCEL-SEARCH-1/cancel")
                .contentType(MediaType.APPLICATION_JSON)
                .content(cancelPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.status", is("cancelled")))
                .andExpect(jsonPath("$.canCancel", is(false)))
                .andExpect(jsonPath("$.cancellationFee", is(0.0)))
                .andExpect(jsonPath("$.cancellationReason", is("Customer cancelled during driver search")))
                .andExpect(jsonPath("$.cancelledBy", is("CUSTOMER")));
    }

    @Test
    void testCancelOrderWithReasonAndRemarks() throws Exception {
        Order order = new Order();
        order.setBookingId("BK-CANCEL-REASONS-1");
        order.setUserEmail("9998887776@customer.porter.in");
        order.setStatus("assigned");
        order.setDriverName("Ramesh Kumar");
        order.setServiceName("Tata Ace");
        order.setCreatedAt(LocalDateTime.now());
        orderRepository.save(order);

        String cancelPayload = "{\"cancelledBy\": \"CUSTOMER\", \"reason\": \"Driver is taking too long\", \"remarks\": \"Waited 20 minutes\"}";

        mockMvc.perform(post("/api/bookings/BK-CANCEL-REASONS-1/cancel")
                .contentType(MediaType.APPLICATION_JSON)
                .content(cancelPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.status", is("cancelled")))
                .andExpect(jsonPath("$.cancellationFee", is(50.0)))
                .andExpect(jsonPath("$.cancellationReason", containsString("Driver is taking too long - Waited 20 minutes")));
    }
}
