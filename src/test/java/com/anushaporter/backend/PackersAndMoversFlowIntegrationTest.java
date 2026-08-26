package com.anushaporter.backend;

import com.anushaporter.backend.model.Order;
import com.anushaporter.backend.repository.OrderRepository;
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

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = BackendApplication.class)
@Import(PackersAndMoversFlowIntegrationTest.TestConfig.class)
public class PackersAndMoversFlowIntegrationTest {

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
    private JwtUtil jwtUtil;

    private String userToken;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        orderRepository.deleteAll();
        userToken = jwtUtil.generateToken("customer@example.com");
    }

    @Test
    void testCompletePackersAndMoversFlow() throws Exception {
        // 1. GET /api/customer/services
        mockMvc.perform(get("/api/customer/services"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.categories", not(empty())));

        // 2. POST /api/pricing/packers
        String pricingPayload = """
                {
                  "serviceId": "intracity",
                  "distanceKm": 12.5,
                  "vehicleId": "14ft",
                  "workerCount": 4,
                  "packingTier": "single_layer",
                  "packingCharge": 199.0,
                  "addons": ["dismantling", "reassembly", "unpacking"],
                  "items": [
                    { "name": "Sofa 3 Seater", "quantity": 1 },
                    { "name": "King Size Bed", "quantity": 1 },
                    { "name": "Double Door Refrigerator", "quantity": 1 },
                    { "name": "Carton Boxes", "quantity": 10 }
                  ],
                  "pickupFloor": 2,
                  "pickupLift": true,
                  "dropFloor": 3,
                  "dropLift": false,
                  "couponCode": "FIRSTMOVE"
                }
                """;

        mockMvc.perform(post("/api/pricing/packers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(pricingPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.serviceId", is("intracity")))
                .andExpect(jsonPath("$.transportationFare", notNullValue()))
                .andExpect(jsonPath("$.laborFare", notNullValue()))
                .andExpect(jsonPath("$.subtotal", notNullValue()))
                .andExpect(jsonPath("$.totalFare", notNullValue()));

        // 3. GET /api/services/{serviceId}/slots?date=2026-08-28
        mockMvc.perform(get("/api/services/intracity/slots?date=2026-08-28"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.date", is("2026-08-28")))
                .andExpect(jsonPath("$.slots", not(empty())));

        // 4. POST /api/bookings (Create Packers Order)
        String bookingPayload = """
                {
                  "serviceCategory": "packers",
                  "vehicleId": "14ft",
                  "workerCount": 4,
                  "pickupAddress": "Flat 402, Royal Palms, Madhapur, Hyderabad",
                  "dropAddress": "Villa 18, Green Meadows, Gachibowli, Hyderabad",
                  "pickupLat": 17.4483,
                  "pickupLng": 78.3915,
                  "dropLat": 17.4375,
                  "dropLng": 78.4482,
                  "senderName": "Anusha",
                  "senderPhone": "9876543210",
                  "receiverName": "Kiran Kumar",
                  "receiverPhone": "9123456780",
                  "scheduledDate": "2026-08-28",
                  "scheduledSlot": "09:00 AM – 11:00 AM",
                  "goodsCategory": "Household Furniture & Electronics",
                  "packingTier": "single_layer",
                  "dismantling": true,
                  "reassembly": true,
                  "unpacking": true,
                  "paymentMode": "advance",
                  "paymentMethod": "upi",
                  "amount": 4546.0,
                  "advancePaid": 500.0
                }
                """;

        String bookingResponse = mockMvc.perform(post("/api/bookings")
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(bookingPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.bookingId", notNullValue()))
                .andExpect(jsonPath("$.deliveryOtp", notNullValue()))
                .andReturn().getResponse().getContentAsString();

        // Extract bookingId and deliveryOtp
        com.fasterxml.jackson.databind.JsonNode rootNode = new com.fasterxml.jackson.databind.ObjectMapper().readTree(bookingResponse);
        String bookingId = rootNode.get("bookingId").asText();
        String deliveryOtp = rootNode.get("deliveryOtp").asText();
        assertNotNull(bookingId);

        // 5. GET /api/bookings/{id}/tracking
        mockMvc.perform(get("/api/bookings/" + bookingId + "/tracking")
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.stageNumber", notNullValue()))
                .andExpect(jsonPath("$.eta", is("25 mins")))
                .andExpect(jsonPath("$.timeline", hasSize(8)));

        // 6. POST /api/bookings/{id}/verify-otp
        mockMvc.perform(post("/api/bookings/" + bookingId + "/verify-otp")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"otp\": \"" + deliveryOtp + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.status", is("completed")))
                .andExpect(jsonPath("$.message", is("Move completed and verified successfully.")));

        // 7. POST /api/bookings/{id}/review
        String reviewPayload = """
                {
                  "rating": 5,
                  "tags": ["Professional Team", "Good Packing", "On Time"],
                  "feedback": "Ramesh and the team were extremely polite and handled glass items with great care."
                }
                """;

        mockMvc.perform(post("/api/bookings/" + bookingId + "/review")
                .contentType(MediaType.APPLICATION_JSON)
                .content(reviewPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.message", is("Thank you for your review!")));

        // 8. POST /api/bookings/{id}/reschedule
        String reschedulePayload = """
                {
                  "newDate": "2026-08-30",
                  "newSlot": "11:00 AM – 01:00 PM"
                }
                """;

        mockMvc.perform(post("/api/bookings/" + bookingId + "/reschedule")
                .contentType(MediaType.APPLICATION_JSON)
                .content(reschedulePayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.status", is("rescheduled")))
                .andExpect(jsonPath("$.message", is("Booking rescheduled successfully.")));

        // 9. POST /api/bookings/{id}/cancel
        String cancelPayload = """
                {
                  "reason": "Change of shifting date",
                  "cancelledBy": "CUSTOMER"
                }
                """;

        mockMvc.perform(post("/api/bookings/" + bookingId + "/cancel")
                .contentType(MediaType.APPLICATION_JSON)
                .content(cancelPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.status", is("cancelled")))
                .andExpect(jsonPath("$.refundAmount", is(500.0)))
                .andExpect(jsonPath("$.message", is("Booking cancelled. Refund initiated.")));
    }
}
