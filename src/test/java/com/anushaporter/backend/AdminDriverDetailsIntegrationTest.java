package com.anushaporter.backend;

import com.anushaporter.backend.model.Driver;
import com.anushaporter.backend.model.Order;
import com.anushaporter.backend.repository.DriverRepository;
import com.anushaporter.backend.repository.OrderRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = BackendApplication.class)
@Import(AdminDriverDetailsIntegrationTest.TestConfig.class)
public class AdminDriverDetailsIntegrationTest {

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
    private OrderRepository orderRepository;

    @Autowired
    private JwtUtil jwtUtil;

    private String adminToken;
    private Driver testDriver;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        adminToken = "Bearer " + jwtUtil.generateToken("admin@porter.com");

        testDriver = driverRepository.findByPhone("9876500001").orElseGet(() -> {
            Driver d = new Driver();
            d.setName("Suresh Raina");
            d.setPhone("9876500001");
            d.setEmail("suresh.driver@porter.com");
            d.setStatus("online");
            d.setKyc("approved");
            d.setRegistrationStep(5);
            d.setVehicle("Tata Ace");
            d.setVehicleType("Tata Ace");
            d.setVehicleNumber("TS09AB1234");
            d.setServiceType("OUR_SERVICES");
            d.setAadhaarNumber("123456789012");
            d.setLicenseNumber("DL-1234567890");
            d.setRcNumber("RC-987654321");
            d.setPanNumber("ABCDE1234F");
            d.setBankName("State Bank of India");
            d.setAccountHolderName("Suresh Raina");
            d.setAccountNumber("112233445566");
            d.setIfscCode("SBIN0001234");
            d.setAddressLine1("Plot 42, Hitech City");
            d.setCity("Hyderabad");
            d.setState("Telangana");
            d.setPincode("500081");
            d.setLicenseUri("https://bucket.s3.amazonaws.com/license.jpg");
            d.setRcUri("https://bucket.s3.amazonaws.com/rc.jpg");
            d.setAadhaarUri("https://bucket.s3.amazonaws.com/aadhaar.jpg");
            return driverRepository.save(d);
        });
    }

    @Test
    void testGetAdminDriversReturnsAllRegistrationDetails() throws Exception {
        mockMvc.perform(get("/api/admin/drivers")
                .header("Authorization", adminToken)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", is(not(empty()))))
                .andExpect(jsonPath("$[?(@.phone == '9876500001')].name", hasItem("Suresh Raina")))
                .andExpect(jsonPath("$[?(@.phone == '9876500001')].aadhaarNumber", hasItem("123456789012")))
                .andExpect(jsonPath("$[?(@.phone == '9876500001')].licenseNumber", hasItem("DL-1234567890")))
                .andExpect(jsonPath("$[?(@.phone == '9876500001')].rcNumber", hasItem("RC-987654321")))
                .andExpect(jsonPath("$[?(@.phone == '9876500001')].bankName", hasItem("State Bank of India")))
                .andExpect(jsonPath("$[?(@.phone == '9876500001')].accountNumber", hasItem("112233445566")))
                .andExpect(jsonPath("$[?(@.phone == '9876500001')].ifscCode", hasItem("SBIN0001234")))
                .andExpect(jsonPath("$[?(@.phone == '9876500001')].addressLine1", hasItem("Plot 42, Hitech City")))
                .andExpect(jsonPath("$[?(@.phone == '9876500001')].city", hasItem("Hyderabad")))
                .andExpect(jsonPath("$[?(@.phone == '9876500001')].pincode", hasItem("500081")))
                .andExpect(jsonPath("$[?(@.phone == '9876500001')].isRegistered", hasItem(true)))
                .andExpect(jsonPath("$[?(@.phone == '9876500001')].registrationStep", hasItem(5)))
                .andExpect(jsonPath("$[?(@.phone == '9876500001')].licenseUri", hasItem(notNullValue())));
    }

    @Test
    void testGetAdminDriversFilterByStatusAndKyc() throws Exception {
        // Status online filter
        mockMvc.perform(get("/api/admin/drivers?status=online")
                .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.phone == '9876500001')].status", hasItem("online")));

        // KYC approved filter
        mockMvc.perform(get("/api/admin/drivers?kyc=approved")
                .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.phone == '9876500001')].kyc", hasItem("approved")));
    }

    @Test
    void testGetDriverByIdAndFormattedId() throws Exception {
        // Numeric ID lookup
        mockMvc.perform(get("/api/admin/drivers/" + testDriver.getId())
                .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Suresh Raina")))
                .andExpect(jsonPath("$.aadhaarNumber", is("123456789012")))
                .andExpect(jsonPath("$.bankName", is("State Bank of India")))
                .andExpect(jsonPath("$.isRegistered", is(true)));

        // Formatted DRV- id lookup
        mockMvc.perform(get("/api/admin/drivers/DRV-" + testDriver.getId())
                .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Suresh Raina")))
                .andExpect(jsonPath("$.licenseNumber", is("DL-1234567890")));
    }

    @Test
    void testDedicatedCategoryEndpoints() throws Exception {
        mockMvc.perform(get("/api/admin/drivers/our-services")
                .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.phone == '9876500001')].serviceType", hasItem("OUR_SERVICES")));
    }

    @Test
    void testOrderWithAssignedDriverReturnsDriverDetailsToAdmin() throws Exception {
        Order order = new Order();
        order.setBookingId("ORD-TEST-DRV-1");
        order.setStatus("accepted");
        order.setDriverId(testDriver.getId().toString());
        order.setDriverName(testDriver.getName());
        order.setDriverPhone(testDriver.getPhone());
        order.setDriverVehicleNumber(testDriver.getVehicleNumber());
        order.setAmount(350.0);
        order.setServiceType("GOODS");
        orderRepository.save(order);

        mockMvc.perform(get("/api/admin/orders")
                .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orders[?(@.bookingId == 'ORD-TEST-DRV-1')].driverName", hasItem("Suresh Raina")))
                .andExpect(jsonPath("$.orders[?(@.bookingId == 'ORD-TEST-DRV-1')].driverPhone", hasItem("9876500001")))
                .andExpect(jsonPath("$.orders[?(@.bookingId == 'ORD-TEST-DRV-1')].driver.name", hasItem("Suresh Raina")));
    }
}
