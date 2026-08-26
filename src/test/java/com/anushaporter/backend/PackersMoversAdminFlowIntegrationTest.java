package com.anushaporter.backend;

import com.anushaporter.backend.model.Order;
import com.anushaporter.backend.repository.OrderRepository;
import com.anushaporter.backend.util.JwtUtil;
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
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = BackendApplication.class)
@Import(PackersMoversAdminFlowIntegrationTest.TestConfig.class)
public class PackersMoversAdminFlowIntegrationTest {

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

    private ObjectMapper objectMapper = new ObjectMapper();

    private String testBookingId = "ANP-TEST-9999";
    private String adminToken;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        adminToken = "Bearer " + jwtUtil.generateToken("admin@porter.com");
        orderRepository.findByBookingId(testBookingId).ifPresent(orderRepository::delete);

        Order order = new Order();
        order.setBookingId(testBookingId);
        order.setUserEmail("pmcustomer@example.com");
        order.setServiceName("Intracity House Shifting");
        order.setGoodsCategory("Household");
        order.setPickupAddress("Hitech City, Hyderabad");
        order.setDropAddress("Gachibowli, Hyderabad");
        order.setAmount(3500.0);
        order.setStatus("QUOTE_PENDING");
        order.setCreatedAt(LocalDateTime.now());
        order.setScheduledDate("2026-08-30");
        order.setScheduledSlot("9:00 AM – 12:00 PM");
        orderRepository.save(order);
    }

    @Test
    void testAll14BusinessFlows() throws Exception {
        // Flow 1: Admin Panel Boot (Boot endpoints)
        mockMvc.perform(get("/api/admin/pm/dashboard").header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/admin/pm/service-types").header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/admin/pm/service-areas").header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/admin/pm/routes").header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/admin/pm/slots").header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/admin/pm/items").header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/admin/pm/item-categories").header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/admin/pm/packing-types").header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/admin/pm/vehicles").header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/admin/pm/labour").header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/admin/pm/coupons").header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/admin/pm/teams?available=true&city=Hyderabad").header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // Flow 2: Customer Books -> Admin views dossier & filter by status
        mockMvc.perform(get("/api/admin/pm/bookings?status=QUOTE_PENDING").header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/admin/pm/bookings/" + testBookingId).header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.booking.bookingId").value(testBookingId));

        // Flow 3: Admin Prepares & Dispatches Quote
        mockMvc.perform(post("/api/admin/pm/bookings/" + testBookingId + "/quote")
                .header("Authorization", adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("quotedAmount", 4200.0))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.status").value("QUOTE_SENT"));

        // Flow 4: Customer Pays -> Slot Confirmed
        mockMvc.perform(post("/api/pm/bookings/" + testBookingId + "/payment")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("paymentMethod", "UPI"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.status").value("SLOT_CONFIRMED"));

        // Flow 5: Team Assignment
        mockMvc.perform(post("/api/admin/pm/bookings/" + testBookingId + "/assign-team")
                .header("Authorization", adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "teamId", "T001",
                        "teamLeaderName", "Ramesh Kumar",
                        "teamLeaderPhone", "+919876543210",
                        "vehicleNumber", "TS09AB1234"
                ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.status").value("team_assigned"));

        // Flow 6: Move Day Live Updates
        mockMvc.perform(patch("/api/admin/pm/bookings/" + testBookingId + "/status")
                .header("Authorization", adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("status", "in_transit"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("in_transit"));

        mockMvc.perform(get("/api/admin/pm/bookings/" + testBookingId + "/live-tracking").header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.status").value("in_transit"));

        // Flow 7: On-Ground Extra Charges
        mockMvc.perform(get("/api/admin/pm/bookings/extra-charges?status=PENDING").header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(post("/api/admin/pm/bookings/" + testBookingId + "/extra-charges/CHG101/approve").header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        // Flow 8: Cancellation & Refund
        mockMvc.perform(get("/api/admin/pm/bookings/" + testBookingId + "/cancellation-fee").header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(post("/api/admin/pm/bookings/" + testBookingId + "/refund")
                .header("Authorization", adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("refundAmount", 3000.0))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // Flow 9: Inventory Verification
        mockMvc.perform(get("/api/admin/pm/bookings/" + testBookingId + "/inventory").header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(post("/api/admin/pm/bookings/" + testBookingId + "/inventory/verify")
                .header("Authorization", adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("verifiedBy", "Admin"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inventoryVerified").value(true));

        // Flow 10: Complaints & Resolution
        mockMvc.perform(get("/api/admin/pm/complaints?status=OPEN").header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(put("/api/admin/pm/complaints/CMP-101")
                .header("Authorization", adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("status", "RESOLVED", "resolution", "Refund issued"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"));

        // Flow 11-13: Configuration CRUD testing
        mockMvc.perform(post("/api/admin/pm/service-types")
                .header("Authorization", adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", "Villa Shifting", "baseFare", 4999))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true));

        // Flow 14: Customer App Feature Toggles
        mockMvc.perform(get("/api/pm/app-settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.settings.packersMoversEnabled").value("true"));

        mockMvc.perform(put("/api/admin/pm/app-settings")
                .header("Authorization", adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("packers_movers_enabled", "true", "support_phone", "+919876543210"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
