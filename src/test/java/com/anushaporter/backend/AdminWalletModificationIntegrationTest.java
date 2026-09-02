package com.anushaporter.backend;

import com.anushaporter.backend.model.AppUser;
import com.anushaporter.backend.model.Customer;
import com.anushaporter.backend.model.Driver;
import com.anushaporter.backend.model.DriverWallet;
import com.anushaporter.backend.model.WalletTransaction;
import com.anushaporter.backend.repository.AppUserRepository;
import com.anushaporter.backend.repository.CustomerRepository;
import com.anushaporter.backend.repository.DriverRepository;
import com.anushaporter.backend.repository.DriverWalletRepository;
import com.anushaporter.backend.repository.WalletTransactionRepository;
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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = BackendApplication.class)
@Import(AdminWalletModificationIntegrationTest.TestConfig.class)
public class AdminWalletModificationIntegrationTest {

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
    private CustomerRepository customerRepository;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private JwtUtil jwtUtil;

    private Driver testDriver;
    private Customer testCustomer;
    private AppUser testUser;
    private String adminJwt;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();

        walletTransactionRepository.deleteAll();
        driverWalletRepository.deleteAll();
        driverRepository.deleteAll();
        customerRepository.deleteAll();
        appUserRepository.deleteAll();

        // 1. Seed Driver
        testDriver = new Driver();
        testDriver.setName("Rajesh Pilot");
        testDriver.setEmail("rajesh.pilot@example.com");
        testDriver.setPhone("9876543210");
        testDriver.setStatus("offline");
        testDriver.setWalletBalance(0.0);
        testDriver = driverRepository.save(testDriver);

        DriverWallet wallet = new DriverWallet();
        wallet.setDriverId(String.valueOf(testDriver.getId()));
        wallet.setAvailableBalance(0.0);
        driverWalletRepository.save(wallet);

        // 2. Seed Customer and matching AppUser
        testCustomer = new Customer();
        testCustomer.setName("Anjali Sharma");
        testCustomer.setEmail("anjali@example.com");
        testCustomer.setPhone("9123456780");
        testCustomer.setWallet(100.0);
        testCustomer = customerRepository.save(testCustomer);

        testUser = new AppUser();
        testUser.setName("Anjali Sharma");
        testUser.setEmail("anjali@example.com");
        testUser.setPhone("9123456780");
        testUser.setRole("customer");
        testUser.setWalletBalance(100.0);
        testUser = appUserRepository.save(testUser);

        adminJwt = jwtUtil.generateToken("admin@anushaporter.com");
    }

    @Test
    void testAdminSetDriverWalletBalance() throws Exception {
        String payload = """
                {
                    "walletBalance": 850.0,
                    "reason": "Admin initial balance grant"
                }
                """;

        mockMvc.perform(put("/api/admin/drivers/" + testDriver.getId() + "/wallet")
                        .header("Authorization", "Bearer " + adminJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.walletBalance", is(850.0)))
                .andExpect(jsonPath("$.previousBalance", is(0.0)))
                .andExpect(jsonPath("$.status", is("online")));

        // Verify driver entity in DB
        Driver updatedDriver = driverRepository.findById(testDriver.getId()).orElseThrow();
        assertEquals(850.0, updatedDriver.getWalletBalance());
        assertEquals("online", updatedDriver.getStatus());

        // Verify driver wallet entity in DB
        DriverWallet updatedWallet = driverWalletRepository.findByDriverId(String.valueOf(testDriver.getId())).orElseThrow();
        assertEquals(850.0, updatedWallet.getAvailableBalance());

        // Verify transaction audit log
        List<WalletTransaction> txs = walletTransactionRepository.findByDriverId(String.valueOf(testDriver.getId()));
        assertFalse(txs.isEmpty());
        WalletTransaction tx = txs.get(0);
        assertEquals("ADMIN_ADJUSTMENT", tx.getTransactionType());
        assertEquals(850.0, tx.getAmount());
        assertEquals(850.0, tx.getBalanceAfter());
    }

    @Test
    void testAdminCreditAndDebitDriverWallet() throws Exception {
        // First credit 300
        String creditPayload = """
                {
                    "amount": 300.0,
                    "action": "credit",
                    "reason": "Bonus compensation"
                }
                """;

        mockMvc.perform(post("/api/admin/drivers/" + testDriver.getId() + "/wallet")
                        .header("Authorization", "Bearer " + adminJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(creditPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.walletBalance", is(300.0)));

        // Then debit 100
        String debitPayload = """
                {
                    "amount": 100.0,
                    "action": "debit",
                    "reason": "Manual penalty"
                }
                """;

        mockMvc.perform(put("/api/admin/drivers/" + testDriver.getId() + "/wallet")
                        .header("Authorization", "Bearer " + adminJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(debitPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.walletBalance", is(200.0)));

        Driver updatedDriver = driverRepository.findById(testDriver.getId()).orElseThrow();
        assertEquals(200.0, updatedDriver.getWalletBalance());
    }

    @Test
    void testAdminModifyCustomerWalletAndSyncsAppUser() throws Exception {
        String payload = """
                {
                    "walletBalance": 500.0,
                    "action": "set"
                }
                """;

        mockMvc.perform(put("/api/admin/customers/" + testCustomer.getId() + "/wallet")
                        .header("Authorization", "Bearer " + adminJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.walletBalance", is(500.0)));

        Customer updatedCustomer = customerRepository.findById(testCustomer.getId()).orElseThrow();
        assertEquals(500.0, updatedCustomer.getWallet());

        AppUser updatedUser = appUserRepository.findById(testUser.getId()).orElseThrow();
        assertEquals(500.0, updatedUser.getWalletBalance());
    }

    @Test
    void testUnifiedWalletModifyEndpoint() throws Exception {
        // Modifying driver via unified endpoint
        String driverPayload = """
                {
                    "userType": "driver",
                    "id": "%d",
                    "amount": 420.0,
                    "action": "set"
                }
                """.formatted(testDriver.getId());

        mockMvc.perform(post("/api/admin/wallet/modify")
                        .header("Authorization", "Bearer " + adminJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(driverPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.walletBalance", is(420.0)));

        // Modifying customer via unified endpoint
        String customerPayload = """
                {
                    "userType": "customer",
                    "id": "%d",
                    "amount": 250.0,
                    "action": "set"
                }
                """.formatted(testCustomer.getId());

        mockMvc.perform(post("/api/admin/wallet/modify")
                        .header("Authorization", "Bearer " + adminJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(customerPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.walletBalance", is(250.0)));
    }

    @Test
    void testAdminGetAndModifyMinimumWalletBalance() throws Exception {
        // 1. GET current minimum balance (default 1000.0)
        mockMvc.perform(get("/api/admin/wallet/minimum-balance")
                        .header("Authorization", "Bearer " + adminJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.minRequiredBalance", is(1000.0)));

        // 2. Modify minimum balance to 1500.0 without applying to existing drivers
        String updatePayload = """
                {
                    "minimumBalance": 1500.0,
                    "applyToExistingDrivers": false
                }
                """;

        mockMvc.perform(put("/api/admin/wallet/minimum-balance")
                        .header("Authorization", "Bearer " + adminJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatePayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.minRequiredBalance", is(1500.0)));

        // 3. Modify minimum balance to 1000.0 AND apply to existing drivers (driver balance was 0.0, should now be 1000.0)
        String applyPayload = """
                {
                    "minimumBalance": 1000.0,
                    "applyToExistingDrivers": true,
                    "reason": "Platform policy minimum balance update"
                }
                """;

        mockMvc.perform(put("/api/admin/settings/wallet/minimum-balance")
                        .header("Authorization", "Bearer " + adminJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(applyPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.minRequiredBalance", is(1000.0)))
                .andExpect(jsonPath("$.appliedToExistingDrivers", is(true)))
                .andExpect(jsonPath("$.driversUpdated", is(1)));

        // Verify driver balance in DB was elevated to minimum 1000.0
        Driver updatedDriver = driverRepository.findById(testDriver.getId()).orElseThrow();
        assertEquals(1000.0, updatedDriver.getWalletBalance());
        assertEquals("online", updatedDriver.getStatus());
    }
}

