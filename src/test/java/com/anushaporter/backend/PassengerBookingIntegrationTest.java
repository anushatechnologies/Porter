package com.anushaporter.backend;

import com.anushaporter.backend.dto.PassengerBookingCreateRequest;
import com.anushaporter.backend.dto.PassengerDriverAssignRequest;
import com.anushaporter.backend.dto.PassengerFareEstimateRequest;
import com.anushaporter.backend.model.*;
import com.anushaporter.backend.repository.*;
import com.anushaporter.backend.service.PassengerPricingVersionService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.mockito.Mockito;
import software.amazon.awssdk.services.s3.S3Client;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = BackendApplication.class)
@Import(PassengerBookingIntegrationTest.TestConfig.class)
public class PassengerBookingIntegrationTest {

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
    private ObjectMapper objectMapper;

    @Autowired
    private PassengerVehicleCategoryRepository categoryRepository;

    @Autowired
    private PassengerPricingRuleRepository ruleRepository;

    @Autowired
    private PassengerBookingRepository bookingRepository;

    @Autowired
    private DriverRepository driverRepository;

    @Autowired
    private PassengerPricingVersionService versionService;

    @Autowired
    private com.anushaporter.backend.util.JwtUtil jwtUtil;

    private String adminToken;

    @BeforeEach
    void setUp() {
        objectMapper.findAndRegisterModules();
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        adminToken = jwtUtil.generateToken("admin@anushaporter.com");

        // Ensure active version and baseline rules
        PassengerPricingVersion activeVersion = versionService.getActiveVersion();

        if (categoryRepository.findByCategoryCode("SEDAN").isEmpty()) {
            categoryRepository.save(PassengerVehicleCategory.builder()
                    .categoryCode("SEDAN")
                    .displayName("Sedan")
                    .passengerCapacity(4)
                    .luggageCapacity(3)
                    .baseFare(new BigDecimal("300.00"))
                    .minimumKm(new BigDecimal("10.00"))
                    .perKmRate(new BigDecimal("14.00"))
                    .minimumFare(new BigDecimal("300.00"))
                    .driverAllowance(new BigDecimal("100.00"))
                    .active(true)
                    .build());
        }

        // Ensure baseline rule for SEDAN ONE_WAY with 14.00/km
        ruleRepository.findByPricingVersionIdAndServiceCodeAndVehicleCategoryCode(
                activeVersion.getVersionNumber(), "ONE_WAY", "SEDAN"
        ).ifPresentOrElse(r -> {
            r.setBaseFare(new BigDecimal("300.00"));
            r.setMinimumKm(new BigDecimal("10.00"));
            r.setPerKmRate(new BigDecimal("14.00"));
            r.setMinimumFare(new BigDecimal("300.00"));
            ruleRepository.save(r);
        }, () -> {
            ruleRepository.save(PassengerPricingRule.builder()
                    .pricingVersionId(activeVersion.getVersionNumber())
                    .serviceCode("ONE_WAY")
                    .vehicleCategoryCode("SEDAN")
                    .baseFare(new BigDecimal("300.00"))
                    .minimumKm(new BigDecimal("10.00"))
                    .perKmRate(new BigDecimal("14.00"))
                    .minimumFare(new BigDecimal("300.00"))
                    .driverAllowance(new BigDecimal("100.00"))
                    .freeWaitingMinutes(15)
                    .waitingChargePer15Min(new BigDecimal("50.00"))
                    .tollHandling("ACTUAL")
                    .parkingHandling("ACTUAL")
                    .driverCommissionPercentage(new BigDecimal("20.00"))
                    .taxPercentage(new BigDecimal("5.00"))
                    .build());
        });
    }

    @Test
    void testCompleteCustomerBookingFlow_AndDriverAssignment() throws Exception {
        // Step 1: Request fare estimate
        PassengerFareEstimateRequest estimateReq = PassengerFareEstimateRequest.builder()
                .serviceType("ONE_WAY")
                .vehicleCategoryCode("SEDAN")
                .pickupAddress("Banjara Hills, Hyderabad")
                .dropAddress("Hitech City, Hyderabad")
                .passengerCount(3)
                .manualDistanceKm(new BigDecimal("20.00"))
                .build();

        MvcResult estimateResult = mockMvc.perform(post("/api/passenger/fare-estimate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(estimateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fareLockToken").isNotEmpty())
                .andExpect(jsonPath("$.breakdown.totalFare").isNotEmpty())
                .andReturn();

        // Step 2: Create Booking
        PassengerBookingCreateRequest bookingReq = PassengerBookingCreateRequest.builder()
                .serviceType("ONE_WAY")
                .vehicleCategoryCode("SEDAN")
                .customerName("John Doe")
                .customerPhone("9876543210")
                .customerEmail("john@example.com")
                .pickupAddress("Banjara Hills, Hyderabad")
                .dropAddress("Hitech City, Hyderabad")
                .passengerCount(3)
                .build();

        MvcResult bookingResult = mockMvc.perform(post("/api/passenger/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bookingReq)))
                .andExpect(status().is2xxSuccessful())
                .andExpect(jsonPath("$.bookingNumber").isNotEmpty())
                .andExpect(jsonPath("$.status").value("DRIVER_SEARCHING"))
                .andReturn();

        PassengerBooking booking = parseBookingResponse(bookingResult);
        assertNotNull(booking.getId());

        // Step 3: Admin assigns driver
        Driver driver = new Driver();
        driver.setName("Rajesh Kumar");
        driver.setPhone("9123456780");
        driver.setVehicle("Honda Amaze");
        driver.setVehicleNumber("TS09AB1234");
        driver = driverRepository.save(driver);

        PassengerDriverAssignRequest assignReq = PassengerDriverAssignRequest.builder()
                .driverId(driver.getId())
                .adminNotes("Assigned closest driver")
                .build();

        mockMvc.perform(post("/api/admin/passenger/bookings/" + booking.getId() + "/assign-driver")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(assignReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRIVER_ASSIGNED"))
                .andExpect(jsonPath("$.driverName").value("Rajesh Kumar"));

        // Step 4: Complete payment
        mockMvc.perform(post("/api/passenger/bookings/" + booking.getId() + "/payment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentMethod\": \"ONLINE\", \"transactionRef\": \"TX123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentStatus").value("PAID"));
    }

    /**
     * Specification Critical Test:
     * "Admin changes Sedan pricing from ₹14/km to ₹16/km.
     * Existing booking must retain ₹14/km.
     * New booking must use ₹16/km."
     */
    @Test
    void testHistoricalPricingImmutability_RateChangeRetainsHistoricalSnapshot() throws Exception {
        // 1. Create initial booking at ₹14/km
        PassengerBookingCreateRequest req1 = PassengerBookingCreateRequest.builder()
                .serviceType("ONE_WAY")
                .vehicleCategoryCode("SEDAN")
                .customerName("Historical Customer")
                .customerPhone("9888877771")
                .pickupAddress("Point A")
                .dropAddress("Point B")
                .passengerCount(2)
                .build();

        MvcResult result1 = mockMvc.perform(post("/api/passenger/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req1)))
                .andExpect(status().is2xxSuccessful())
                .andReturn();

        PassengerBooking booking1 = parseBookingResponse(result1);
        BigDecimal distanceFareBooking1 = booking1.getFareBreakdown().getDistanceFare();
        BigDecimal totalFareBooking1 = booking1.getFareBreakdown().getTotalFare();
        String versionBooking1 = booking1.getPricingVersionId();

        // 2. Admin publishes new version and increases Sedan rate to ₹16/km
        PassengerPricingVersion newVersion = versionService.publishNewVersion("SuperAdmin", "admin@anushaporter.com", "Increase Sedan rate to 16/km");
        PassengerPricingRule newRule = ruleRepository
                .findByPricingVersionIdAndServiceCodeAndVehicleCategoryCode(newVersion.getVersionNumber(), "ONE_WAY", "SEDAN")
                .orElseThrow();
        newRule.setPerKmRate(new BigDecimal("16.00"));
        ruleRepository.save(newRule);

        // 3. Create second booking under new version
        PassengerBookingCreateRequest req2 = PassengerBookingCreateRequest.builder()
                .serviceType("ONE_WAY")
                .vehicleCategoryCode("SEDAN")
                .customerName("New Customer")
                .customerPhone("9888877772")
                .pickupAddress("Point A")
                .dropAddress("Point B")
                .passengerCount(2)
                .build();

        MvcResult result2 = mockMvc.perform(post("/api/passenger/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req2)))
                .andExpect(status().is2xxSuccessful())
                .andReturn();

        PassengerBooking booking2 = parseBookingResponse(result2);

        // 4. Verify historical immutability:
        // Booking 1 fetched from DB retains its exact snapshot and lower fare
        PassengerBooking fetchedBooking1 = bookingRepository.findById(booking1.getId()).orElseThrow();
        assertEquals(versionBooking1, fetchedBooking1.getPricingVersionId());
        assertEquals(0, distanceFareBooking1.compareTo(fetchedBooking1.getFareBreakdown().getDistanceFare()));
        assertEquals(0, totalFareBooking1.compareTo(fetchedBooking1.getFareBreakdown().getTotalFare()));

        // Booking 2 has new version and higher fare reflecting ₹16/km!
        assertEquals(newVersion.getVersionNumber(), booking2.getPricingVersionId());
        assertTrue(booking2.getFareBreakdown().getTotalFare().compareTo(fetchedBooking1.getFareBreakdown().getTotalFare()) > 0);
    }

    @Test
    void testPricingPreviewToolAPI() throws Exception {
        PassengerFareEstimateRequest previewReq = PassengerFareEstimateRequest.builder()
                .serviceType("ONE_WAY")
                .vehicleCategoryCode("SEDAN")
                .manualDistanceKm(new BigDecimal("25.00"))
                .manualDurationMinutes(60)
                .passengerCount(3)
                .scheduledPickupTime(LocalDateTime.of(2026, 9, 7, 20, 30)) // 8:30 PM
                .build();

        mockMvc.perform(post("/api/admin/passenger/pricing/preview")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(previewReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.breakdown.baseFare").value(300.00))
                .andExpect(jsonPath("$.breakdown.distanceFare").value(210.00))
                .andExpect(jsonPath("$.breakdown.driverAllowance").value(100.00))
                .andExpect(jsonPath("$.breakdown.totalFare").isNotEmpty());
    }

    private PassengerBooking parseBookingResponse(MvcResult result) throws Exception {
        String content = result.getResponse().getContentAsString();
        JsonNode node = objectMapper.readTree(content);
        if (node.has("booking")) {
            return objectMapper.treeToValue(node.get("booking"), PassengerBooking.class);
        }
        return objectMapper.readValue(content, PassengerBooking.class);
    }
}
