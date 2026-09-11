package com.anushaporter.backend;

import com.anushaporter.backend.dto.DriverOfferResponse;
import com.anushaporter.backend.model.*;
import com.anushaporter.backend.repository.*;
import com.anushaporter.backend.service.DriverOfferService;
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

import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = BackendApplication.class)
@Import(OrderBookingAndDriverAcceptanceFlowTest.TestConfig.class)
public class OrderBookingAndDriverAcceptanceFlowTest {

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
    private DriverOfferService driverOfferService;

    @Autowired
    private JwtUtil jwtUtil;

    private MockMvc mockMvc;
    private Driver driver;
    private String userToken;
    private String driverToken;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();

        driverOfferRepository.deleteAll();
        notificationRepository.deleteAll();
        orderRepository.deleteAll();
        driverRepository.deleteAll();

        // Create an online 2-Wheeler driver
        driver = new Driver();
        driver.setName("Vinay Kumar");
        driver.setEmail("vinay@driver.com");
        driver.setPhone("9876543210");
        driver.setVehicleType("2 Wheeler");
        driver.setVehicle("2 Wheeler");
        driver.setVehicleNumber("TS 09 AB 5678");
        driver.setRating("4.9");
        driver.setStatus("online");
        driver.setKyc("approved");
        driver.setVerificationStatus("approved");
        driver.setWalletBalance(500.0);
        driver.setLatitude(17.4486);
        driver.setLongitude(78.3908);
        driver = driverRepository.save(driver);

        userToken = jwtUtil.generateToken("customer@example.com");
        driverToken = jwtUtil.generateToken(driver.getEmail());
    }

    @Test
    void testEndToEnd_OrderBooking_NoDefaultMockDriver_NotifiesOnlineDriver_DisplaysAcceptedDriver() throws Exception {
        // 1. Customer books a 2-Wheeler order (even with goodsCategory = "Household Items")
        String bookingPayload = """
                {
                  "serviceType": "2 Wheeler",
                  "serviceName": "2 Wheeler",
                  "pickupAddress": "Madhura Nagar Colony, Khajaguda, Hyderabad",
                  "dropAddress": "Yashoda Hospitals Hitec City Hyderabad",
                  "pickupLat": 17.4486,
                  "pickupLng": 78.3908,
                  "dropLat": 17.4560,
                  "dropLng": 78.4000,
                  "amount": 120.0,
                  "goodsCategory": "Household Items",
                  "senderName": "Customer Anusha",
                  "senderPhone": "9876500001"
                }
                """;

        String bookingResponse = mockMvc.perform(post("/api/bookings")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.bookingId", notNullValue()))
                .andExpect(jsonPath("$.status", is("searching")))
                .andReturn().getResponse().getContentAsString();

        com.fasterxml.jackson.databind.JsonNode rootNode = new com.fasterxml.jackson.databind.ObjectMapper().readTree(bookingResponse);
        String bookingId = rootNode.get("bookingId").asText();
        assertNotNull(bookingId);

        // Give auto-assignment a moment to run
        Thread.sleep(500);

        // 2. Customer checks tracking while in SEARCHING status:
        // By default, NO driver should be displayed (driver is null, hasAssignedDriver is false)
        // MUST NOT show "Manjunath (Supervisor)" or "Ramesh Kumar"
        mockMvc.perform(get("/api/bookings/" + bookingId + "/tracking")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.bookingId", is(bookingId)))
                .andExpect(jsonPath("$.status", is("searching")))
                .andExpect(jsonPath("$.hasAssignedDriver", is(false)))
                .andExpect(jsonPath("$.driver", nullValue()))
                .andExpect(jsonPath("$.assignedDriver", nullValue()))
                .andExpect(jsonPath("$.driverInfo", nullValue()))
                .andExpect(jsonPath("$.stageNumber", is(1)))
                .andExpect(jsonPath("$.stageLabel", is("Searching for Driver Partner")))
                .andExpect(jsonPath("$.eta", is("Finding driver...")))
                .andExpect(jsonPath("$.timeline[0].completed", is(true)))
                .andExpect(jsonPath("$.timeline[1].completed", is(false)));

        // 3. Verify that the online 2-Wheeler driver received the notification / offer
        List<DriverOfferResponse> offers = driverOfferService.getActiveOffersForDriver(driver.getId());
        assertFalse(offers.isEmpty(), "Online 2-Wheeler driver should have received the order offer");
        assertEquals(bookingId, offers.get(0).getBookingId());

        // 4. Online Driver accepts the order offer
        String acceptPayload = """
                {
                  "accept": true
                }
                """;

        mockMvc.perform(post("/api/driver/offers/" + bookingId + "/respond")
                        .header("Authorization", "Bearer " + driverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(acceptPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.status", is("ASSIGNED")));

        // 5. Customer checks tracking AFTER driver accepted:
        // NOW driver details must be populated with Vinay Kumar (the real driver)
        mockMvc.perform(get("/api/bookings/" + bookingId + "/tracking")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.bookingId", is(bookingId)))
                .andExpect(jsonPath("$.status", equalToIgnoringCase("assigned")))
                .andExpect(jsonPath("$.hasAssignedDriver", is(true)))
                .andExpect(jsonPath("$.driver", notNullValue()))
                .andExpect(jsonPath("$.driver.name", is("Vinay Kumar")))
                .andExpect(jsonPath("$.driver.phone", is("9876543210")))
                .andExpect(jsonPath("$.driver.vehicleNumber", is("TS 09 AB 5678")))
                .andExpect(jsonPath("$.driver.rating", is(4.9)))
                .andExpect(jsonPath("$.stageNumber", is(2)))
                .andExpect(jsonPath("$.stageLabel", is("Driver Assigned")))
                .andExpect(jsonPath("$.timeline[0].completed", is(true)))
                .andExpect(jsonPath("$.timeline[1].completed", is(true)));
    }

    @Test
    void testPackersShiftSupervisor_RetainedForDedicatedPmOrdersWithoutDriver() throws Exception {
        // Dedicated PM- booking without driver assigned
        String pmBookingId = "PM-SHIFT-101";
        Order order = new Order();
        order.setBookingId(pmBookingId);
        order.setUserEmail("customer@example.com");
        order.setServiceName("Packers & Movers - 1 BHK");
        order.setStatus("CONFIRMED");
        order.setDeliveryOtp("7788");
        orderRepository.save(order);

        mockMvc.perform(get("/api/bookings/" + pmBookingId + "/tracking")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.bookingId", is(pmBookingId)))
                .andExpect(jsonPath("$.status", is("IN_TRANSIT")))
                .andExpect(jsonPath("$.driver.name", containsString("Supervisor")))
                .andExpect(jsonPath("$.driver.vehicleNumber", is("KA-05-AB-7890")));
    }
}
