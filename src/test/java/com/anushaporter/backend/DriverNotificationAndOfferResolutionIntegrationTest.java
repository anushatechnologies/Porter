package com.anushaporter.backend;

import com.anushaporter.backend.model.*;
import com.anushaporter.backend.repository.DriverOfferRepository;
import com.anushaporter.backend.repository.DriverRepository;
import com.anushaporter.backend.repository.OrderRepository;
import com.anushaporter.backend.service.DriverOfferService;
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

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = BackendApplication.class)
@Import(DriverNotificationAndOfferResolutionIntegrationTest.TestConfig.class)
public class DriverNotificationAndOfferResolutionIntegrationTest {

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
    private DriverOfferService driverOfferService;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;

    @BeforeEach
    public void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        driverOfferRepository.deleteAll();
        orderRepository.deleteAll();
        driverRepository.deleteAll();
    }

    private Driver createTestDriver(String phone, String name, String vehicleNumber, double walletBalance) {
        Driver d = new Driver();
        d.setName(name);
        d.setPhone(phone);
        d.setEmail(phone + "@testdriver.com");
        d.setVehicleNumber(vehicleNumber);
        d.setVehicleType("Tata Ace");
        d.setStatus("online");
        d.setWalletBalance(walletBalance);
        d.setRegistrationStep(4);
        d.setKyc("approved");
        return driverRepository.save(d);
    }

    private Order createTestOrder(String bookingId, double amount) {
        Order o = new Order();
        o.setBookingId(bookingId);
        o.setUserEmail("customer@test.com");
        o.setServiceName("Tata Ace");
        o.setPickupAddress("Hitech City, Hyderabad");
        o.setDropAddress("Gachibowli, Hyderabad");
        o.setPickupLat(17.4486);
        o.setPickupLng(78.3908);
        o.setDropLat(17.4400);
        o.setDropLng(78.3800);
        o.setAmount(amount);
        o.setStatus("searching");
        o.setCreatedAt(LocalDateTime.now());
        return orderRepository.save(o);
    }

    @Test
    public void testDriverRejectOffer_StopsRingtoneAndPersistsRejection() throws Exception {
        Driver driver = createTestDriver("9876543210", "Ramesh Kumar", "TS09EA1111", 500.0);
        Order order = createTestOrder("BK-REJECT-001", 350.0);
        String driverToken = jwtUtil.generateToken(driver.getPhone());

        // Create initial OFFERED state in DB
        DriverOffer offer = new DriverOffer();
        offer.setBookingId(order.getBookingId());
        offer.setOrderId(order.getId());
        offer.setDriverId(driver.getId());
        offer.setStatus(DriverOfferStatus.OFFERED);
        offer.setOfferedAt(LocalDateTime.now());
        offer.setExpiresAt(LocalDateTime.now().plusMinutes(5));
        driverOfferRepository.save(offer);

        // Call Driver Reject endpoint: POST /api/driver/orders/{bookingId}/reject
        mockMvc.perform(post("/api/driver/orders/" + order.getBookingId() + "/reject")
                        .header("Authorization", "Bearer " + driverToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.stopSound").value(true))
                .andExpect(jsonPath("$.action").value("STOP_RINGTONE"));

        // Verify offer status in database is REJECTED
        DriverOffer updatedOffer = driverOfferRepository.findFirstByBookingIdAndDriverIdOrderByIdDesc(order.getBookingId(), driver.getId()).orElse(null);
        assertNotNull(updatedOffer);
        assertEquals(DriverOfferStatus.REJECTED, updatedOffer.getStatus());
        assertNotNull(updatedOffer.getRespondedAt());
    }

    @Test
    public void testDriverRejectOffer_SynthesizesRejectionWhenNoOfferExisted() throws Exception {
        Driver driver = createTestDriver("9876543211", "Suresh Patel", "TS09EA2222", 500.0);
        Order order = createTestOrder("BK-REJECT-SYNTH-002", 450.0);
        String driverToken = jwtUtil.generateToken(driver.getPhone());

        // Call Reject endpoint without prior offer row in DB
        mockMvc.perform(post("/api/driver/orders/" + order.getBookingId() + "/reject")
                        .header("Authorization", "Bearer " + driverToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.stopSound").value(true))
                .andExpect(jsonPath("$.action").value("STOP_RINGTONE"));

        // Verify synthesized offer is saved as REJECTED
        DriverOffer synth = driverOfferRepository.findFirstByBookingIdAndDriverIdOrderByIdDesc(order.getBookingId(), driver.getId()).orElse(null);
        assertNotNull(synth);
        assertEquals(DriverOfferStatus.REJECTED, synth.getStatus());
    }

    @Test
    public void testDriverAcceptOrder_ReturnsStopSoundAndAction() throws Exception {
        Driver winner = createTestDriver("9876543212", "Vijay Singh", "TS09EA3333", 500.0);
        Order order = createTestOrder("BK-ACCEPT-003", 400.0);
        String token = jwtUtil.generateToken(winner.getPhone());

        // Call Driver Accept endpoint
        mockMvc.perform(post("/api/driver/orders/" + order.getBookingId() + "/accept")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.status").value("accepted"))
                .andExpect(jsonPath("$.stopSound").value(true))
                .andExpect(jsonPath("$.action").value("STOP_RINGTONE"));

        // Second call from same driver returns idempotent success with stopSound
        mockMvc.perform(post("/api/driver/orders/" + order.getBookingId() + "/accept")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.stopSound").value(true))
                .andExpect(jsonPath("$.action").value("STOP_RINGTONE"));
    }

    @Test
    public void testOrderControllerAcceptAndRejectAliases() throws Exception {
        Driver driver = createTestDriver("9876543213", "Anil Rao", "TS09EA4444", 500.0);
        Order order = createTestOrder("BK-ALIAS-004", 500.0);
        String token = jwtUtil.generateToken(driver.getPhone());

        // Test POST /api/orders/{id}/reject
        mockMvc.perform(post("/api/orders/" + order.getBookingId() + "/reject")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.stopSound").value(true))
                .andExpect(jsonPath("$.action").value("STOP_RINGTONE"));

        // Reset order status for accept test
        order.setStatus("searching");
        orderRepository.save(order);

        // Test POST /api/orders/{id}/accept
        mockMvc.perform(post("/api/orders/" + order.getBookingId() + "/accept")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.stopSound").value(true))
                .andExpect(jsonPath("$.action").value("STOP_RINGTONE"));
    }

    @Test
    public void testCreateBooking_HandlesAliasesAndStringNumbers() throws Exception {
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("userPhone", "9988776655");
        payload.put("customerName", "Pooja Sharma");
        payload.put("vehicleType", "17ft");
        payload.put("pickupAddress", "Madhapur, Hyderabad");
        payload.put("dropAddress", "Secunderabad");
        payload.put("pickupLatitude", "17.4486");
        payload.put("pickupLongitude", "78.3908");
        payload.put("dropLatitude", "17.5000");
        payload.put("dropLongitude", "78.4500");
        payload.put("totalFare", "69196");
        payload.put("distanceKm", "1519.7");

        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.amount").value(69196.0))
                .andExpect(jsonPath("$.deliveryOtp").exists())
                .andExpect(jsonPath("$.bookingId").exists());

        Order saved = orderRepository.findAll().stream()
                .filter(o -> "17ft".equals(o.getServiceName()))
                .findFirst().orElse(null);
        assertNotNull(saved);
        assertEquals(69196.0, saved.getAmount());
        assertEquals("9988776655", saved.getReceiverPhone());
        assertEquals("Pooja Sharma", saved.getReceiverName());
    }

    @Test
    public void testJwtUtil_ExtractsFirebasePayloadClaimsGracefully() {
        // Construct simulated Firebase ID token payload (header.payload.signature)
        String header = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"alg\":\"RS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String payloadJson = "{\"iss\":\"https://securetoken.google.com/porter-test\",\"sub\":\"firebase_uid_12345\",\"phone_number\":\"+919876543299\",\"exp\":1700000000}";
        String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));
        String simulatedFirebaseToken = header + "." + payload + ".dummy_signature";

        String identifier = jwtUtil.extractIdentifierFromFirebaseOrJwt(simulatedFirebaseToken);
        assertEquals("+919876543299", identifier);
        assertTrue(jwtUtil.validateTokenOrFirebase(simulatedFirebaseToken));
    }
}
