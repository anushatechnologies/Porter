package com.anushaporter.backend;

import com.anushaporter.backend.model.AppUser;
import com.anushaporter.backend.model.Driver;
import com.anushaporter.backend.model.Order;
import com.anushaporter.backend.repository.AppUserRepository;
import com.anushaporter.backend.repository.DriverRepository;
import com.anushaporter.backend.repository.OrderRepository;
import com.anushaporter.backend.service.PushNotificationService;
import com.anushaporter.backend.util.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
public class PushNotificationTokenIsolationIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private DriverRepository driverRepository;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PushNotificationService pushNotificationService;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;

    @BeforeEach
    public void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @Test
    public void testDriverTokenRegistration_StoresInDriverEntity_AndIsolatesCustomer() throws Exception {
        String phone = "9112233445";
        String driverPushToken = "fcm_driver_device_token_xyz_999";

        // Setup driver
        Driver driver = driverRepository.findByPhone(phone).orElseGet(() -> {
            Driver d = new Driver();
            d.setPhone(phone);
            d.setName("Dedicated Test Driver");
            d.setStatus("online");
            d.setKyc("approved");
            return driverRepository.save(d);
        });

        // Setup customer user with the same phone (simulating shared phone or test account)
        AppUser customer = appUserRepository.findFirstByPhoneOrderByIdDesc(phone).orElseGet(() -> {
            AppUser u = new AppUser();
            u.setPhone(phone);
            u.setName("Customer User");
            u.setRole("CUSTOMER");
            u.setFcmToken("old_customer_token");
            return appUserRepository.save(u);
        });
        customer.setRole("CUSTOMER");
        customer.setFcmToken("old_customer_token");
        appUserRepository.save(customer);

        String driverJwt = jwtUtil.generateToken(phone);

        // Driver registers device token
        Map<String, String> payload = Map.of("fcmToken", driverPushToken);
        mockMvc.perform(post("/api/driver/fcm-token")
                        .header("Authorization", "Bearer " + driverJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)));

        // Verify driver record now contains the FCM token directly
        Driver refreshedDriver = driverRepository.findById(driver.getId()).orElseThrow();
        assertEquals(driverPushToken, refreshedDriver.getFcmToken());

        // Verify that customer account was not polluted with driver token
        AppUser refreshedCustomer = appUserRepository.findById(customer.getId()).orElseThrow();
        assertNotEquals(driverPushToken, refreshedCustomer.getFcmToken());
    }

    @Test
    public void testCustomerOrderStatusNotification_DoesNotPushToDriverDevice() {
        String driverPhone = "9443322110";
        String driverToken = "fcm_driver_phone_token_777";

        Driver driver = new Driver();
        driver.setPhone(driverPhone);
        driver.setName("Notification Test Driver");
        driver.setFcmToken(driverToken);
        Driver savedDriver = driverRepository.save(driver);

        // Suppose customer erroneously has driver device token
        AppUser customer = new AppUser();
        customer.setEmail("customer_test_alert@example.com");
        customer.setPhone("9888877777");
        customer.setRole("CUSTOMER");
        customer.setFcmToken(driverToken); // Stale token collision
        appUserRepository.save(customer);

        Order order = new Order();
        order.setBookingId("BOOK-TEST-ALERT-01");
        order.setUserEmail("customer_test_alert@example.com");
        order.setDriverName("Notification Test Driver");
        order.setDriverPhone(driverPhone);
        order.setDriverId(String.valueOf(savedDriver.getId()));
        Order savedOrder = orderRepository.save(order);

        // Notify customer of order status
        assertDoesNotThrow(() -> {
            pushNotificationService.notifyOrderStatus(savedOrder, "assigned");
        });

        // Verify token resolution for driver returns the driver token
        String resolvedToken = pushNotificationService.resolveDriverToken(savedDriver);
        assertEquals(driverToken, resolvedToken);
    }
}
