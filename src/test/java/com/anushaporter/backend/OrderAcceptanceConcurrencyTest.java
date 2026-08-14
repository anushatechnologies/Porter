package com.anushaporter.backend;

import com.anushaporter.backend.model.Driver;
import com.anushaporter.backend.model.Order;
import com.anushaporter.backend.repository.DriverRepository;
import com.anushaporter.backend.repository.OrderRepository;
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
@Import(OrderAcceptanceConcurrencyTest.TestConfig.class)
public class OrderAcceptanceConcurrencyTest {

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
    private DriverRepository driverRepository;

    @Autowired
    private JwtUtil jwtUtil;

    private Driver driverA;
    private Driver driverB;
    private String tokenDriverA;
    private String tokenDriverB;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        orderRepository.deleteAll();
        driverRepository.deleteAll();

        driverA = new Driver();
        driverA.setName("Driver Alice");
        driverA.setEmail("alice@driver.com");
        driverA.setPhone("9876500001");
        driverA.setVehicleNumber("TS09AA1111");
        driverA.setStatus("online");
        driverA = driverRepository.save(driverA);

        driverB = new Driver();
        driverB.setName("Driver Bob");
        driverB.setEmail("bob@driver.com");
        driverB.setPhone("9876500002");
        driverB.setVehicleNumber("TS09BB2222");
        driverB.setStatus("online");
        driverB = driverRepository.save(driverB);

        tokenDriverA = jwtUtil.generateToken(driverA.getEmail());
        tokenDriverB = jwtUtil.generateToken(driverB.getEmail());
    }

    @Test
    void testFirstDriverAcceptsAndSecondDriverIsRejected() throws Exception {
        Order order = new Order();
        order.setBookingId("BK_TEST_001");
        order.setUserEmail("customer@test.com");
        order.setServiceName("Tata Ace");
        order.setStatus("searching");
        order.setAmount(450.0);
        order.setCreatedAt(LocalDateTime.now());
        order = orderRepository.save(order);

        // 1. Driver A taps ACCEPT via /api/orders/{id}/accept
        mockMvc.perform(put("/api/orders/" + order.getId() + "/accept")
                .header("Authorization", "Bearer " + tokenDriverA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.message", containsString("Order accepted successfully")))
                .andExpect(jsonPath("$.order.driverId", is(driverA.getId().toString())))
                .andExpect(jsonPath("$.order.status", is("accepted")));

        // Verify Driver A owns the order in DB
        Order inDb = orderRepository.findById(order.getId()).orElseThrow();
        assertEquals(driverA.getId().toString(), inDb.getDriverId());
        assertEquals("accepted", inDb.getStatus());

        // 2. Driver B taps ACCEPT 1 second later on the same order -> MUST receive 409 Conflict
        mockMvc.perform(put("/api/orders/" + order.getId() + "/accept")
                .header("Authorization", "Bearer " + tokenDriverB))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", is("This order has already been accepted by another driver.")));

        // Verify Driver A STILL owns the order and Driver B did not overwrite it
        inDb = orderRepository.findById(order.getId()).orElseThrow();
        assertEquals(driverA.getId().toString(), inDb.getDriverId());
        assertEquals("Driver Alice", inDb.getDriverName());
    }

    @Test
    void testAcceptViaStatusEndpointPreventsDuplicateAcceptance() throws Exception {
        Order order = new Order();
        order.setBookingId("BK_TEST_002");
        order.setUserEmail("customer@test.com");
        order.setServiceName("Mini Truck");
        order.setStatus("searching");
        order.setAmount(300.0);
        order = orderRepository.save(order);

        // Driver A accepts via PUT /api/orders/{id}/status
        mockMvc.perform(put("/api/orders/" + order.getBookingId() + "/status")
                .header("Authorization", "Bearer " + tokenDriverA)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\": \"accepted\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.message", containsString("Order accepted successfully")));

        // Driver B attempts to accept via PUT /api/orders/{id}/status -> MUST fail with 409
        mockMvc.perform(put("/api/orders/" + order.getBookingId() + "/status")
                .header("Authorization", "Bearer " + tokenDriverB)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\": \"accepted\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", is("This order has already been accepted by another driver.")));

        // Verify order remains assigned to Driver A
        Order inDb = orderRepository.findById(order.getId()).orElseThrow();
        assertEquals(driverA.getId().toString(), inDb.getDriverId());
    }

    @Test
    void testAcceptViaDriverApiEndpointPreventsDuplicateAcceptance() throws Exception {
        Order order = new Order();
        order.setBookingId("BK_TEST_003");
        order.setUserEmail("customer@test.com");
        order.setStatus("searching");
        order.setAmount(500.0);
        order = orderRepository.save(order);

        // Driver A accepts via /api/driver/orders/{bookingId}/accept
        mockMvc.perform(put("/api/driver/orders/" + order.getBookingId() + "/accept")
                .header("Authorization", "Bearer " + tokenDriverA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.status", is("accepted")));

        // Driver B attempts via /api/driver/orders/{bookingId}/accept -> MUST receive 409
        mockMvc.perform(put("/api/driver/orders/" + order.getBookingId() + "/accept")
                .header("Authorization", "Bearer " + tokenDriverB))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", is("This order has already been accepted by another driver.")));

        // Idempotent retry by Driver A -> Returns 200 OK
        mockMvc.perform(put("/api/driver/orders/" + order.getBookingId() + "/accept")
                .header("Authorization", "Bearer " + tokenDriverA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)));
    }
}
