package com.anushaporter.backend;

import com.anushaporter.backend.dto.PassengerFareEstimateRequest;
import com.anushaporter.backend.dto.PassengerFareEstimateResponse;
import com.anushaporter.backend.model.*;
import com.anushaporter.backend.repository.*;
import com.anushaporter.backend.service.PassengerCapacityExceededException;
import com.anushaporter.backend.service.PassengerPricingEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.mockito.Mockito;
import software.amazon.awssdk.services.s3.S3Client;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = BackendApplication.class)
@Import(PassengerPricingEngineTest.TestConfig.class)
public class PassengerPricingEngineTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        public S3Client mockS3Client() {
            return Mockito.mock(S3Client.class);
        }
    }

    @Autowired
    private PassengerPricingEngine pricingEngine;

    @Autowired
    private PassengerVehicleCategoryRepository categoryRepository;

    @Autowired
    private PassengerPricingRuleRepository ruleRepository;

    @Autowired
    private RentalPackageRepository rentalPackageRepository;

    @Autowired
    private CouponRepository couponRepository;

    @BeforeEach
    void setUp() {
        // Ensure standard Sedan exists
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

        // Ensure coupon WELCOME100 exists
        if (couponRepository.findByCodeIgnoreCase("WELCOME100").isEmpty()) {
            Coupon c = new Coupon();
            c.setCode("WELCOME100");
            c.setDescription("Flat 100 off");
            c.setFlatDiscount(100.0);
            c.setMinOrderAmount(400.0);
            c.setActive(true);
            couponRepository.save(c);
        }

        // Ensure RentalPackage for SEDAN exists
        if (rentalPackageRepository.findByVehicleCategoryCodeAndActiveTrue("SEDAN").isEmpty()) {
            rentalPackageRepository.save(RentalPackage.builder()
                    .packageName("8 Hours / 80 KM")
                    .vehicleCategoryCode("SEDAN")
                    .baseFare(new BigDecimal("1800.00"))
                    .includedDistanceKm(new BigDecimal("80.00"))
                    .includedHours(new BigDecimal("8.00"))
                    .extraKmRate(new BigDecimal("18.00"))
                    .extraHourRate(new BigDecimal("150.00"))
                    .driverAllowance(new BigDecimal("300.00"))
                    .active(true)
                    .build());
        }
    }

    @Test
    void testOneWayPricingFormula_Sedan25Km() {
        // As per prompt Example:
        // Sedan: Base = 300, Min KM = 10, Per KM = 14, Driver Allowance = 100
        // Distance = 25 KM -> Extra KM = 15 -> Distance Fare = 15 * 14 = 210
        // Driver Allowance = 100
        // Subtotal = 300 + 210 + 100 = 610
        // Plus toll = 80 (distance > 20 km), tax 5%
        PassengerFareEstimateRequest req = PassengerFareEstimateRequest.builder()
                .serviceType("ONE_WAY")
                .vehicleCategoryCode("SEDAN")
                .passengerCount(3)
                .manualDistanceKm(new BigDecimal("25.00"))
                .manualDurationMinutes(45)
                .scheduledPickupTime(LocalDateTime.of(2026, 9, 7, 14, 0)) // 2 PM (daytime, no night)
                .build();

        PassengerFareEstimateResponse res = pricingEngine.calculateFare(req);

        assertNotNull(res);
        assertNotNull(res.getBreakdown());
        assertEquals(new BigDecimal("300.00"), res.getBreakdown().getBaseFare());
        assertEquals(new BigDecimal("210.00"), res.getBreakdown().getDistanceFare());
        assertEquals(new BigDecimal("100.00"), res.getBreakdown().getDriverAllowance());
        assertTrue(res.getBreakdown().getTotalFare().compareTo(new BigDecimal("600.00")) > 0);
        assertTrue(res.getBreakdown().getDriverEarnings().compareTo(BigDecimal.ZERO) > 0);
        assertTrue(res.getBreakdown().getCompanyCommission().compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    void testPassengerCapacityExceeded_ThrowsException() {
        // Sedan max capacity is 4. Requesting 6 passengers must throw PassengerCapacityExceededException
        PassengerFareEstimateRequest req = PassengerFareEstimateRequest.builder()
                .serviceType("ONE_WAY")
                .vehicleCategoryCode("SEDAN")
                .passengerCount(6)
                .manualDistanceKm(new BigDecimal("15.00"))
                .build();

        PassengerCapacityExceededException ex = assertThrows(
                PassengerCapacityExceededException.class,
                () -> pricingEngine.calculateFare(req)
        );

        assertEquals("Please select a larger vehicle for this number of passengers.", ex.getMessage());
    }

    @Test
    void testRentalPackagePricing() {
        // Find or create 8 Hours / 80 KM package
        RentalPackage pkg = rentalPackageRepository.findByVehicleCategoryCodeAndActiveTrue("SEDAN").stream()
                .filter(p -> p.getPackageName().contains("8 Hours"))
                .findFirst()
                .orElseGet(() -> rentalPackageRepository.save(RentalPackage.builder()
                        .packageName("8 Hours / 80 KM")
                        .vehicleCategoryCode("SEDAN")
                        .baseFare(new BigDecimal("1800.00"))
                        .includedDistanceKm(new BigDecimal("80.00"))
                        .includedHours(new BigDecimal("8.00"))
                        .extraKmRate(new BigDecimal("18.00"))
                        .extraHourRate(new BigDecimal("150.00"))
                        .driverAllowance(new BigDecimal("300.00"))
                        .active(true)
                        .build()));

        // Case 1: Trip within package limits (70 KM, 6 hours)
        PassengerFareEstimateRequest reqWithin = PassengerFareEstimateRequest.builder()
                .serviceType("RENTAL")
                .vehicleCategoryCode("SEDAN")
                .rentalPackageId(pkg.getId())
                .passengerCount(2)
                .manualDistanceKm(new BigDecimal("70.00")) // within 80 KM
                .manualDurationMinutes(360) // 6 hours (within 8 hours)
                .scheduledPickupTime(LocalDateTime.of(2026, 9, 7, 10, 0))
                .build();

        PassengerFareEstimateResponse resWithin = pricingEngine.calculateFare(reqWithin);

        assertNotNull(resWithin);
        assertNotNull(resWithin.getBreakdown());
        assertEquals(BigDecimal.ZERO, resWithin.getBreakdown().getDistanceFare()); // No excess distance
        assertEquals(BigDecimal.ZERO, resWithin.getBreakdown().getTimeFare()); // No excess time
        assertEquals(new BigDecimal("1800.00"), resWithin.getBreakdown().getBaseFare());

        // Case 2: Excess distance (90 KM -> 10 KM excess @ ₹18/KM = ₹180)
        PassengerFareEstimateRequest reqExcess = PassengerFareEstimateRequest.builder()
                .serviceType("RENTAL")
                .vehicleCategoryCode("SEDAN")
                .rentalPackageId(pkg.getId())
                .passengerCount(2)
                .manualDistanceKm(new BigDecimal("90.00")) // 10 KM excess
                .manualDurationMinutes(360) // 6 hours (within 8 hours)
                .scheduledPickupTime(LocalDateTime.of(2026, 9, 7, 10, 0))
                .build();

        PassengerFareEstimateResponse resExcess = pricingEngine.calculateFare(reqExcess);
        assertEquals(new BigDecimal("180.00"), resExcess.getBreakdown().getDistanceFare());
    }

    @Test
    void testCouponDiscountApplied() {
        PassengerFareEstimateRequest req = PassengerFareEstimateRequest.builder()
                .serviceType("ONE_WAY")
                .vehicleCategoryCode("SEDAN")
                .passengerCount(2)
                .manualDistanceKm(new BigDecimal("25.00"))
                .couponCode("WELCOME100")
                .scheduledPickupTime(LocalDateTime.of(2026, 9, 7, 12, 0))
                .build();

        PassengerFareEstimateResponse res = pricingEngine.calculateFare(req);

        assertNotNull(res);
        assertEquals(new BigDecimal("100.00"), res.getBreakdown().getDiscount());
        assertEquals("WELCOME100", res.getBreakdown().getAppliedCouponCode());
    }

    @Test
    void testNightChargeAppliedDuringNightWindow() {
        // 11:30 PM (23:30) is in night window (23:00 - 05:00)
        PassengerFareEstimateRequest req = PassengerFareEstimateRequest.builder()
                .serviceType("ONE_WAY")
                .vehicleCategoryCode("SEDAN")
                .passengerCount(2)
                .manualDistanceKm(new BigDecimal("15.00"))
                .scheduledPickupTime(LocalDateTime.of(2026, 9, 7, 23, 30))
                .build();

        PassengerFareEstimateResponse res = pricingEngine.calculateFare(req);

        assertNotNull(res);
        assertEquals(new BigDecimal("150.00"), res.getBreakdown().getNightCharge());
    }

    @Test
    void testBikePricingFormula_AliasAndCapacity() {
        if (categoryRepository.findByCategoryCode("BIKE").isEmpty()) {
            categoryRepository.save(PassengerVehicleCategory.builder()
                    .categoryCode("BIKE")
                    .displayName("Bike")
                    .passengerCapacity(1)
                    .luggageCapacity(1)
                    .baseFare(new BigDecimal("20.00"))
                    .minimumKm(new BigDecimal("1.50"))
                    .perKmRate(new BigDecimal("8.00"))
                    .minimumFare(new BigDecimal("20.00"))
                    .driverAllowance(BigDecimal.ZERO)
                    .displayOrder(0)
                    .active(true)
                    .build());
        }

        // 1. Valid Bike ride using alias "2_WHEELER"
        PassengerFareEstimateRequest req = PassengerFareEstimateRequest.builder()
                .serviceType("ONE_WAY")
                .vehicleCategoryCode("2_WHEELER")
                .passengerCount(1)
                .manualDistanceKm(new BigDecimal("10.00"))
                .scheduledPickupTime(LocalDateTime.of(2026, 9, 7, 14, 0))
                .build();

        PassengerFareEstimateResponse res = pricingEngine.calculateFare(req);
        assertNotNull(res);
        assertEquals("BIKE", res.getVehicleCategoryCode());
        assertEquals(1, res.getPassengerCapacity());

        // 2. Reject if passenger count > 1
        PassengerFareEstimateRequest invalidReq = PassengerFareEstimateRequest.builder()
                .serviceType("ONE_WAY")
                .vehicleCategoryCode("BIKE")
                .passengerCount(2)
                .manualDistanceKm(new BigDecimal("10.00"))
                .scheduledPickupTime(LocalDateTime.of(2026, 9, 7, 14, 0))
                .build();

        assertThrows(PassengerCapacityExceededException.class, () -> pricingEngine.calculateFare(invalidReq));
    }
}
