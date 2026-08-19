package com.anushaporter.backend;

import com.anushaporter.backend.model.Driver;
import com.anushaporter.backend.model.DriverWallet;
import com.anushaporter.backend.model.WithdrawalRequest;
import com.anushaporter.backend.repository.DriverRepository;
import com.anushaporter.backend.repository.DriverWalletRepository;
import com.anushaporter.backend.repository.WalletTransactionRepository;
import com.anushaporter.backend.repository.WithdrawalRequestRepository;
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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = BackendApplication.class)
@Import(DriverWalletIntegrationTest.TestConfig.class)
public class DriverWalletIntegrationTest {

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
    private WithdrawalRequestRepository withdrawalRequestRepository;

    @Autowired
    private WalletTransactionRepository walletTransactionRepository;

    @Autowired
    private com.anushaporter.backend.service.DriverWalletService driverWalletService;

    @Autowired
    private JwtUtil jwtUtil;

    private Driver testDriver;
    private String jwtToken;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();

        // Clear database in correct order of dependency
        walletTransactionRepository.deleteAll();
        withdrawalRequestRepository.deleteAll();
        driverWalletRepository.deleteAll();
        driverRepository.deleteAll();

        // Seed a driver
        testDriver = new Driver();
        testDriver.setName("Supriya Rao");
        testDriver.setEmail("supriya@example.com");
        testDriver.setPhone("9876543210");
        testDriver.setVehicleNumber("TS09AB1234");
        testDriver.setVehicleType("Tata Ace");
        testDriver.setStatus("online");
        testDriver.setKyc("verified");
        testDriver.setAccountNumber("1234567890");
        testDriver.setBankName("AU Small Finance Bank");
        testDriver = driverRepository.save(testDriver);

        // Seed driver's wallet with initial balance
        DriverWallet wallet = new DriverWallet();
        wallet.setDriverId(String.valueOf(testDriver.getId()));
        wallet.setAvailableBalance(2000.00);
        wallet.setPendingBalance(0.00);
        wallet.setTotalEarned(3000.00);
        wallet.setTotalWithdrawn(1000.00);
        wallet.setPlatformCommission(150.00);
        driverWalletRepository.save(wallet);

        jwtToken = jwtUtil.generateToken(testDriver.getEmail());
    }

    @Test
    void testGetWalletEndpoint() throws Exception {
        mockMvc.perform(get("/api/driver/wallet")
                .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                // Root level checks
                .andExpect(jsonPath("$.availableBalance", is(2000.00)))
                .andExpect(jsonPath("$.pendingBalance", is(0.00)))
                .andExpect(jsonPath("$.totalEarned", is(3000.00)))
                .andExpect(jsonPath("$.totalWithdrawn", is(1000.00)))
                .andExpect(jsonPath("$.platformCommission", is(150.00)))
                // Nested level checks
                .andExpect(jsonPath("$.wallet.availableBalance", is(2000.00)))
                .andExpect(jsonPath("$.wallet.pendingBalance", is(0.00)))
                .andExpect(jsonPath("$.wallet.totalEarned", is(3000.00)))
                .andExpect(jsonPath("$.wallet.totalWithdrawn", is(1000.00)))
                .andExpect(jsonPath("$.wallet.platformCommission", is(150.00)))
                .andExpect(jsonPath("$.wallet.isPayoutEligible", is(true)));
    }

    @Test
    void testRequestWithdrawalSuccess() throws Exception {
        mockMvc.perform(post("/api/driver/withdrawals")
                .header("Authorization", "Bearer " + jwtToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\": 500.00, \"status\": \"PENDING_ADMIN_APPROVAL\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.message", containsString("submitted for Admin approval")))
                .andExpect(jsonPath("$.availableBalance", is(1500.00)))
                .andExpect(jsonPath("$.heldAmount", is(500.00)))
                .andExpect(jsonPath("$.request.amount", is(500.00)))
                .andExpect(jsonPath("$.request.heldAmount", is(500.00)))
                .andExpect(jsonPath("$.request.status", is("PENDING_ADMIN_APPROVAL")));

        // Verify in DB directly
        DriverWallet walletInDb = driverWalletRepository.findByDriverId(String.valueOf(testDriver.getId())).orElseThrow();
        assertEquals(1500.00, walletInDb.getAvailableBalance());
        assertEquals(500.00, walletInDb.getPendingBalance());
    }

    @Test
    void testRequestWithdrawalInsufficientBalance() throws Exception {
        mockMvc.perform(post("/api/driver/withdrawals")
                .header("Authorization", "Bearer " + jwtToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\": 2500.00, \"status\": \"PENDING_ADMIN_APPROVAL\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", containsString("Insufficient available balance")));
    }

    @Test
    void testRequestWithdrawalBelowMinimum() throws Exception {
        mockMvc.perform(post("/api/driver/withdrawals")
                .header("Authorization", "Bearer " + jwtToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\": 50.00, \"status\": \"PENDING_ADMIN_APPROVAL\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", containsString("Minimum payout amount is 100.00")));
    }

    @Test
    void testRequestWithdrawalDuplicatePending() throws Exception {
        // Create first pending request
        mockMvc.perform(post("/api/driver/withdrawals")
                .header("Authorization", "Bearer " + jwtToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\": 500.00, \"status\": \"PENDING_ADMIN_APPROVAL\"}"))
                .andExpect(status().isCreated());

        // Attempt second pending request (should fail)
        mockMvc.perform(post("/api/driver/withdrawals")
                .header("Authorization", "Bearer " + jwtToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\": 200.00, \"status\": \"PENDING_ADMIN_APPROVAL\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", containsString("already have a pending withdrawal request")));
    }

    @Test
    void testGetActiveWithdrawal() throws Exception {
        // 1. Initially no active request
        mockMvc.perform(get("/api/driver/withdrawals/active")
                .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.request", nullValue()));

        // 2. Submit a request (becomes PENDING_ADMIN_APPROVAL)
        mockMvc.perform(post("/api/driver/withdrawals")
                .header("Authorization", "Bearer " + jwtToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\": 500.00}"))
                .andExpect(status().isCreated());

        // 3. Verify it is returned as active
        mockMvc.perform(get("/api/driver/withdrawals/active")
                .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.request.amount", is(500.00)))
                .andExpect(jsonPath("$.request.status", is("PENDING_ADMIN_APPROVAL")))
                .andExpect(jsonPath("$.request.bankName", is("AU Small Finance Bank")))
                .andExpect(jsonPath("$.request.accountNumberMasked", is("•••• 7890")));

        // 4. Update request to PROCESSING and verify it is still active
        List<WithdrawalRequest> requests = withdrawalRequestRepository.findByDriverIdOrderByRequestedAtDesc(String.valueOf(testDriver.getId()));
        WithdrawalRequest req = requests.get(0);
        req.setStatus("PROCESSING");
        withdrawalRequestRepository.save(req);

        mockMvc.perform(get("/api/driver/withdrawals/active")
                .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.request.status", is("PROCESSING")));
    }

    @Test
    void testGetWithdrawalHistory() throws Exception {
        // 1. Submit a pending request
        mockMvc.perform(post("/api/driver/withdrawals")
                .header("Authorization", "Bearer " + jwtToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\": 500.00}"))
                .andExpect(status().isCreated());

        // 2. History should be empty because active requests are excluded
        mockMvc.perform(get("/api/driver/withdrawals/history")
                .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.withdrawals", hasSize(0)));

        // 3. Complete the request and verify it appears in history
        List<WithdrawalRequest> requests = withdrawalRequestRepository.findByDriverIdOrderByRequestedAtDesc(String.valueOf(testDriver.getId()));
        WithdrawalRequest req = requests.get(0);
        req.setStatus("COMPLETED");
        withdrawalRequestRepository.save(req);

        mockMvc.perform(get("/api/driver/withdrawals/history")
                .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.withdrawals", hasSize(1)))
                .andExpect(jsonPath("$.withdrawals[0].status", is("COMPLETED")))
                .andExpect(jsonPath("$.withdrawals[0].amount", is(500.00)));
    }

    @Test
    void testDeductCommissionIdempotency() {
        // First deduction for order ORD-100 (500 fare * 5% = 25 commission)
        driverWalletService.deductCommissionOnCompletion(String.valueOf(testDriver.getId()), "ORD-100", 500.00);

        Driver driverAfterFirst = driverRepository.findById(testDriver.getId()).orElseThrow();
        assertEquals(2000.00 - 25.00, driverAfterFirst.getWalletBalance());

        // Second deduction call for same order ORD-100 must be a no-op (idempotent)
        driverWalletService.deductCommissionOnCompletion(String.valueOf(testDriver.getId()), "ORD-100", 500.00);

        Driver driverAfterSecond = driverRepository.findById(testDriver.getId()).orElseThrow();
        assertEquals(1975.00, driverAfterSecond.getWalletBalance());

        // Verify only 1 transaction exists for ORD-100
        List<com.anushaporter.backend.model.WalletTransaction> txs = walletTransactionRepository.findByOrderId("ORD-100");
        assertEquals(1, txs.size());
        assertEquals("COMMISSION_DEDUCTION", txs.get(0).getTransactionType());
    }

    @Test
    void testCleanDuplicateCommissionTransactionsRemovesLegacyRowsAndRecalculatesBalance() {
        // Setup: Driver has a COMMISSION_DEDUCTION and an erroneous legacy COMMISSION row for ORD-200
        com.anushaporter.backend.model.WalletTransaction tx1 = new com.anushaporter.backend.model.WalletTransaction();
        tx1.setId("TXN_1");
        tx1.setDriverId(String.valueOf(testDriver.getId()));
        tx1.setOrderId("ORD-200");
        tx1.setTransactionType("COMMISSION_DEDUCTION");
        tx1.setAmount(-25.00);
        tx1.setBalanceBefore(2000.00);
        tx1.setBalanceAfter(1975.00);
        tx1.setStatus("SUCCESS");
        walletTransactionRepository.save(tx1);

        com.anushaporter.backend.model.WalletTransaction tx2 = new com.anushaporter.backend.model.WalletTransaction();
        tx2.setId("TXN_2");
        tx2.setDriverId(String.valueOf(testDriver.getId()));
        tx2.setOrderId("ORD-200");
        tx2.setTransactionType("COMMISSION");
        tx2.setAmount(-25.00);
        tx2.setBalanceBefore(1975.00);
        tx2.setBalanceAfter(1950.00);
        tx2.setStatus("SUCCESS");
        walletTransactionRepository.save(tx2);

        // Erroneously double-deducted balance
        testDriver.setWalletBalance(1950.00);
        driverRepository.save(testDriver);

        // Run data cleanup
        Map<String, Object> result = driverWalletService.cleanDuplicateCommissionTransactions();
        assertEquals(true, result.get("success"));
        assertEquals(1, result.get("deletedDuplicatesCount"));

        // Verify legacy COMMISSION was deleted
        List<com.anushaporter.backend.model.WalletTransaction> txsAfter = walletTransactionRepository.findByOrderId("ORD-200");
        assertEquals(1, txsAfter.size());
        assertEquals("COMMISSION_DEDUCTION", txsAfter.get(0).getTransactionType());

        // Verify balance was restored accurately (1975.00 instead of 1950.00)
        Driver driverFixed = driverRepository.findById(testDriver.getId()).orElseThrow();
        assertEquals(1975.00, driverFixed.getWalletBalance());
    }
}
