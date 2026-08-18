package com.anushaporter.backend;

import com.anushaporter.backend.model.*;
import com.anushaporter.backend.repository.*;
import com.anushaporter.backend.service.payment.MockSandboxPaymentProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = BackendApplication.class)
@Import(PaymentLifecycleIntegrationTest.TestConfig.class)
public class PaymentLifecycleIntegrationTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        public S3Client mockS3Client() {
            return Mockito.mock(S3Client.class);
        }

        @Bean
        @Primary
        public S3Presigner mockS3Presigner() {
            return Mockito.mock(S3Presigner.class);
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
    private AppUserRepository appUserRepository;

    @Autowired
    private PaymentOrderRepository paymentOrderRepository;

    @Autowired
    private PaymentWebhookEventRepository webhookEventRepository;

    @Autowired
    private PaymentRefundRepository refundRepository;

    @Autowired
    private LedgerEntryRepository ledgerRepository;

    @Autowired
    private DriverEarningsRepository driverEarningsRepository;

    @Autowired
    private DriverPayoutAccountRepository payoutAccountRepository;

    @Autowired
    private DriverPayoutRecordRepository payoutRecordRepository;

    @Autowired
    private MockSandboxPaymentProvider paymentProvider;

    @Autowired
    private com.anushaporter.backend.util.JwtUtil jwtUtil;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private Driver testDriver;
    private AppUser testDriverUser;
    private Order testOrder;
    private String driverToken;
    private String adminToken;

    @Autowired
    private CommissionRuleRepository commissionRuleRepository;

    @BeforeEach
    public void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();

        // Clear previous state for deterministic test run
        refundRepository.deleteAll();
        webhookEventRepository.deleteAll();
        payoutRecordRepository.deleteAll();
        payoutAccountRepository.deleteAll();
        driverEarningsRepository.deleteAll();
        ledgerRepository.deleteAll();
        paymentOrderRepository.deleteAll();
        commissionRuleRepository.deleteAll();

        // Seed 10% default commission rule for payment lifecycle tests
        CommissionRule rule = new CommissionRule();
        rule.setRuleId("RULE_DEFAULT_10");
        rule.setServiceCategory("ALL");
        rule.setCommissionType("PERCENTAGE");
        rule.setPercentageRate(10.0);
        rule.setFixedAmount(0.0);
        rule.setIsActive(true);
        commissionRuleRepository.save(rule);

        // 1. Create Driver & AppUser
        String phone = "9876543210";
        testDriver = driverRepository.findByPhone(phone).orElseGet(() -> {
            Driver d = new Driver();
            d.setName("Ramesh Kumar");
            d.setPhone(phone);
            d.setEmail("ramesh.driver@anushaporter.com");
            d.setVehicleType("Tata Ace");
            d.setVehicleNumber("TS 09 EA 4582");
            d.setStatus("online");
            d.setKyc("approved");
            return driverRepository.save(d);
        });

        testDriverUser = appUserRepository.findFirstByPhoneOrderByIdDesc(phone).orElseGet(() -> {
            AppUser u = new AppUser();
            u.setName("Ramesh Kumar");
            u.setPhone(phone);
            u.setEmail("ramesh.driver@anushaporter.com");
            u.setRole("driver");
            u.setWalletBalance(0.0);
            return appUserRepository.save(u);
        });
        testDriverUser.setWalletBalance(0.0);
        appUserRepository.save(testDriverUser);

        driverToken = jwtUtil.generateToken(testDriver.getEmail());
        adminToken = jwtUtil.generateToken("admin@anushaporter.com");

        // 2. Create Order
        String bookingId = "BK_PAY_TEST_" + UUID.randomUUID().toString().substring(0, 6);
        testOrder = new Order();
        testOrder.setBookingId(bookingId);
        testOrder.setUserEmail("customer@test.com");
        testOrder.setReceiverName("Anusha Customer");
        testOrder.setReceiverPhone("9123456789");
        testOrder.setDriverId(testDriver.getId().toString());
        testOrder.setDriverName(testDriver.getName());
        testOrder.setDriverPhone(testDriver.getPhone());
        testOrder.setDriverVehicleNumber(testDriver.getVehicleNumber());
        testOrder.setAmount(500.0);
        testOrder.setBaseFare(149.0);
        testOrder.setDistanceFare(351.0);
        testOrder.setStatus("accepted");
        testOrder.setPaymentStatus("unpaid");
        testOrder = orderRepository.save(testOrder);
    }

    @Test
    @DisplayName("1. Ride Completion creates Payment Order with Dynamic UPI QR")
    public void testCreatePaymentOrderAndDynamicQr() throws Exception {
        mockMvc.perform(post("/api/payments/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "bookingId", testOrder.getBookingId(),
                                "paymentMethod", "UPI_QR"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.bookingId", is(testOrder.getBookingId())))
                .andExpect(jsonPath("$.amount", is(500.0)))
                .andExpect(jsonPath("$.status", is("PENDING")))
                .andExpect(jsonPath("$.qrCodeData", startsWith("upi://pay?pa=")))
                .andExpect(jsonPath("$.invoiceId", notNullValue()));

        PaymentOrder payment = paymentOrderRepository.findByBookingId(testOrder.getBookingId()).orElseThrow();
        assertEquals(PaymentStatus.PENDING, payment.getStatus());
        assertEquals(500.0, payment.getAmount());
    }

    @Test
    @DisplayName("2. Process Payment Success: Atomic double-entry ledger, 10% commission & driver earnings")
    public void testProcessPaymentSuccessAndDoubleEntryLedger() throws Exception {
        // First create payment order
        PaymentOrder payment = new PaymentOrder();
        payment.setPaymentId("PAY_TST_001");
        payment.setBookingId(testOrder.getBookingId());
        payment.setInvoiceId("INV_TST_001");
        payment.setCustomerId(testOrder.getUserEmail());
        payment.setDriverId(testDriver.getId().toString());
        payment.setAmount(500.0);
        payment.setStatus(PaymentStatus.PENDING);
        payment = paymentOrderRepository.save(payment);

        // Verify payment
        mockMvc.perform(post("/api/payments/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "paymentId", payment.getPaymentId(),
                                "gatewayPaymentId", "pay_sbx_12345",
                                "transactionId", "TXN_789456"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.status", is("SUCCESS")));

        // Check Payment Status
        PaymentOrder updatedPayment = paymentOrderRepository.findByPaymentId(payment.getPaymentId()).orElseThrow();
        assertEquals(PaymentStatus.SUCCESS, updatedPayment.getStatus());

        // Check Ledger Entries (3 entries: PAYMENT_RECEIVED, PLATFORM_COMMISSION, DRIVER_EARNING)
        var ledgerEntries = ledgerRepository.findByPaymentId(payment.getPaymentId());
        assertEquals(3, ledgerEntries.size());

        // Check Commission Calculation: 10% of ₹500 = ₹50, Driver Earnings = ₹450
        DriverEarnings earnings = driverEarningsRepository.findByPaymentId(payment.getPaymentId()).orElseThrow();
        assertEquals(500.0, earnings.getGrossFare());
        assertEquals(50.0, earnings.getPlatformCommission());
        assertEquals(450.0, earnings.getDriverNetEarning());

        // Check Driver Wallet Balance credited with ₹450
        AppUser driverUser = appUserRepository.findFirstByPhoneOrderByIdDesc(testDriver.getPhone()).orElseThrow();
        assertEquals(450.0, driverUser.getWalletBalance());
    }

    @Test
    @DisplayName("3. Idempotent Webhook with HMAC Signature & Duplicate Replay Protection")
    public void testIdempotentWebhookProcessing() throws Exception {
        PaymentOrder payment = new PaymentOrder();
        payment.setPaymentId("PAY_WB_001");
        payment.setBookingId(testOrder.getBookingId());
        payment.setInvoiceId("INV_WB_001");
        payment.setCustomerId(testOrder.getUserEmail());
        payment.setDriverId(testDriver.getId().toString());
        payment.setAmount(500.0);
        payment.setStatus(PaymentStatus.PENDING);
        payment = paymentOrderRepository.save(payment);

        String eventId = "evt_test_unique_999";
        String rawPayload = String.format("{\"eventId\":\"%s\",\"event\":\"payment.captured\",\"paymentId\":\"%s\",\"gatewayPaymentId\":\"pay_rzp_99\",\"transactionId\":\"TXN_99\"}",
                eventId, payment.getPaymentId());

        String validSignature = paymentProvider.generateMockSignature(rawPayload);

        // 1st Webhook Delivery -> 200 OK
        mockMvc.perform(post("/api/payments/webhook")
                        .header("X-Webhook-Signature", validSignature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rawPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.message", containsString("Webhook processed successfully")));

        // 2nd Webhook Delivery (Duplicate / Replay) -> 200 OK without re-crediting
        mockMvc.perform(post("/api/payments/webhook")
                        .header("X-Webhook-Signature", validSignature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rawPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", containsString("Duplicate webhook ignored")));

        // Ensure wallet balance is still exactly ₹450 (not credited twice)
        AppUser driverUser = appUserRepository.findFirstByPhoneOrderByIdDesc(testDriver.getPhone()).orElseThrow();
        assertEquals(450.0, driverUser.getWalletBalance());
    }

    @Test
    @DisplayName("4. Full and Partial Refund Processing")
    public void testPaymentRefund() throws Exception {
        PaymentOrder payment = new PaymentOrder();
        payment.setPaymentId("PAY_RF_001");
        payment.setBookingId(testOrder.getBookingId());
        payment.setAmount(500.0);
        payment.setStatus(PaymentStatus.SUCCESS);
        payment = paymentOrderRepository.save(payment);

        mockMvc.perform(post("/api/payments/" + payment.getPaymentId() + "/refund")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "amount", 200.0,
                                "reason", "Damaged goods in transit"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.refundType", is("PARTIAL_REFUND")))
                .andExpect(jsonPath("$.amount", is(200.0)));

        PaymentOrder refundedPayment = paymentOrderRepository.findByPaymentId(payment.getPaymentId()).orElseThrow();
        assertEquals(PaymentStatus.PARTIALLY_REFUNDED, refundedPayment.getStatus());
    }

    @Test
    @DisplayName("5. Driver Payout Account Masking & Settlement Lifecycle")
    public void testDriverPayoutLifecycle() throws Exception {
        // 1. Save Payout Account
        DriverPayoutAccount acc = new DriverPayoutAccount();
        acc.setDriverId(testDriver.getId().toString());
        acc.setAccountHolderName(testDriver.getName());
        acc.setBankName("State Bank of India");
        acc.setAccountNumber("12345678904582");
        acc.setIfscCode("SBIN0001234");
        acc.setUpiId("ramesh@oksbi");
        acc.setVerificationStatus("VERIFIED");
        acc.maskAccountNumber();
        payoutAccountRepository.save(acc);

        // 2. Simulate earnings
        DriverEarnings earn = new DriverEarnings();
        earn.setDriverId(testDriver.getId().toString());
        earn.setBookingId(testOrder.getBookingId());
        earn.setPaymentId("PAY_EARN_1");
        earn.setGrossFare(500.0);
        earn.setPlatformCommission(50.0);
        earn.setDriverNetEarning(450.0);
        earn.setPaymentStatus("PAID");
        earn.setSettlementStatus("PENDING");
        driverEarningsRepository.save(earn);

        // 3. Driver Earnings Summary & Balance API Check
        mockMvc.perform(get("/api/drivers/me/earnings")
                        .header("Authorization", "Bearer " + driverToken)
                        .requestAttr("userId", testDriver.getEmail()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.availableBalance", is(450.0)))
                .andExpect(jsonPath("$.todayPlatformFee", is(50.0)));

        mockMvc.perform(get("/api/drivers/me/balance")
                        .header("Authorization", "Bearer " + driverToken)
                        .requestAttr("userId", testDriver.getEmail()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.availableBalance", is(450.0)))
                .andExpect(jsonPath("$.isPayoutEligible", is(true)));

        // 4. Request Instant Payout
        mockMvc.perform(post("/api/drivers/me/payout-request")
                        .header("Authorization", "Bearer " + driverToken)
                        .requestAttr("userId", testDriver.getEmail())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "amount", 450.0,
                                "payoutMode", "MANUAL"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.status", is("SUCCESS")))
                .andExpect(jsonPath("$.destination", containsString("4582")))
                .andExpect(jsonPath("$.utr", notNullValue()));

        // 5. Check Payouts List
        mockMvc.perform(get("/api/drivers/me/payouts")
                        .header("Authorization", "Bearer " + driverToken)
                        .requestAttr("userId", testDriver.getEmail()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.count", greaterThanOrEqualTo(1)));
    }

    @Test
    @DisplayName("6. Admin Commission Metrics & Reconciliation Audit")
    public void testAdminCommissionAndReconciliation() throws Exception {
        // Record mock ledger entry
        LedgerEntry entry1 = new LedgerEntry();
        entry1.setEntryNumber("LED-REV-01");
        entry1.setType(LedgerType.PAYMENT_RECEIVED);
        entry1.setEntryType("CREDIT");
        entry1.setAmount(1000.0);
        ledgerRepository.save(entry1);

        LedgerEntry entry2 = new LedgerEntry();
        entry2.setEntryNumber("LED-COMM-01");
        entry2.setType(LedgerType.PLATFORM_COMMISSION);
        entry2.setEntryType("CREDIT");
        entry2.setAmount(100.0);
        ledgerRepository.save(entry2);

        mockMvc.perform(get("/api/admin/commissions")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.metrics.grossRevenue", is(1000.0)))
                .andExpect(jsonPath("$.metrics.platformCommissionEarned", is(100.0)));

        mockMvc.perform(get("/api/admin/reconciliation")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.matchedCount", greaterThanOrEqualTo(0)));
    }
}
