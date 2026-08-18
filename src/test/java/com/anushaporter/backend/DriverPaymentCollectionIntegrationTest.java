package com.anushaporter.backend;

import com.anushaporter.backend.model.Driver;
import com.anushaporter.backend.model.DriverWallet;
import com.anushaporter.backend.model.Order;
import com.anushaporter.backend.repository.DriverEarningsRepository;
import com.anushaporter.backend.repository.DriverRepository;
import com.anushaporter.backend.repository.DriverWalletRepository;
import com.anushaporter.backend.repository.OrderRepository;
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

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = BackendApplication.class)
@Import(DriverPaymentCollectionIntegrationTest.TestConfig.class)
public class DriverPaymentCollectionIntegrationTest {

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
    private OrderRepository orderRepository;

    @Autowired
    private DriverEarningsRepository driverEarningsRepository;

    @Autowired
    private com.anushaporter.backend.repository.PaymentOrderRepository paymentOrderRepository;

    @Autowired
    private WalletTransactionRepository walletTransactionRepository;

    @Autowired
    private JwtUtil jwtUtil;

    private Driver assignedDriver;
    private Driver otherDriver;
    private String assignedDriverToken;
    private String otherDriverToken;
    private Order testOrder;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();

        paymentOrderRepository.deleteAll();
        walletTransactionRepository.deleteAll();
        driverEarningsRepository.deleteAll();
        orderRepository.deleteAll();
        driverWalletRepository.deleteAll();
        driverRepository.deleteAll();

        // 1. Create assigned driver
        assignedDriver = new Driver();
        assignedDriver.setName("Ramesh Kumar");
        assignedDriver.setEmail("ramesh@example.com");
        assignedDriver.setPhone("9876543210");
        assignedDriver.setVehicleNumber("TS09AB1234");
        assignedDriver.setVehicleType("Tata Ace");
        assignedDriver.setStatus("online");
        assignedDriver.setKyc("verified");
        assignedDriver.setWalletBalance(100.00);
        assignedDriver = driverRepository.save(assignedDriver);

        // Initialise wallet
        DriverWallet wallet = new DriverWallet();
        wallet.setDriverId(String.valueOf(assignedDriver.getId()));
        wallet.setAvailableBalance(100.00);
        wallet.setPendingBalance(0.00);
        wallet.setTotalEarned(500.00);
        wallet.setPlatformCommission(25.00);
        driverWalletRepository.save(wallet);

        assignedDriverToken = jwtUtil.generateToken(assignedDriver.getEmail());

        // 2. Create unassigned / other driver
        otherDriver = new Driver();
        otherDriver.setName("Suresh Patel");
        otherDriver.setEmail("suresh@example.com");
        otherDriver.setPhone("9123456780");
        otherDriver.setStatus("online");
        otherDriver = driverRepository.save(otherDriver);
        otherDriverToken = jwtUtil.generateToken(otherDriver.getEmail());

        // 3. Create active Order
        testOrder = new Order();
        testOrder.setBookingId("BK_12345");
        testOrder.setAmount(850.00);
        testOrder.setDriverId(String.valueOf(assignedDriver.getId()));
        testOrder.setDriverEmail(assignedDriver.getEmail());
        testOrder.setDriverPhone(assignedDriver.getPhone());
        testOrder.setDriverName(assignedDriver.getName());
        testOrder.setUserEmail("customer@example.com");
        testOrder.setDeliveryOtp("8813");
        testOrder.setStatus("driver_reached");
        testOrder.setOtpVerified(false);
        testOrder = orderRepository.save(testOrder);
    }

    @Test
    void testVerifyOtpSuccess() throws Exception {
        mockMvc.perform(post("/api/driver/orders/" + testOrder.getBookingId() + "/verify-otp")
                        .header("Authorization", "Bearer " + assignedDriverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enteredOtp\": \"8813\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.otpVerified", is(true)))
                .andExpect(jsonPath("$.status", is("OTP_VERIFIED")))
                .andExpect(jsonPath("$.message", containsString("OTP verified successfully")));

        Order inDb = orderRepository.findByBookingId(testOrder.getBookingId()).orElseThrow();
        assertEquals(true, inDb.getOtpVerified());
    }

    @Test
    void testVerifyOtpInvalid() throws Exception {
        mockMvc.perform(post("/api/driver/orders/" + testOrder.getBookingId() + "/verify-otp")
                        .header("Authorization", "Bearer " + assignedDriverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enteredOtp\": \"0000\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", containsString("Incorrect Customer Delivery OTP")));
    }

    @Test
    void testConfirmPaymentSuccessCash() throws Exception {
        // Step 1: Verify OTP
        testOrder.setOtpVerified(true);
        testOrder.setStatus("payment_confirmation_pending");
        orderRepository.save(testOrder);

        // Step 2: Confirm Payment CASH
        mockMvc.perform(post("/api/driver/orders/" + testOrder.getBookingId() + "/confirm-payment")
                        .header("Authorization", "Bearer " + assignedDriverToken)
                        .header("Idempotency-Key", "COMPL_BK_12345_1720000001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookingId\": \"BK_12345\", \"amount\": 850.00, \"method\": \"CASH\", \"paymentConfirmed\": true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.message", containsString("Payment confirmed and order completed successfully.")))
                .andExpect(jsonPath("$.earnings.grossFare", is(850.00)))
                .andExpect(jsonPath("$.earnings.platformCommission", is(42.50)))
                .andExpect(jsonPath("$.earnings.driverNetEarning", is(807.50)))
                .andExpect(jsonPath("$.order.status", is("completed")))
                .andExpect(jsonPath("$.order.paymentStatus", is("PAID")));

        // Verify Driver Wallet deducted 5% platform commission in DB
        DriverWallet walletInDb = driverWalletRepository.findByDriverId(String.valueOf(assignedDriver.getId())).orElseThrow();
        assertEquals(100.00 - 42.50, walletInDb.getAvailableBalance());
        assertEquals(500.00 + 850.00, walletInDb.getTotalEarned());
        assertEquals(25.00 + 42.50, walletInDb.getPlatformCommission());

        // Verify Order in DB
        Order orderInDb = orderRepository.findByBookingId(testOrder.getBookingId()).orElseThrow();
        assertEquals("completed", orderInDb.getStatus());
        assertEquals("PAID", orderInDb.getPaymentStatus());
        assertEquals("CASH", orderInDb.getPaymentMethod());

        // Verify PaymentOrder record in DB
        com.anushaporter.backend.model.PaymentOrder po = paymentOrderRepository.findByBookingId(testOrder.getBookingId()).orElseThrow();
        assertEquals(com.anushaporter.backend.model.PaymentStatus.SUCCESS, po.getStatus());
        assertEquals("CASH", po.getPaymentMethod());
        assertEquals(850.00, po.getAmount());
        assertEquals(String.valueOf(assignedDriver.getId()), po.getDriverId());
    }

    @Test
    void testConfirmPaymentSuccessOnline() throws Exception {
        // Step 1: Verify OTP
        testOrder.setOtpVerified(true);
        testOrder.setStatus("payment_confirmation_pending");
        orderRepository.save(testOrder);

        // Step 2: Confirm Payment ONLINE via /api/orders/{id}/complete
        mockMvc.perform(post("/api/orders/" + testOrder.getBookingId() + "/complete")
                        .header("Authorization", "Bearer " + assignedDriverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookingId\": \"BK_12345\", \"amount\": 850.00, \"paymentMethod\": \"ONLINE\", \"paymentConfirmed\": true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.earnings.grossFare", is(850.00)))
                .andExpect(jsonPath("$.earnings.platformCommission", is(42.50)))
                .andExpect(jsonPath("$.earnings.driverNetEarning", is(807.50)))
                .andExpect(jsonPath("$.order.paymentMethod", is("ONLINE")));

        // Verify PaymentOrder record in DB
        com.anushaporter.backend.model.PaymentOrder po = paymentOrderRepository.findByBookingId(testOrder.getBookingId()).orElseThrow();
        assertEquals(com.anushaporter.backend.model.PaymentStatus.SUCCESS, po.getStatus());
        assertEquals("ONLINE", po.getPaymentMethod());
        assertEquals(850.00, po.getAmount());
    }

    @Test
    void testConfirmPaymentViaDriversOrdersComplete() throws Exception {
        // Step 1: Verify OTP
        testOrder.setOtpVerified(true);
        testOrder.setStatus("payment_confirmation_pending");
        orderRepository.save(testOrder);

        // Step 2: Confirm Payment via /api/drivers/orders/{bookingId}/complete
        mockMvc.perform(post("/api/drivers/orders/" + testOrder.getBookingId() + "/complete")
                        .header("Authorization", "Bearer " + assignedDriverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookingId\": \"BK_12345\", \"amount\": 850.00, \"method\": \"CASH\", \"paymentConfirmed\": true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.earnings.grossFare", is(850.00)))
                .andExpect(jsonPath("$.order.status", is("completed")));
    }

    @Test
    void testConfirmPaymentViaRootConfirmPayment() throws Exception {
        // Step 1: Verify OTP
        testOrder.setOtpVerified(true);
        testOrder.setStatus("payment_confirmation_pending");
        orderRepository.save(testOrder);

        // Step 2: Confirm Payment via POST /api/confirm-payment with bookingId in body
        mockMvc.perform(post("/api/confirm-payment")
                        .header("Authorization", "Bearer " + assignedDriverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookingId\": \"BK_12345\", \"amount\": 850.00, \"method\": \"CASH\", \"paymentConfirmed\": true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.earnings.grossFare", is(850.00)))
                .andExpect(jsonPath("$.order.status", is("completed")));
    }

    @Test
    void testVerifyOtpViaRootVerifyOtp() throws Exception {
        // POST /api/verify-otp with bookingId in body
        mockMvc.perform(post("/api/verify-otp")
                        .header("Authorization", "Bearer " + assignedDriverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookingId\": \"BK_12345\", \"enteredOtp\": \"8813\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.otpVerified", is(true)))
                .andExpect(jsonPath("$.status", is("OTP_VERIFIED")));
    }

    @Test
    void testConfirmPaymentWithoutOtpFails() throws Exception {
        // OTP is NOT verified
        mockMvc.perform(post("/api/driver/orders/" + testOrder.getBookingId() + "/confirm-payment")
                        .header("Authorization", "Bearer " + assignedDriverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookingId\": \"BK_12345\", \"amount\": 850.00, \"method\": \"CASH\", \"paymentConfirmed\": true}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", containsString("OTP has not been verified yet")));
    }

    @Test
    void testConfirmPaymentUnauthorizedDriverFails() throws Exception {
        // OTP is verified
        testOrder.setOtpVerified(true);
        testOrder.setStatus("payment_confirmation_pending");
        orderRepository.save(testOrder);

        // Calling with otherDriverToken
        mockMvc.perform(post("/api/driver/orders/" + testOrder.getBookingId() + "/confirm-payment")
                        .header("Authorization", "Bearer " + otherDriverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookingId\": \"BK_12345\", \"amount\": 850.00, \"method\": \"CASH\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", containsString("Forbidden")));
    }

    @Test
    void testConfirmPaymentAmountMismatchFails() throws Exception {
        // OTP is verified
        testOrder.setOtpVerified(true);
        testOrder.setStatus("payment_confirmation_pending");
        orderRepository.save(testOrder);

        // Calling with mismatched amount
        mockMvc.perform(post("/api/driver/orders/" + testOrder.getBookingId() + "/confirm-payment")
                        .header("Authorization", "Bearer " + assignedDriverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookingId\": \"BK_12345\", \"amount\": 999.00, \"method\": \"CASH\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", containsString("does not match")));
    }

    @Test
    void testConfirmPaymentIdempotency() throws Exception {
        // Step 1: Verify OTP
        testOrder.setOtpVerified(true);
        testOrder.setStatus("payment_confirmation_pending");
        orderRepository.save(testOrder);

        String idemKey = "COMPL_BK_12345_1720000099";

        // First call
        mockMvc.perform(post("/api/driver/orders/" + testOrder.getBookingId() + "/confirm-payment")
                        .header("Authorization", "Bearer " + assignedDriverToken)
                        .header("Idempotency-Key", idemKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookingId\": \"BK_12345\", \"amount\": 850.00, \"method\": \"CASH\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.earnings.driverNetEarning", is(807.50)));

        // Record balance after first call
        DriverWallet walletAfterFirst = driverWalletRepository.findByDriverId(String.valueOf(assignedDriver.getId())).orElseThrow();
        double balanceAfterFirst = walletAfterFirst.getAvailableBalance();

        // Second call with same idempotency key
        mockMvc.perform(post("/api/driver/orders/" + testOrder.getBookingId() + "/confirm-payment")
                        .header("Authorization", "Bearer " + assignedDriverToken)
                        .header("Idempotency-Key", idemKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookingId\": \"BK_12345\", \"amount\": 850.00, \"method\": \"CASH\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.earnings.driverNetEarning", is(807.50)));

        // Verify balance was NOT double-credited
        DriverWallet walletAfterSecond = driverWalletRepository.findByDriverId(String.valueOf(assignedDriver.getId())).orElseThrow();
        assertEquals(balanceAfterFirst, walletAfterSecond.getAvailableBalance());
    }
}
