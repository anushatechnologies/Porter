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
                  "distanceKm": 8.5,
                  "packingType": "single_layer",
                  "packingCharge": 199,
                  "addons": ["addon_installation", "addon_unpacking"],
                  "items": [
                    { "name": "Sofa 3 Seater", "quantity": 1 },
                    { "name": "King Bed Frame", "quantity": 1 }
                  ]
                }
                """;

        mockMvc.perform(post("/api/pricing/packers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(pricingPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.baseFare", is(649.0)))
                .andExpect(jsonPath("$.distanceFare", is(170.0)))
                .andExpect(jsonPath("$.laborCharge", is(400.0)))
                .andExpect(jsonPath("$.packingCharge", is(199.0)))
                .andExpect(jsonPath("$.totalFare", is(1418.0)));

        // 3. GET /api/services/{serviceId}/slots?date=2026-08-27
        mockMvc.perform(get("/api/services/intracity/slots?date=2026-08-27"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.date", is("2026-08-27")))
                .andExpect(jsonPath("$.slots", hasSize(3)))
                .andExpect(jsonPath("$.slots[0].label", is("07:00 AM - 09:00 AM")));

        // 4. POST /api/bookings (Create Packers Order)
        String bookingPayload = """
                {
                  "serviceCategory": "packers",
                  "vehicleId": "intracity",
                  "pickupAddress": "Madhura Nagar, Khajaguda",
                  "dropAddress": "Hitech City, Madhapur",
                  "pickupLat": 17.4483,
                  "pickupLng": 78.3915,
                  "dropLat": 17.4375,
                  "dropLng": 78.4482,
                  "senderName": "Anusha",
                  "senderPhone": "9876543210",
                  "receiverName": "Kiran",
                  "receiverPhone": "9123456780",
                  "goodsCategory": "Household Goods",
                  "distanceKm": 8.5,
                  "paymentMethod": "cash",
                  "amount": 1418.0,
                  "scheduledDate": "2026-08-27",
                  "scheduledSlot": "07:00 AM - 09:00 AM"
                }
                """;

        String bookingResponse = mockMvc.perform(post("/api/bookings")
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(bookingPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.status", is("searching")))
                .andExpect(jsonPath("$.bookingId", startsWith("AP")))
                .andReturn().getResponse().getContentAsString();

        // Extract bookingId
        com.fasterxml.jackson.databind.JsonNode rootNode = new com.fasterxml.jackson.databind.ObjectMapper().readTree(bookingResponse);
        String bookingId = rootNode.get("bookingId").asText();
        assertNotNull(bookingId);

        // 5a. POST /api/bookings/{id}/assign-driver
        mockMvc.perform(post("/api/bookings/" + bookingId + "/assign-driver")
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.message", is("Broadcast sent to nearby drivers.")));

        // 5b. GET /api/bookings/{id}/tracking (while searching)
        mockMvc.perform(get("/api/bookings/" + bookingId + "/tracking")
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.status", is("searching")))
                .andExpect(jsonPath("$.driver").doesNotExist());

        // 5c. POST /api/bookings/{id}/cancel
        String cancelPayload = """
                {
                  "reason": "Change of plans",
                  "cancelledBy": "CUSTOMER"
                }
                """;

        mockMvc.perform(post("/api/bookings/" + bookingId + "/cancel")
                .contentType(MediaType.APPLICATION_JSON)
                .content(cancelPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.status", is("cancelled")))
                .andExpect(jsonPath("$.message", is("Order cancelled successfully")));

        // 5d. GET /api/bookings/{id}/tracking (after cancel)
        mockMvc.perform(get("/api/bookings/" + bookingId + "/tracking"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.status", is("cancelled")));
    }
}
