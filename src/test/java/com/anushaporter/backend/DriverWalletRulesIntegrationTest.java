package com.anushaporter.backend;

import com.anushaporter.backend.model.AppUser;
import com.anushaporter.backend.model.Driver;
import com.anushaporter.backend.model.DriverWallet;
import com.anushaporter.backend.model.Order;
import com.anushaporter.backend.model.WalletTransaction;
import com.anushaporter.backend.repository.*;
import com.anushaporter.backend.service.DeliveryCompletionService;
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

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = BackendApplication.class)
@Import(DriverWalletRulesIntegrationTest.TestConfig.class)
public class DriverWalletRulesIntegrationTest {

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
    private PaymentOrderRepository paymentOrderRepository;

    @Autowired
    private GlobalSettingsRepository globalSettingsRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private DriverWalletService driverWalletService;

    @Autowired
    private DeliveryCompletionService deliveryCompletionService;

    @Autowired
    private JwtUtil jwtUtil;

    private Driver testDriver;
    private String driverJwt;
    private String adminJwt;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();

        walletTransactionRepository.deleteAll();
        paymentOrderRepository.deleteAll();
        orderRepository.deleteAll();
        driverWalletRepository.deleteAll();
        driverRepository.deleteAll();
        globalSettingsRepository.deleteAll();
        appUserRepository.deleteAll();

        // Seed Admin User
        AppUser adminUser = new AppUser();
        adminUser.setEmail("admin@anushaporter.com");
        adminUser.setName("Super Admin");
        adminUser.setRole("admin");
        appUserRepository.save(adminUser);
        adminJwt = jwtUtil.generateToken("admin@anushaporter.com");

        // Seed Driver User
        AppUser driverUser = new AppUser();
        driverUser.setEmail("driver@anushaporter.com");
        driverUser.setPhone("9876543210");
        driverUser.setName("Suresh Raina");
        driverUser.setRole("driver");
        appUserRepository.save(driverUser);

        // Seed Driver Profile
        testDriver = new Driver();
        testDriver.setName("Suresh Raina");
        testDriver.setEmail("driver@anushaporter.com");
        testDriver.setPhone("9876543210");
        testDriver.setVehicleNumber("TS09CD5678");
        testDriver.setVehicleType("Tata Ace");
        testDriver.setStatus("online");
        testDriver.setKyc("verified");
        testDriver.setWalletBalance(0.0);
        testDriver = driverRepository.save(testDriver);

        DriverWallet wallet = new DriverWallet();
        wallet.setDriverId(String.valueOf(testDriver.getId()));
        wallet.setAvailableBalance(0.0);
        driverWalletRepository.save(wallet);

        driverJwt = jwtUtil.generateToken(testDriver.getEmail());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 1. MINIMUM RECHARGE AMOUNT (DEFAULT 1000) & ADMIN CAN MODIFY ANYTIME
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void testDefaultMinimumRechargeAmountIs1000AndEnforced() throws Exception {
        // Driver attempts to recharge ₹500 (less than minimum 1000) -> fails with 400
        String rechOrder = "RECH_" + System.currentTimeMillis();
        mockMvc.perform(post("/api/payments/razorpay/create-order")
                        .header("Authorization", "Bearer " + driverJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"amount\": 500.00, \"bookingId\": \"%s\", \"currency\": \"INR\"}", rechOrder)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.error", is("MINIMUM_RECHARGE_AMOUNT_NOT_MET")))
                .andExpect(jsonPath("$.message", containsString("1000")));

        // Driver recharges with ₹1000 (equal to minimum 1000) -> succeeds with 200
        mockMvc.perform(post("/api/payments/razorpay/create-order")
                        .header("Authorization", "Bearer " + driverJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"amount\": 1000.00, \"bookingId\": \"%s\", \"currency\": \"INR\"}", rechOrder)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.amount", is(1000.00)));
    }

    @Test
    void testAdminCanModifyMinimumRechargeAmountAnytime() throws Exception {
        // 1. Admin modifies minimum balance / recharge amount to ₹1500
        mockMvc.perform(post("/api/admin/settings/wallet")
                        .header("Authorization", "Bearer " + adminJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\n" +
                                "  \"minRechargeAmount\": 1500.0,\n" +
                                "  \"minRequiredBalance\": 1500.0\n" +
                                "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.minRechargeAmount", is(1500.0)))
                .andExpect(jsonPath("$.minRequiredBalance", is(1500.0)));

        // 2. Now ₹1000 recharge fails because minimum is now 1500
        String rechOrder = "RECH_" + System.currentTimeMillis();
        mockMvc.perform(post("/api/payments/razorpay/create-order")
                        .header("Authorization", "Bearer " + driverJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"amount\": 1000.00, \"bookingId\": \"%s\", \"currency\": \"INR\"}", rechOrder)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", containsString("1500")));

        // 3. Recharging ₹1500 succeeds
        mockMvc.perform(post("/api/payments/razorpay/create-order")
                        .header("Authorization", "Bearer " + driverJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"amount\": 1500.00, \"bookingId\": \"%s\", \"currency\": \"INR\"}", rechOrder)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.amount", is(1500.00)));

        // 4. Admin lowers minimum back anytime (e.g. to ₹500)
        mockMvc.perform(put("/api/admin/wallet/minimum-balance")
                        .header("Authorization", "Bearer " + adminJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"minimumBalance\": 500.0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.minRequiredBalance", is(500.0)));

        // Now ₹500 is accepted
        mockMvc.perform(post("/api/payments/razorpay/create-order")
                        .header("Authorization", "Bearer " + driverJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"amount\": 500.00, \"bookingId\": \"%s\", \"currency\": \"INR\"}", "RECH_2_" + System.currentTimeMillis())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 2. DRIVER WALLET MUST BE > 0 TO ACCEPT RIDES (0 OR NEGATIVE BLOCKED)
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void testDriverWithZeroBalanceCannotAcceptRide() throws Exception {
        // Driver balance is 0.00
        testDriver.setWalletBalance(0.00);
        driverRepository.save(testDriver);

        Order order = new Order();
        order.setBookingId("BK_ZERO_BAL_1");
        order.setAmount(600.00);
        order.setStatus("placed");
        order = orderRepository.save(order);

        // Acceptance via /api/driver/orders/{bookingId}/accept must fail
        mockMvc.perform(post("/api/driver/orders/" + order.getBookingId() + "/accept")
                        .header("Authorization", "Bearer " + driverJwt))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.error", is("INSUFFICIENT_WALLET_BALANCE")))
                .andExpect(jsonPath("$.message", containsString("Driver wallet balance must be greater than ₹0")));

        // Acceptance via /api/orders/{id}/accept must fail
        mockMvc.perform(post("/api/orders/" + order.getBookingId() + "/accept")
                        .header("Authorization", "Bearer " + driverJwt))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.error", is("INSUFFICIENT_WALLET_BALANCE")));

        // Acceptance via /api/orders/{id}/status with status: accepted must fail
        mockMvc.perform(post("/api/orders/" + order.getBookingId() + "/status")
                        .header("Authorization", "Bearer " + driverJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"accepted\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.error", is("INSUFFICIENT_WALLET_BALANCE")));
    }

    @Test
    void testDriverWithNegativeBalanceCannotAcceptRide() throws Exception {
        // Driver has negative balance (-25.00)
        testDriver.setWalletBalance(-25.00);
        driverRepository.save(testDriver);

        DriverWallet wallet = driverWalletRepository.findByDriverId(String.valueOf(testDriver.getId())).orElseThrow();
        wallet.setAvailableBalance(-25.00);
        driverWalletRepository.save(wallet);

        Order order = new Order();
        order.setBookingId("BK_NEG_BAL_1");
        order.setAmount(800.00);
        order.setStatus("placed");
        order = orderRepository.save(order);

        mockMvc.perform(post("/api/driver/orders/" + order.getBookingId() + "/accept")
                        .header("Authorization", "Bearer " + driverJwt))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.error", is("INSUFFICIENT_WALLET_BALANCE")));
    }

    @Test
    void testDriverWithPositiveBalanceCanAcceptRide() throws Exception {
        // Driver has positive balance (> 0)
        testDriver.setWalletBalance(150.00);
        driverRepository.save(testDriver);

        DriverWallet wallet = driverWalletRepository.findByDriverId(String.valueOf(testDriver.getId())).orElseThrow();
        wallet.setAvailableBalance(150.00);
        driverWalletRepository.save(wallet);

        Order order = new Order();
        order.setBookingId("BK_POS_BAL_1");
        order.setAmount(500.00);
        order.setStatus("placed");
        order = orderRepository.save(order);

        mockMvc.perform(post("/api/driver/orders/" + order.getBookingId() + "/accept")
                        .header("Authorization", "Bearer " + driverJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.status", is("accepted")));

        Order acceptedOrder = orderRepository.findByBookingId("BK_POS_BAL_1").orElseThrow();
        assertEquals("accepted", acceptedOrder.getStatus());
        assertEquals(String.valueOf(testDriver.getId()), acceptedOrder.getDriverId());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 3. 5% COMMISSION DEDUCTION AFTER SUCCESSFUL RIDE
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void testFivePercentCommissionDeductedOnRideCompletion() throws Exception {
        // Driver starts with ₹500.00 balance
        testDriver.setWalletBalance(500.00);
        driverRepository.save(testDriver);

        DriverWallet wallet = driverWalletRepository.findByDriverId(String.valueOf(testDriver.getId())).orElseThrow();
        wallet.setAvailableBalance(500.00);
        driverWalletRepository.save(wallet);

        // Create an order of ₹1000.00
        Order order = new Order();
        order.setBookingId("BK_RIDE_COMPL_1");
        order.setAmount(1000.00);
        order.setStatus("assigned");
        order.setDriverId(String.valueOf(testDriver.getId()));
        order.setDriverEmail(testDriver.getEmail());
        order.setDriverPhone(testDriver.getPhone());
        order = orderRepository.save(order);

        // Deduct commission on completion (5% of 1000.00 = 50.00)
        WalletTransaction tx = driverWalletService.deductCommissionOnCompletion(
                String.valueOf(testDriver.getId()), order.getBookingId(), 1000.00
        );

        assertNotNull(tx);
        assertEquals("COMMISSION_DEDUCTION", tx.getTransactionType());
        assertEquals(-50.00, tx.getAmount());
        assertEquals(50.00, tx.getCommissionAmount());
        assertEquals(1000.00, tx.getGrossAmount());
        assertEquals(500.00, tx.getBalanceBefore());
        assertEquals(450.00, tx.getBalanceAfter());

        // Check driver entity updated
        Driver driverAfter = driverRepository.findById(testDriver.getId()).orElseThrow();
        assertEquals(450.00, driverAfter.getWalletBalance());

        // Check DriverWallet entity updated
        DriverWallet walletAfter = driverWalletRepository.findByDriverId(String.valueOf(testDriver.getId())).orElseThrow();
        assertEquals(450.00, walletAfter.getAvailableBalance());
        assertEquals(50.00, walletAfter.getPlatformCommission());
    }

    @Test
    void testCommissionDeductionCausesNegativeBalanceAndBlocksSubsequentAcceptance() throws Exception {
        // Driver starts with ₹20.00
        testDriver.setWalletBalance(20.00);
        driverRepository.save(testDriver);

        DriverWallet wallet = driverWalletRepository.findByDriverId(String.valueOf(testDriver.getId())).orElseThrow();
        wallet.setAvailableBalance(20.00);
        driverWalletRepository.save(wallet);

        // Order fare is ₹1000.00 -> 5% commission is ₹50.00
        // Balance will drop to 20 - 50 = -30.00
        driverWalletService.deductCommissionOnCompletion(String.valueOf(testDriver.getId()), "BK_LOW_BAL_1", 1000.00);

        Driver driverAfter = driverRepository.findById(testDriver.getId()).orElseThrow();
        assertEquals(-30.00, driverAfter.getWalletBalance());
        assertEquals("offline", driverAfter.getStatus()); // auto-switched to offline

        // Driver now tries to accept another ride -> blocked because balance is negative (-30.00)
        Order nextOrder = new Order();
        nextOrder.setBookingId("BK_NEXT_RIDE_1");
        nextOrder.setAmount(400.00);
        nextOrder.setStatus("placed");
        nextOrder = orderRepository.save(nextOrder);

        mockMvc.perform(post("/api/driver/orders/" + nextOrder.getBookingId() + "/accept")
                        .header("Authorization", "Bearer " + driverJwt))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.error", is("INSUFFICIENT_WALLET_BALANCE")))
                .andExpect(jsonPath("$.message", containsString("Driver wallet balance must be greater than ₹0")));
    }
}
