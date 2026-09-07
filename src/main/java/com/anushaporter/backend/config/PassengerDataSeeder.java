package com.anushaporter.backend.config;

import com.anushaporter.backend.model.*;
import com.anushaporter.backend.repository.*;
import com.anushaporter.backend.service.PassengerPricingVersionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Component
@Order(10)
@RequiredArgsConstructor
@Slf4j
public class PassengerDataSeeder implements CommandLineRunner {

    private final PassengerServiceRepository serviceRepository;
    private final PassengerVehicleCategoryRepository categoryRepository;
    private final RentalPackageRepository rentalPackageRepository;
    private final PassengerPricingVersionRepository versionRepository;
    private final PassengerPricingRuleRepository ruleRepository;
    private final SurgeRuleRepository surgeRuleRepository;
    private final PassengerCancellationPolicyRepository cancellationPolicyRepository;
    private final CouponRepository couponRepository;
    private final PassengerPricingVersionService versionService;

    @Override
    public void run(String... args) {
        try {
            seedServices();
            seedVehicleCategories();
            seedRentalPackages();
            seedPricingVersionAndRules();
            seedSurgeRules();
            seedCancellationPolicy();
            seedCoupons();
            log.info("[PassengerDataSeeder] Passenger car services, categories, and dynamic pricing rules initialized successfully.");
        } catch (Exception e) {
            log.warn("[PassengerDataSeeder] Seeding notice: {}", e.getMessage());
        }
    }

    private void seedServices() {
        if (serviceRepository.count() == 0) {
            serviceRepository.saveAll(List.of(
                    PassengerServiceEntity.builder()
                            .serviceCode("ONE_WAY")
                            .displayName("One-Way Ride")
                            .description("Direct point-to-point passenger travel")
                            .displayOrder(1)
                            .active(true)
                            .iconUrl("🚗")
                            .build(),
                    PassengerServiceEntity.builder()
                            .serviceCode("ROUND_TRIP")
                            .displayName("Round Trip")
                            .description("Two-way trip with driver allowance and waiting")
                            .displayOrder(2)
                            .active(true)
                            .iconUrl("🔁")
                            .build(),
                    PassengerServiceEntity.builder()
                            .serviceCode("RENTAL")
                            .displayName("Local Rental")
                            .description("Hourly and kilometer fixed rental packages")
                            .displayOrder(3)
                            .active(true)
                            .iconUrl("⏱️")
                            .build(),
                    PassengerServiceEntity.builder()
                            .serviceCode("AIRPORT_TRANSFER")
                            .displayName("Airport Transfer")
                            .description("Dedicated airport pickup and drop services")
                            .displayOrder(4)
                            .active(true)
                            .iconUrl("✈️")
                            .build()
            ));
        }
    }

    private void seedVehicleCategories() {
        if (categoryRepository.count() == 0) {
            categoryRepository.saveAll(List.of(
                    PassengerVehicleCategory.builder()
                            .categoryCode("HATCHBACK")
                            .displayName("Hatchback")
                            .description("Compact & economical (Alto, WagonR, Swift)")
                            .passengerCapacity(4)
                            .luggageCapacity(2)
                            .baseFare(new BigDecimal("250.00"))
                            .perKmRate(new BigDecimal("12.00"))
                            .perHourRate(new BigDecimal("120.00"))
                            .minimumFare(new BigDecimal("250.00"))
                            .minimumKm(new BigDecimal("10.00"))
                            .driverAllowance(BigDecimal.ZERO)
                            .displayOrder(1)
                            .active(true)
                            .imageUrl("https://images.unsplash.com/photo-1541899481282-d53bffe3c35d?w=400&q=80")
                            .build(),
                    PassengerVehicleCategory.builder()
                            .categoryCode("SEDAN")
                            .displayName("Sedan")
                            .description("Comfortable & spacious (Dzire, Etios, Amaze)")
                            .passengerCapacity(4)
                            .luggageCapacity(3)
                            .baseFare(new BigDecimal("300.00"))
                            .perKmRate(new BigDecimal("14.00"))
                            .perHourRate(new BigDecimal("140.00"))
                            .minimumFare(new BigDecimal("300.00"))
                            .minimumKm(new BigDecimal("10.00"))
                            .driverAllowance(new BigDecimal("100.00"))
                            .displayOrder(2)
                            .active(true)
                            .imageUrl("https://images.unsplash.com/photo-1550355291-bbee04a92027?w=400&q=80")
                            .build(),
                    PassengerVehicleCategory.builder()
                            .categoryCode("SUV")
                            .displayName("SUV")
                            .description("Spacious family travel (Ertiga, Carens, Xylo)")
                            .passengerCapacity(6)
                            .luggageCapacity(4)
                            .baseFare(new BigDecimal("450.00"))
                            .perKmRate(new BigDecimal("18.00"))
                            .perHourRate(new BigDecimal("180.00"))
                            .minimumFare(new BigDecimal("450.00"))
                            .minimumKm(new BigDecimal("10.00"))
                            .driverAllowance(new BigDecimal("150.00"))
                            .displayOrder(3)
                            .active(true)
                            .imageUrl("https://images.unsplash.com/photo-1533473359331-0135ef1b58bf?w=400&q=80")
                            .build(),
                    PassengerVehicleCategory.builder()
                            .categoryCode("PREMIUM_SUV")
                            .displayName("Premium SUV")
                            .description("Executive comfort (Innova, Crysta)")
                            .passengerCapacity(7)
                            .luggageCapacity(4)
                            .baseFare(new BigDecimal("600.00"))
                            .perKmRate(new BigDecimal("22.00"))
                            .perHourRate(new BigDecimal("220.00"))
                            .minimumFare(new BigDecimal("600.00"))
                            .minimumKm(new BigDecimal("10.00"))
                            .driverAllowance(new BigDecimal("200.00"))
                            .displayOrder(4)
                            .active(true)
                            .imageUrl("https://images.unsplash.com/photo-1503376780353-7e6692767b70?w=400&q=80")
                            .build(),
                    PassengerVehicleCategory.builder()
                            .categoryCode("LUXURY")
                            .displayName("Luxury")
                            .description("High-end travel (Mercedes, BMW, Audi)")
                            .passengerCapacity(4)
                            .luggageCapacity(3)
                            .baseFare(new BigDecimal("1000.00"))
                            .perKmRate(new BigDecimal("35.00"))
                            .perHourRate(new BigDecimal("350.00"))
                            .minimumFare(new BigDecimal("1000.00"))
                            .minimumKm(new BigDecimal("10.00"))
                            .driverAllowance(new BigDecimal("300.00"))
                            .displayOrder(5)
                            .active(true)
                            .imageUrl("https://images.unsplash.com/photo-1563720223185-11003d516935?w=400&q=80")
                            .build()
            ));
        }
    }

    private void seedRentalPackages() {
        if (rentalPackageRepository.count() == 0) {
            rentalPackageRepository.saveAll(List.of(
                    RentalPackage.builder()
                            .packageName("4 Hours / 40 KM")
                            .vehicleCategoryCode("SEDAN")
                            .baseFare(new BigDecimal("1100.00"))
                            .includedDistanceKm(new BigDecimal("40.00"))
                            .includedHours(new BigDecimal("4.00"))
                            .extraKmRate(new BigDecimal("14.00"))
                            .extraHourRate(new BigDecimal("140.00"))
                            .driverAllowance(new BigDecimal("200.00"))
                            .active(true)
                            .build(),
                    RentalPackage.builder()
                            .packageName("8 Hours / 80 KM")
                            .vehicleCategoryCode("SEDAN")
                            .baseFare(new BigDecimal("1800.00"))
                            .includedDistanceKm(new BigDecimal("80.00"))
                            .includedHours(new BigDecimal("8.00"))
                            .extraKmRate(new BigDecimal("18.00"))
                            .extraHourRate(new BigDecimal("150.00"))
                            .driverAllowance(new BigDecimal("300.00"))
                            .active(true)
                            .build(),
                    RentalPackage.builder()
                            .packageName("12 Hours / 120 KM")
                            .vehicleCategoryCode("SEDAN")
                            .baseFare(new BigDecimal("2600.00"))
                            .includedDistanceKm(new BigDecimal("120.00"))
                            .includedHours(new BigDecimal("12.00"))
                            .extraKmRate(new BigDecimal("18.00"))
                            .extraHourRate(new BigDecimal("150.00"))
                            .driverAllowance(new BigDecimal("400.00"))
                            .active(true)
                            .build(),
                    RentalPackage.builder()
                            .packageName("8 Hours / 80 KM")
                            .vehicleCategoryCode("SUV")
                            .baseFare(new BigDecimal("2500.00"))
                            .includedDistanceKm(new BigDecimal("80.00"))
                            .includedHours(new BigDecimal("8.00"))
                            .extraKmRate(new BigDecimal("22.00"))
                            .extraHourRate(new BigDecimal("200.00"))
                            .driverAllowance(new BigDecimal("350.00"))
                            .active(true)
                            .build()
            ));
        }
    }

    private void seedPricingVersionAndRules() {
        if (versionRepository.count() == 0) {
            String vNumber = "PV-2026-09-07-01";
            PassengerPricingVersion version = PassengerPricingVersion.builder()
                    .versionNumber(vNumber)
                    .status("ACTIVE")
                    .effectiveFrom(LocalDateTime.now())
                    .createdBy("Initial Seeder")
                    .notes("Initial production dynamic pricing release")
                    .build();
            versionRepository.save(version);

            // Seed rules for each service and category
            String[] services = {"ONE_WAY", "ROUND_TRIP", "RENTAL", "AIRPORT_TRANSFER"};
            List<PassengerVehicleCategory> categories = categoryRepository.findAll();

            for (String svc : services) {
                for (PassengerVehicleCategory cat : categories) {
                    BigDecimal base = cat.getBaseFare();
                    BigDecimal perKm = cat.getPerKmRate();
                    BigDecimal minKm = cat.getMinimumKm();
                    BigDecimal minFare = cat.getMinimumFare();
                    BigDecimal allowance = cat.getDriverAllowance();

                    if ("ROUND_TRIP".equals(svc)) {
                        allowance = allowance.max(new BigDecimal("300.00"));
                    } else if ("AIRPORT_TRANSFER".equals(svc)) {
                        base = base.max(new BigDecimal("500.00"));
                        minKm = minKm.max(new BigDecimal("15.00"));
                    }

                    PassengerPricingRule rule = PassengerPricingRule.builder()
                            .pricingVersionId(vNumber)
                            .serviceCode(svc)
                            .vehicleCategoryCode(cat.getCategoryCode())
                            .baseFare(base)
                            .minimumKm(minKm)
                            .perKmRate(perKm)
                            .minimumFare(minFare)
                            .driverAllowance(allowance)
                            .freeWaitingMinutes(15)
                            .waitingChargePer15Min(new BigDecimal("50.00"))
                            .waitingChargePerHour(new BigDecimal("150.00"))
                            .nightChargeFixed(new BigDecimal("150.00"))
                            .nightStartHour(23)
                            .nightEndHour(5)
                            .firstStopFree(true)
                            .additionalStopCharge(new BigDecimal("50.00"))
                            .tollHandling("ACTUAL")
                            .fixedTollAmount(BigDecimal.ZERO)
                            .parkingHandling("ACTUAL")
                            .fixedParkingAmount(BigDecimal.ZERO)
                            .driverCommissionPercentage(new BigDecimal("20.00"))
                            .taxPercentage(new BigDecimal("5.00"))
                            .build();
                    ruleRepository.save(rule);
                }
            }
        }
    }

    private void seedSurgeRules() {
        if (surgeRuleRepository.count() == 0) {
            surgeRuleRepository.saveAll(List.of(
                    SurgeRule.builder()
                            .surgeName("Evening Peak Surge")
                            .surgeType("PEAK")
                            .multiplier(new BigDecimal("1.20"))
                            .percentage(new BigDecimal("20.00"))
                            .startTime(LocalTime.of(17, 0))
                            .endTime(LocalTime.of(21, 0))
                            .active(true)
                            .build(),
                    SurgeRule.builder()
                            .surgeName("Weekend High Demand")
                            .surgeType("WEEKEND")
                            .multiplier(new BigDecimal("1.15"))
                            .percentage(new BigDecimal("15.00"))
                            .daysOfWeek("SATURDAY,SUNDAY")
                            .active(false)
                            .build()
            ));
        }
    }

    private void seedCancellationPolicy() {
        if (cancellationPolicyRepository.count() == 0) {
            cancellationPolicyRepository.save(
                    PassengerCancellationPolicy.builder()
                            .serviceCode("ALL")
                            .freeCancellationMinutes(5)
                            .cancellationFeeBeforeArrival(new BigDecimal("50.00"))
                            .cancellationFeeAfterArrival(new BigDecimal("100.00"))
                            .customerNoShowFee(new BigDecimal("150.00"))
                            .driverWaitingFee(new BigDecimal("50.00"))
                            .active(true)
                            .build()
            );
        }
    }

    private void seedCoupons() {
        if (couponRepository.findByCodeIgnoreCase("WELCOME100").isEmpty()) {
            Coupon c1 = new Coupon();
            c1.setCode("WELCOME100");
            c1.setDescription("Flat ₹100 OFF on your ride");
            c1.setFlatDiscount(100.0);
            c1.setMinOrderAmount(400.0);
            c1.setActive(true);
            couponRepository.save(c1);
        }
        if (couponRepository.findByCodeIgnoreCase("RIDE50").isEmpty()) {
            Coupon c2 = new Coupon();
            c2.setCode("RIDE50");
            c2.setDescription("Flat ₹50 OFF");
            c2.setFlatDiscount(50.0);
            c2.setMinOrderAmount(200.0);
            c2.setActive(true);
            couponRepository.save(c2);
        }
    }
}
