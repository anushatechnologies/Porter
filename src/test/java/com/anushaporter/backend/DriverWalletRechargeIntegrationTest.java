package com.anushaporter.backend;

import com.anushaporter.backend.model.AppUser;
import com.anushaporter.backend.model.Driver;
import com.anushaporter.backend.model.DriverWallet;
import com.anushaporter.backend.model.GlobalSettings;
import com.anushaporter.backend.model.Order;
import com.anushaporter.backend.model.WalletTransaction;
import com.anushaporter.backend.repository.*;
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

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = BackendApplication.class)
@Import(DriverWalletRechargeIntegrationTest.TestConfig.class)
public class DriverWalletRechargeIntegrationTest {

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
    private DriverWalletService driverWalletService;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private AppUserRepository appUserRepository;

    private Driver testDriver;
    private String jwtToken;
    private String adminToken;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();

        walletTransactionRepository.deleteAll();
        paymentOrderRepository.deleteAll();
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
        adminToken = jwtUtil.generateToken("admin@anushaporter.com");

        // Configure Admin Settings
        GlobalSettings s1 = new GlobalSettings();
        s1.setSettingKey("wallet_commission_percentage");
        s1.setSettingValue("5.0");
        globalSettingsRepository.save(s1);

        GlobalSettings s2 = new GlobalSettings();
        s2.setSettingKey("wallet_min_required_balance");
        s2.setSettingValue("100.0");
        globalSettingsRepository.save(s2);

        GlobalSettings s3 = new GlobalSettings();
        s3.setSettingKey("wallet_required_for_rides");
        s3.setSettingValue("true");
        globalSettingsRepository.save(s3);

        GlobalSettings s4 = new GlobalSettings();
        s4.setSettingKey("wallet_auto_offline_insufficient");
        s4.setSettingValue("true");
        globalSettingsRepository.save(s4);

        // Seed Driver
        testDriver = new Driver();
        testDriver.setName("Rajesh Kumar");
        testDriver.setEmail("rajesh.driver@anushaporter.com");
        testDriver.setPhone("9876543210");
        testDriver.setVehicleNumber("TS09AB1234");
        testDriver.setVehicleType("Tata Ace");
        testDriver.setStatus("online");
        testDriver.setKyc("verified");
        testDriver.setAccountNumber("1234567890");
        testDriver.setBankName("AU Small Finance Bank");
        testDriver = driverRepository.save(testDriver);

        // Seed Wallet with initial balance = 500.00
        DriverWallet wallet = new DriverWallet();
        wallet.setDriverId(String.valueOf(testDriver.getId()));
        wallet.setAvailableBalance(500.00);
        wallet.setTotalEarned(12500.00);
        wallet.setPlatformCommission(625.00);
        driverWalletRepository.save(wallet);

        jwtToken = jwtUtil.generateToken(testDriver.getEmail());
    }

    @Test
    void testGetDriverWalletBalanceAndEligibility() throws Exception {
        mockMvc.perform(get("/api/driver/wallet")
                .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.wallet.availableBalance", is(500.00)))
                .andExpect(jsonPath("$.wallet.totalEarned", is(12500.00)))
                .andExpect(jsonPath("$.wallet.platformCommission", is(625.00)))
                .andExpect(jsonPath("$.wallet.commissionPercentage", is(5.0)))
                .andExpect(jsonPath("$.wallet.minRequiredBalance", is(100.0)))
                .andExpect(jsonPath("$.wallet.isEligible", is(true)))
                .andExpect(jsonPath("$.wallet.eligibilityReason", is("Sufficient balance")));
    }

    @Test
    void testGetDriverWalletViaMeWalletEndpoint() throws Exception {
        mockMvc.perform(get("/api/drivers/me/wallet")
                .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.wallet.availableBalance", is(500.00)))
                .andExpect(jsonPath("$.wallet.isEligible", is(true)));
    }

    @Test
    void testCreateRazorpayRechargeOrder() throws Exception {
        String bookingId = "RECH_" + System.currentTimeMillis();
        mockMvc.perform(post("/api/payments/razorpay/create-order")
                .header("Authorization", "Bearer " + jwtToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"amount\": 500.00, \"bookingId\": \"%s\", \"currency\": \"INR\", \"paymentMethod\": \"RAZORPAY\"}", bookingId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.razorpayOrderId", notNullValue()))
                .andExpect(jsonPath("$.keyId", notNullValue()))
                .andExpect(jsonPath("$.amount", is(500.00)))
                .andExpect(jsonPath("$.currency", is("INR")));
    }

    @Test
    void testVerifyRazorpayPaymentAndCreditWallet() throws Exception {
        String bookingId = "RECH_" + System.currentTimeMillis();

        mockMvc.perform(post("/api/payments/razorpay/verify")
                .header("Authorization", "Bearer " + jwtToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\n" +
                        "  \"razorpay_payment_id\": \"pay_29QQoUBi66xm2f\",\n" +
                        "  \"razorpay_order_id\": \"order_EKwxwAgItmmXdp\",\n" +
                        "  \"razorpay_signature\": \"test_signature_valid\",\n" +
                        "  \"bookingId\": \"%s\",\n" +
                        "  \"amount\": 500.00\n" +
                        "}", bookingId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.message", containsString("Payment verified and wallet credited successfully")))
                .andExpect(jsonPath("$.wallet.availableBalance", is(1000.00)))
                .andExpect(jsonPath("$.wallet.isEligible", is(true)));

        // Verify in DB
        DriverWallet inDb = driverWalletRepository.findByDriverId(String.valueOf(testDriver.getId())).orElseThrow();
        assertEquals(1000.00, inDb.getAvailableBalance());

        // Verify Transaction in DB
        List<WalletTransaction> txs = walletTransactionRepository.findByDriverIdOrderByCreatedAtDesc(String.valueOf(testDriver.getId()));
        assertEquals(1, txs.size());
        assertEquals("RECHARGE", txs.get(0).getTransactionType());
        assertEquals(500.00, txs.get(0).getAmount());
        assertEquals("SUCCESS", txs.get(0).getStatus());
        assertEquals("pay_29QQoUBi66xm2f", txs.get(0).getReferenceId());
    }

    @Test
    void testGetWalletTransactionsEndpoint() throws Exception {
        // Create 2 transactions
        driverWalletService.rechargeWallet(String.valueOf(testDriver.getId()), 500.00, "pay_111", "Wallet Recharge (Razorpay)");
        driverWalletService.processOrderEarning(String.valueOf(testDriver.getId()), "12345", 1000.00);

        mockMvc.perform(get("/api/driver/wallet/transactions")
                .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.transactions", hasSize(3))) // 1 recharge + 1 earning + 1 commission
                .andExpect(jsonPath("$.transactions[0].transactionType", notNullValue()));
    }

    @Test
    void testAdminWalletSettingsGetAndUpdate() throws Exception {
        // 1. GET /api/admin/settings/wallet
        mockMvc.perform(get("/api/admin/settings/wallet")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.commissionPercentage", is(5.0)))
                .andExpect(jsonPath("$.minRequiredBalance", is(100.0)))
                .andExpect(jsonPath("$.walletRequiredForRides", is(true)))
                .andExpect(jsonPath("$.autoOfflineWhenBalanceInsufficient", is(true)));

        // 2. POST /api/admin/settings/wallet
        mockMvc.perform(post("/api/admin/settings/wallet")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\n" +
                        "  \"commissionPercentage\": 8.0,\n" +
                        "  \"minRequiredBalance\": 250.0,\n" +
                        "  \"walletRequiredForRides\": true,\n" +
                        "  \"autoOfflineWhenBalanceInsufficient\": true\n" +
                        "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.commissionPercentage", is(8.0)))
                .andExpect(jsonPath("$.minRequiredBalance", is(250.0)));

        // Verify updated in service
        assertEquals(8.0, driverWalletService.getCommissionPercentage());
        assertEquals(250.0, driverWalletService.getMinRequiredBalance());
    }

    @Test
    void testAutoOfflineWhenBalanceDropsBelowMinimum() throws Exception {
        // Set min required balance = 600.0
        driverWalletService.updateAdminWalletSettings(java.util.Map.of("minRequiredBalance", 600.0));

        // Driver currently has 500 balance, which is < 600.0
        assertFalse(driverWalletService.isDriverEligibleForRides(String.valueOf(testDriver.getId())));
        assertEquals("Insufficient balance. Recharge wallet to accept rides.",
                driverWalletService.getEligibilityReason(String.valueOf(testDriver.getId())));
    }
}
