package com.anushaporter.backend;

import com.anushaporter.backend.model.AppUser;
import com.anushaporter.backend.model.Driver;
import com.anushaporter.backend.model.DriverWallet;
import com.anushaporter.backend.model.Order;
import com.anushaporter.backend.model.WalletTransaction;
import com.anushaporter.backend.repository.AppUserRepository;
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

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = BackendApplication.class)
@Import(DriverRideAcceptanceAndRegistrationWalletTest.TestConfig.class)
public class DriverRideAcceptanceAndRegistrationWalletTest {

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
    private AppUserRepository appUserRepository;

    @Autowired
    private DriverWalletService driverWalletService;

    @Autowired
    private JwtUtil jwtUtil;

    private String adminToken;

    @BeforeEach
    public void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(this.webApplicationContext).build();

        // Ensure Admin user exists
        AppUser admin = appUserRepository.findByEmail("admin_wallet_test@porter.com").orElse(null);
        if (admin == null) {
            admin = new AppUser();
            admin.setName("Admin Tester");
            admin.setEmail("admin_wallet_test@porter.com");
            admin.setPhone("9999999990");
            admin.setRole("ADMIN");
            admin = appUserRepository.save(admin);
        }
        adminToken = jwtUtil.generateToken(admin.getEmail());

        // Reset default registration minimum balance to 1000.0
        driverWalletService.updateRegistrationMinBalance(1000.0);
    }

    private String createDriverWithToken(String phone, String email, double initialBalance) {
        Driver driver = new Driver();
        driver.setName("Test Driver " + phone);
        driver.setPhone(phone);
        driver.setEmail(email);
        driver.setKyc("approved");
        driver.setVerificationStatus("approved");
        driver.setVehicle("Tata Ace");
        driver.setVehicleType("2 Wheeler / Tata Ace");
        driver.setVehicleNumber("KA-01-AB-" + (phone.length() >= 4 ? phone.substring(phone.length() - 4) : "1234"));
        driver.setWalletBalance(initialBalance);
        driver.setStatus(initialBalance > 0 ? "online" : "offline");
        Driver saved = driverRepository.save(driver);

        DriverWallet wallet = driverWalletService.getWallet(String.valueOf(saved.getId()));
        wallet.setAvailableBalance(initialBalance);
        driverWalletRepository.save(wallet);

        AppUser user = appUserRepository.findByEmail(email).orElseGet(() -> {
            AppUser u = new AppUser();
            u.setEmail(email);
            u.setPhone(phone);
            u.setName(driver.getName());
            u.setRole("DRIVER");
            return appUserRepository.save(u);
        });

        return jwtUtil.generateToken(user.getEmail());
    }

    @Test
    public void testDriverRegistrationInitializesMinimumBalance1000AndSetsOnline() throws Exception {
        String phone = "9888888801";
        String email = "regdriver01@porter.com";

        // Pre-create user for registration
        AppUser user = new AppUser();
        user.setName("Reg Driver One");
        user.setPhone(phone);
        user.setEmail(email);
        user.setRole("DRIVER");
        appUserRepository.save(user);
        String token = jwtUtil.generateToken(email);

        // Driver submits final registration
        mockMvc.perform(post("/api/driver/register")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {
                            "name": "Reg Driver One",
                            "phone": "%s",
                            "email": "%s",
                            "vehicle": "Tata Ace",
                            "vehicleType": "Tata Ace",
                            "vehicleNumber": "KA-05-CD-1111",
                            "submit": true
                        }
                        """.formatted(phone, email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.walletBalance").value(1000.0))
                .andExpect(jsonPath("$.registrationMinBalance").value(1000.0));

        Driver driver = driverRepository.findByPhone(phone).orElseThrow();
        assertEquals(1000.0, driver.getWalletBalance());
        assertEquals("online", driver.getStatus());

        DriverWallet wallet = driverWalletService.getWallet(String.valueOf(driver.getId()));
        assertEquals(1000.0, wallet.getAvailableBalance());

        List<WalletTransaction> txs = walletTransactionRepository.findByDriverIdOrderByCreatedAtDesc(String.valueOf(driver.getId()));
        assertFalse(txs.isEmpty());
        assertEquals("REGISTRATION_MINIMUM_CREDIT", txs.get(0).getTransactionType());
        assertEquals(1000.0, txs.get(0).getGrossAmount());
    }

    @Test
    public void testAdminModifiesRegistrationMinimumBalance() throws Exception {
        // Admin updates registration minimum to ₹1,500
        mockMvc.perform(put("/api/admin/settings/wallet/registration-minimum-balance")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {
                            "registrationMinBalance": 1500.0
                        }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.registrationMinBalance").value(1500.0));

        // Verify GET
        mockMvc.perform(get("/api/admin/settings/wallet/registration-minimum-balance")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registrationMinBalance").value(1500.0));

        // Now register new driver -> should get 1500.0 initial balance
        String phone = "9888888802";
        String email = "regdriver02@porter.com";
        AppUser user = new AppUser();
        user.setName("Reg Driver Two");
        user.setPhone(phone);
        user.setEmail(email);
        user.setRole("DRIVER");
        appUserRepository.save(user);
        String token = jwtUtil.generateToken(email);

        mockMvc.perform(post("/api/driver/register")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {
                            "name": "Reg Driver Two",
                            "phone": "%s",
                            "email": "%s",
                            "vehicle": "Pickup 8ft",
                            "vehicleType": "Pickup 8ft",
                            "vehicleNumber": "KA-05-CD-2222",
                            "submit": true
                        }
                        """.formatted(phone, email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.walletBalance").value(1500.0))
                .andExpect(jsonPath("$.registrationMinBalance").value(1500.0));

        Driver driver = driverRepository.findByPhone(phone).orElseThrow();
        assertEquals(1500.0, driver.getWalletBalance());
        assertEquals("online", driver.getStatus());
    }

    @Test
    public void testRideAcceptancePreconditionRejectionAndRemainingTopupFlow() throws Exception {
        // Driver with low wallet balance: ₹20.0
        String phone = "9888888803";
        String email = "driver03@porter.com";
        String token = createDriverWithToken(phone, email, 20.0);
        Driver driver = driverRepository.findByPhone(phone).orElseThrow();

        // Create an order of ₹1000 (5% commission = ₹50)
        String bookingId = "BOOK_TEST_" + System.currentTimeMillis();
        Order order = new Order();
        order.setBookingId(bookingId);
        order.setAmount(1000.0);
        order.setStatus("SEARCHING");
        order.setPickupAddress("Indiranagar, Bangalore");
        order.setDropAddress("Koramangala, Bangalore");
        order.setCreatedAt(LocalDateTime.now());
        order = orderRepository.save(order);

        // 1. Driver attempts to accept ride -> Rejection expected (needs ₹50, has ₹20, shortfall = ₹30)
        mockMvc.perform(post("/api/driver/orders/" + bookingId + "/accept")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INSUFFICIENT_WALLET_BALANCE"))
                .andExpect(jsonPath("$.requiredCommission").value(50.0))
                .andExpect(jsonPath("$.currentWalletBalance").value(20.0))
                .andExpect(jsonPath("$.remainingAmount").value(30.0))
                .andExpect(jsonPath("$.canAccept").value(false))
                .andExpect(jsonPath("$.rechargeRequired").value(true));

        // 2. Driver checks wallet eligibility via check endpoint
        mockMvc.perform(get("/api/driver/orders/" + bookingId + "/ride-wallet-check")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canAccept").value(false))
                .andExpect(jsonPath("$.requiredCommission").value(50.0))
                .andExpect(jsonPath("$.currentWalletBalance").value(20.0))
                .andExpect(jsonPath("$.remainingAmount").value(30.0));

        // 3. Driver uses remaining recharge endpoint to top up exact remaining shortfall (₹30.0)
        mockMvc.perform(post("/api/driver/orders/" + bookingId + "/recharge-remaining")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.rechargedAmount").value(30.0))
                .andExpect(jsonPath("$.newWalletBalance").value(50.0))
                .andExpect(jsonPath("$.canAccept").value(true));

        // 4. Now driver accepts the ride -> Success! 5% (₹50) deducted on acceptance
        mockMvc.perform(post("/api/driver/orders/" + bookingId + "/accept")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.commissionDeducted").value(50.0))
                .andExpect(jsonPath("$.walletBalance").value(0.0))
                .andExpect(jsonPath("$.driverStatus").value("offline")); // balance <= 0 sets driver offline

        // Verify driver entity in DB
        Driver updatedDriver = driverRepository.findById(driver.getId()).orElseThrow();
        assertEquals(0.0, updatedDriver.getWalletBalance());
        assertEquals("offline", updatedDriver.getStatus());

        // Verify commission deduction audit transaction
        List<WalletTransaction> deductions = walletTransactionRepository.findByDriverIdOrderByCreatedAtDesc(String.valueOf(driver.getId()));
        WalletTransaction commTx = deductions.stream()
                .filter(t -> "COMMISSION_DEDUCTION".equals(t.getTransactionType()))
                .findFirst().orElseThrow();
        assertEquals(1000.0, commTx.getGrossAmount());
        assertEquals(50.0, commTx.getCommissionAmount());
        assertEquals(-50.0, commTx.getAmount());
        assertEquals(50.0, commTx.getBalanceBefore());
        assertEquals(0.0, commTx.getBalanceAfter());

        // 5. Booking is cancelled -> 5% commission (₹50.0) is refunded to driver and driver is reactivated online!
        mockMvc.perform(put("/api/bookings/" + bookingId + "/cancel")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {
                            "cancellationReason": "Customer requested cancellation"
                        }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // Verify refund in driver entity and wallet
        Driver refundedDriver = driverRepository.findById(driver.getId()).orElseThrow();
        assertEquals(50.0, refundedDriver.getWalletBalance());
        assertEquals("online", refundedDriver.getStatus());

        DriverWallet refundedWallet = driverWalletService.getWallet(String.valueOf(driver.getId()));
        assertEquals(50.0, refundedWallet.getAvailableBalance());

        List<WalletTransaction> refunds = walletTransactionRepository.findByDriverIdOrderByCreatedAtDesc(String.valueOf(driver.getId()));
        WalletTransaction refTx = refunds.stream()
                .filter(t -> "COMMISSION_REFUND".equals(t.getTransactionType()))
                .findFirst().orElseThrow();
        assertEquals(50.0, refTx.getAmount());
        assertEquals(0.0, refTx.getBalanceBefore());
        assertEquals(50.0, refTx.getBalanceAfter());
    }
}
