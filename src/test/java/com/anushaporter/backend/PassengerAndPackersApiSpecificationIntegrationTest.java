package com.anushaporter.backend;

import com.anushaporter.backend.model.PassengerVehicleCategory;
import com.anushaporter.backend.repository.PassengerBookingRepository;
import com.anushaporter.backend.repository.PassengerVehicleCategoryRepository;
import com.anushaporter.backend.repository.OrderRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import software.amazon.awssdk.services.s3.S3Client;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = BackendApplication.class)
@Import(PassengerAndPackersApiSpecificationIntegrationTest.TestConfig.class)
public class PassengerAndPackersApiSpecificationIntegrationTest {

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
    private PassengerVehicleCategoryRepository passengerCategoryRepository;

    @Autowired
    private PassengerBookingRepository passengerBookingRepository;

    @Autowired
    private OrderRepository orderRepository;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();

        // Seed auto and sedan if missing
        if (passengerCategoryRepository.findByCategoryCode("AUTO").isEmpty()) {
            passengerCategoryRepository.save(PassengerVehicleCategory.builder()
                    .categoryCode("AUTO")
                    .displayName("Auto")
                    .description("Affordable 3-wheeler auto rickshaw")
                    .passengerCapacity(3)
                    .luggageCapacity(2)
                    .baseFare(new BigDecimal("30.00"))
                    .perKmRate(new BigDecimal("14.00"))
                    .displayOrder(1)
                    .active(true)
                    .build());
        }

        if (passengerCategoryRepository.findByCategoryCode("SEDAN").isEmpty()) {
            passengerCategoryRepository.save(PassengerVehicleCategory.builder()
                    .categoryCode("SEDAN")
                    .displayName("Sedan Prime")
                    .description("Spacious sedans with extra boot space & AC")
                    .passengerCapacity(4)
                    .luggageCapacity(3)
                    .baseFare(new BigDecimal("80.00"))
                    .perKmRate(new BigDecimal("22.00"))
                    .displayOrder(3)
                    .active(true)
                    .build());
        }
    }

    @Test
    void testCompletePassengerRideWorkflow() throws Exception {
        // 1. GET /api/passenger/categories
        mockMvc.perform(get("/api/passenger/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", not(empty())))
                .andExpect(jsonPath("$.data[?(@.code == 'AUTO')].passengerCapacity", contains(3)))
                .andExpect(jsonPath("$.data[?(@.code == 'AUTO')].basePrice", notNullValue()));

        // 2. POST /api/passenger/fare-estimate
        String fareEstimatePayload = """
                {
                  "serviceType": "ONE_WAY",
                  "vehicleCategoryCode": "SEDAN",
                  "pickupAddress": "Koramangala 4th Block, Bangalore",
                  "dropAddress": "Indiranagar 100ft Road, Bangalore",
                  "pickupLatitude": 12.9352,
                  "pickupLongitude": 77.6245,
                  "dropLatitude": 12.9784,
                  "dropLongitude": 77.6408,
                  "passengerCount": 2,
                  "luggageCount": 1,
                  "couponCode": "WELCOME100",
                  "stops": []
                }
                """;

        MvcResult fareResult = mockMvc.perform(post("/api/passenger/fare-estimate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(fareEstimatePayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fareToken", notNullValue()))
                .andExpect(jsonPath("$.tokenExpiresAt", notNullValue()))
                .andExpect(jsonPath("$.distanceKm", notNullValue()))
                .andExpect(jsonPath("$.durationMinutes", notNullValue()))
                .andExpect(jsonPath("$.estimatedFare", notNullValue()))
                .andExpect(jsonPath("$.breakdown.baseFare", notNullValue()))
                .andExpect(jsonPath("$.breakdown.taxes", notNullValue()))
                .andReturn();

        JsonNode fareNode = objectMapper.readTree(fareResult.getResponse().getContentAsString());
        String fareToken = fareNode.get("fareToken").asText();
        assertNotNull(fareToken);

        // 3. POST /api/passenger/bookings
        String bookingPayload = String.format("""
                {
                  "fareToken": "%s",
                  "serviceType": "ONE_WAY",
                  "vehicleCategoryCode": "SEDAN",
                  "pickupAddress": "Koramangala 4th Block, Bangalore",
                  "dropAddress": "Indiranagar 100ft Road, Bangalore",
                  "pickupLatitude": 12.9352,
                  "pickupLongitude": 77.6245,
                  "dropLatitude": 12.9784,
                  "dropLongitude": 77.6408,
                  "passengerName": "Anusha R",
                  "passengerPhone": "+919876543210",
                  "passengerCount": 2,
                  "luggageCount": 1,
                  "paymentMode": "CASH",
                  "stops": []
                }
                """, fareToken);

        MvcResult bookingResult = mockMvc.perform(post("/api/passenger/bookings")
                .contentType(MediaType.APPLICATION_JSON)
                .content(bookingPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.booking.status", is("DRIVER_SEARCHING")))
                .andExpect(jsonPath("$.booking.startOtp", notNullValue()))
                .andExpect(jsonPath("$.booking.trackingNumber", notNullValue()))
                .andReturn();

        JsonNode bookingNode = objectMapper.readTree(bookingResult.getResponse().getContentAsString());
        String bookingId = bookingNode.get("booking").get("id").asText();
        String bookingNumber = bookingNode.get("booking").get("bookingNumber").asText();
        String startOtp = bookingNode.get("booking").get("startOtp").asText();
        assertNotNull(bookingNumber);
        assertNotNull(startOtp);

        // 4. GET /api/passenger/bookings/{id}
        mockMvc.perform(get("/api/passenger/bookings/" + bookingNumber))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.booking.status", is("DRIVER_SEARCHING")))
                .andExpect(jsonPath("$.booking.startOtp", is(startOtp)))
                .andExpect(jsonPath("$.booking.driverLatitude", notNullValue()))
                .andExpect(jsonPath("$.booking.driverLongitude", notNullValue()))
                .andExpect(jsonPath("$.booking.driverBearing", notNullValue()))
                .andExpect(jsonPath("$.booking.etaMinutes", notNullValue()));

        // 5. POST /api/passenger/bookings/{id}/cancel
        String cancelPayload = """
                {
                  "reason": "Driver is taking too long"
                }
                """;

        mockMvc.perform(post("/api/passenger/bookings/" + bookingNumber + "/cancel")
                .contentType(MediaType.APPLICATION_JSON)
                .content(cancelPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.message", containsString("Ride cancelled successfully")))
                .andExpect(jsonPath("$.cancellationFee", is(0)));

        // 6. POST /api/passenger/bookings/{id}/review
        String reviewPayload = """
                {
                  "rating": 5,
                  "tags": ["Clean Car", "Polite Driver", "Smooth Ride"],
                  "feedback": "Great trip, driver arrived right on time!"
                }
                """;

        mockMvc.perform(post("/api/passenger/bookings/" + bookingNumber + "/review")
                .contentType(MediaType.APPLICATION_JSON)
                .content(reviewPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.message", is("Review submitted successfully")));
    }

    @Test
    void testCompletePackersAndMoversWorkflow() throws Exception {
        // 7. GET /api/customer/services?category=packers
        mockMvc.perform(get("/api/customer/services?category=packers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", hasSize(4)))
                .andExpect(jsonPath("$.data[?(@.code == '1_RK')].basePrice", contains(2499)))
                .andExpect(jsonPath("$.data[?(@.code == '2_BHK')].basePrice", contains(5999)))
                .andExpect(jsonPath("$.data[?(@.code == '3_BHK')].basePrice", contains(8999)));

        // 8. GET /api/addons?category=packers
        mockMvc.perform(get("/api/addons?category=packers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.addons", not(empty())))
                .andExpect(jsonPath("$.addons[?(@.addonId == 'addon-bubble-wrap')].perItemRate", contains(49.0)));

        // 9. GET /api/services/{id}/slots?date=YYYY-MM-DD
        mockMvc.perform(get("/api/services/pm-2bhk/slots?date=2026-09-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.serviceId", is("pm-2bhk")))
                .andExpect(jsonPath("$.date", is("2026-09-10")))
                .andExpect(jsonPath("$.slots", hasSize(5)))
                .andExpect(jsonPath("$.slots[0].slot", is("07:00 AM - 09:00 AM")))
                .andExpect(jsonPath("$.slots[0].isAvailable", is(true)));

        // 10. POST /api/pricing/packers
        String pricingPayload = """
                {
                  "serviceId": "pm-2bhk",
                  "distanceKm": 18.5,
                  "pickupFloor": 3,
                  "dropFloor": 2,
                  "hasElevatorPickup": false,
                  "hasElevatorDrop": true,
                  "workerCount": 4,
                  "packingTier": "PREMIUM",
                  "items": [
                    "queen_bed_1",
                    "sofa_3_seater_1",
                    "refrigerator_double_door_1",
                    "washing_machine_1",
                    "dining_table_4_chairs_1",
                    "carton_box_medium_8"
                  ],
                  "addons": [
                    { "addonId": "addon-bubble-wrap", "quantity": 4 },
                    { "addonId": "addon-dismantle-bed", "quantity": 1 }
                  ],
                  "couponCode": "SHIFT200"
                }
                """;

        mockMvc.perform(post("/api/pricing/packers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(pricingPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.baseFare", is(5999.0)))
                .andExpect(jsonPath("$.distanceFare", is(1202.5)))
                .andExpect(jsonPath("$.laborCharge", is(1600.0)))
                .andExpect(jsonPath("$.packingCharge", is(1295.0)))
                .andExpect(jsonPath("$.floorCharge", is(450.0)))
                .andExpect(jsonPath("$.gst", is(390.6)))
                .andExpect(jsonPath("$.couponDiscount", is(200.0)))
                .andExpect(jsonPath("$.totalFare", is(10737.1)));

        // 11. POST /api/bookings (Create Packers Booking)
        String packersBookingPayload = """
                {
                  "serviceCategory": "packers",
                  "serviceType": "2_BHK",
                  "serviceTitle": "2 BHK Complete Shifting",
                  "movingDate": "2026-09-10",
                  "movingSlot": "09:00 AM - 11:00 AM",
                  "pickup": {
                    "address": "Flat 302, Green Glen Layout, Bellandur, Bangalore",
                    "latitude": 12.9298,
                    "longitude": 77.6690,
                    "houseNumber": "302, Green Glen",
                    "floor": 3,
                    "hasElevator": false,
                    "hasTruckParking": true,
                    "contactName": "Anusha R",
                    "contactPhone": "+919876543210"
                  },
                  "drop": {
                    "address": "House 14, 5th Main, HSR Layout Sector 2, Bangalore",
                    "latitude": 12.9116,
                    "longitude": 77.6476,
                    "houseNumber": "House 14",
                    "floor": 2,
                    "hasElevator": true,
                    "hasTruckParking": true,
                    "contactName": "Anusha R",
                    "contactPhone": "+919876543210"
                  },
                  "distanceKm": 18.5,
                  "inventory": {
                    "queen_bed": 1,
                    "sofa_3_seater": 1,
                    "refrigerator": 1,
                    "washing_machine": 1,
                    "dining_table": 1,
                    "carton_boxes": 8
                  },
                  "packingDetails": {
                    "packingTier": "PREMIUM",
                    "dismantling": true,
                    "reassembly": true,
                    "unpacking": false
                  },
                  "pricing": {
                    "baseFare": 5999,
                    "distanceFare": 1202.5,
                    "laborCharge": 1600,
                    "packingCharge": 1295,
                    "floorCharge": 450,
                    "gst": 390.6,
                    "couponDiscount": 200,
                    "totalFare": 10737.1
                  },
                  "payment": {
                    "mode": "advance",
                    "method": "upi",
                    "advancePaid": 1000,
                    "remainingAmount": 9737.1,
                    "transactionId": "TXN_UPI_98247192"
                  }
                }
                """;

        MvcResult pmResult = mockMvc.perform(post("/api/bookings")
                .contentType(MediaType.APPLICATION_JSON)
                .content(packersBookingPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.bookingId", startsWith("PM-")))
                .andExpect(jsonPath("$.trackingNumber", startsWith("TRK-PM-")))
                .andExpect(jsonPath("$.status", is("CONFIRMED")))
                .andExpect(jsonPath("$.deliveryOtp", notNullValue()))
                .andExpect(jsonPath("$.message", containsString("Packers & Movers booking confirmed successfully")))
                .andReturn();

        JsonNode pmNode = objectMapper.readTree(pmResult.getResponse().getContentAsString());
        String pmBookingId = pmNode.get("bookingId").asText();
        String pmDeliveryOtp = pmNode.get("deliveryOtp").asText();

        // 12. GET /api/bookings/{id}/tracking
        mockMvc.perform(get("/api/bookings/" + pmBookingId + "/tracking"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.bookingId", is(pmBookingId)))
                .andExpect(jsonPath("$.status", is("IN_TRANSIT")))
                .andExpect(jsonPath("$.stageNumber", notNullValue()))
                .andExpect(jsonPath("$.stageLabel", notNullValue()))
                .andExpect(jsonPath("$.stageDescription", notNullValue()))
                .andExpect(jsonPath("$.isDelivered", is(false)))
                .andExpect(jsonPath("$.deliveryOtp", is(pmDeliveryOtp)))
                .andExpect(jsonPath("$.driver.name", containsString("Supervisor")))
                .andExpect(jsonPath("$.driver.helpersCount", is(4)))
                .andExpect(jsonPath("$.currentLocation.latitude", notNullValue()))
                .andExpect(jsonPath("$.currentLocation.etaMinutes", notNullValue()))
                .andExpect(jsonPath("$.timeline", hasSize(8)))
                .andExpect(jsonPath("$.timeline[0].title", is("Booking Confirmed")))
                .andExpect(jsonPath("$.timeline[0].completed", is(true)))
                .andExpect(jsonPath("$.timeline[0].timestamp", notNullValue()));

        // 13. POST /api/bookings/{id}/verify-otp
        mockMvc.perform(post("/api/bookings/" + pmBookingId + "/verify-otp")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"otp\": \"" + pmDeliveryOtp + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.isDelivered", is(true)))
                .andExpect(jsonPath("$.stageNumber", is(8)))
                .andExpect(jsonPath("$.message", containsString("Delivery OTP verified")));

        // 14. POST /api/bookings/{id}/reschedule
        String reschedulePayload = """
                {
                  "newDate": "2026-09-12",
                  "newSlot": "02:00 PM - 04:00 PM"
                }
                """;

        mockMvc.perform(post("/api/bookings/" + pmBookingId + "/reschedule")
                .contentType(MediaType.APPLICATION_JSON)
                .content(reschedulePayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.message", containsString("Booking rescheduled successfully to 2026-09-12 (02:00 PM - 04:00 PM)")));

        // 15. POST /api/bookings/{id}/cancel
        String cancelPayload = """
                {
                  "reason": "Shifted moving date to next month",
                  "cancelledBy": "CUSTOMER"
                }
                """;

        mockMvc.perform(post("/api/bookings/" + pmBookingId + "/cancel")
                .contentType(MediaType.APPLICATION_JSON)
                .content(cancelPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.refundAmount", notNullValue()))
                .andExpect(jsonPath("$.message", containsString("Booking cancelled successfully. Advance refund initiated.")));

        // 16. POST /api/bookings/{id}/review
        String reviewPayload = """
                {
                  "rating": 5,
                  "tags": ["Professional Team", "Punctual", "Careful Handling", "Fast Assembly"],
                  "feedback": "Flawless moving experience! All my glassware was bubble wrapped safely and no scratches."
                }
                """;

        mockMvc.perform(post("/api/bookings/" + pmBookingId + "/review")
                .contentType(MediaType.APPLICATION_JSON)
                .content(reviewPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.message", is("Review submitted successfully")));
    }
}
