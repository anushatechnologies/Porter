package com.anushaporter.backend;

import com.anushaporter.backend.model.Driver;
import com.anushaporter.backend.model.DriverWallet;
import com.anushaporter.backend.model.Order;
import com.anushaporter.backend.model.WalletTransaction;
import com.anushaporter.backend.repository.DriverRepository;
import com.anushaporter.backend.repository.DriverWalletRepository;
import com.anushaporter.backend.repository.OrderRepository;
import com.anushaporter.backend.repository.WalletTransactionRepository;
import com.anushaporter.backend.service.DriverWalletService;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = BackendApplication.class)
@Import(DriverWalletFlowIntegrationTest.TestConfig.class)
public class DriverWalletFlowIntegrationTest {

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
    private DriverRepository driverRepository;

    @Autowired
    private DriverWalletRepository driverWalletRepository;

    @Autowired
    private WalletTransactionRepository walletTransactionRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private DriverWalletService driverWalletService;

    @Autowired
    private JwtUtil jwtUtil;

    private Driver testDriver;
    private String jwtToken;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();

        walletTransactionRepository.deleteAll();
        orderRepository.deleteAll();
        driverWalletRepository.deleteAll();
        driverRepository.deleteAll();

        testDriver = new Driver();
        testDriver.setName("Nithisha Kumar");
        testDriver.setEmail("nithisha@example.com");
        testDriver.setPhone("9876543299");
        testDriver.setVehicleNumber("TS09CD5678");
        testDriver.setVehicleType("Three Wheeler");
        testDriver.setStatus("offline");
        testDriver.setKyc("verified");
        testDriver.setWalletBalance(0.0);
        testDriver = driverRepository.save(testDriver);

        jwtToken = jwtUtil.generateToken(testDriver.getEmail());
    }

    @Test
    void testFullDriverWalletFlowEndToEnd() throws Exception {
        // 1. Zero-wallet driver CANNOT go online
        mockMvc.perform(put("/api/drivers/me/status")
                .header("Authorization", "Bearer " + jwtToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\": \"online\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.error", is("WALLET_EMPTY")));

        // 2. Zero-wallet driver CANNOT be assigned an order
        Order order = new Order();
        order.setBookingId("BOOK-TEST-101");
        order.setAmount(470.82);
        order.setStatus("pending");
        order = orderRepository.save(order);

        mockMvc.perform(post("/api/orders/" + order.getBookingId() + "/assign")
                .header("Authorization", "Bearer " + jwtToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"driverId\": \"" + testDriver.getId() + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.error", is("INSUFFICIENT_WALLET_BALANCE")));

        // 3. Admin recharges driver wallet with ₹1.00 -> driver auto-switches to "online"
        Map<String, Object> rechargeRes = driverWalletService.rechargeDriverWalletDirect(
                String.valueOf(testDriver.getId()), 1.00, "UPI-REF-001", "Admin Initial Recharge"
        );
        assertTrue((Boolean) rechargeRes.get("success"));
        assertEquals(1.00, (Double) rechargeRes.get("newWalletBalance"), 0.001);

        Driver reloadedDriver = driverRepository.findById(testDriver.getId()).orElseThrow();
        assertEquals("online", reloadedDriver.getStatus(), "Driver should be online after recharge from <= 0 balance");

        // 4. Admin assigns driver now that wallet > 0 -> success
        mockMvc.perform(post("/api/orders/" + order.getBookingId() + "/assign")
                .header("Authorization", "Bearer " + jwtToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"driverId\": \"" + testDriver.getId() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.status", is("assigned")));

        // 5. Order completed -> 5% commission deducted (470.82 * 0.05 = 23.54)
        // Wallet: 1.00 - 23.54 = -22.54 -> balance <= 0 -> driver auto-switched to "offline"
        WalletTransaction txn = driverWalletService.deductCommissionOnCompletion(
                String.valueOf(testDriver.getId()), order.getBookingId(), order.getAmount()
        );
        assertNotNull(txn);
        assertEquals("COMMISSION_DEDUCTION", txn.getTransactionType());
        assertEquals(23.54, txn.getCommissionAmount(), 0.01);
        assertEquals(-23.54, txn.getAmount(), 0.01);
        assertEquals(-22.54, txn.getBalanceAfter(), 0.01);

        Driver postOrderDriver = driverRepository.findById(testDriver.getId()).orElseThrow();
        assertEquals(-22.54, postOrderDriver.getWalletBalance(), 0.01);
        assertEquals("offline", postOrderDriver.getStatus(), "Driver should be set to offline when wallet <= 0");

        // Verify only 1 COMMISSION_DEDUCTION and 1 RECHARGE transaction exist (NO ORDER_EARNING transaction)
        List<WalletTransaction> allTxns = walletTransactionRepository.findAll();
        assertEquals(2, allTxns.size());
        boolean hasOrderEarning = allTxns.stream().anyMatch(t -> "ORDER_EARNING".equalsIgnoreCase(t.getTransactionType()));
        assertFalse(hasOrderEarning, "Should NOT contain ORDER_EARNING in wallet transactions");

        // 6. Driver tries to go online with negative balance -> rejected
        mockMvc.perform(put("/api/drivers/me/status")
                .header("Authorization", "Bearer " + jwtToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\": \"online\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("WALLET_EMPTY")));

        // 7. Driver recharges ₹100 -> New balance = -22.54 + 100 = 77.46 > 0 -> driver auto-switches to "online"
        driverWalletService.rechargeDriverWalletDirect(
                String.valueOf(testDriver.getId()), 100.00, "UPI-REF-002", "Driver Top-up"
        );

        Driver rechargedDriver = driverRepository.findById(testDriver.getId()).orElseThrow();
        assertEquals(77.46, rechargedDriver.getWalletBalance(), 0.01);
        assertEquals("online", rechargedDriver.getStatus(), "Driver should auto-switch to online when balance becomes positive");
    }
}
