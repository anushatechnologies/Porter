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
import java.util.Optional;

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
        // Default vehicle category seeding is disabled: Only admin-added passenger vehicles are retained.
        try {
            categoryRepository.findByCategoryCode("AUTO").ifPresent(auto -> {
                if (auto.getDisplayOrder() == null || auto.getDisplayOrder() == 0) {
                    auto.setDisplayOrder(1);
                    categoryRepository.save(auto);
                }
            });
            categoryRepository.findByCategoryCode("HATCHBACK").ifPresent(hatchback -> {
                if (hatchback.getImageUrl() == null || hatchback.getImageUrl().contains("unsplash.com")) {
                    hatchback.setImageUrl("https://poteranusha.s3.ap-south-2.amazonaws.com/vehicles/cab.png");
                    categoryRepository.save(hatchback);
                }
            });
        } catch (Exception ignored) {}
    }

    private void seedRentalPackages() {
        if (rentalPackageRepository.count() == 0) {
            rentalPackageRepository.saveAll(List.of(
                    RentalPackage.builder()
                            .packageName("2 Hours / 20 KM")
                            .vehicleCategoryCode("BIKE")
                            .baseFare(new BigDecimal("200.00"))
                            .includedDistanceKm(new BigDecimal("20.00"))
                            .includedHours(new BigDecimal("2.00"))
                            .extraKmRate(new BigDecimal("8.00"))
                            .extraHourRate(new BigDecimal("60.00"))
                            .driverAllowance(BigDecimal.ZERO)
                            .active(true)
                            .build(),
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
        } else if (rentalPackageRepository.findByVehicleCategoryCodeAndActiveTrue("BIKE").isEmpty()) {
            rentalPackageRepository.save(RentalPackage.builder()
                    .packageName("2 Hours / 20 KM")
                    .vehicleCategoryCode("BIKE")
                    .baseFare(new BigDecimal("200.00"))
                    .includedDistanceKm(new BigDecimal("20.00"))
                    .includedHours(new BigDecimal("2.00"))
                    .extraKmRate(new BigDecimal("8.00"))
                    .extraHourRate(new BigDecimal("60.00"))
                    .driverAllowance(BigDecimal.ZERO)
                    .active(true)
                    .build());
        }
    }

    private void seedPricingVersionAndRules() {
        String vNumber = "PV-2026-09-07-01";
        if (versionRepository.count() == 0) {
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
                    savePricingRule(vNumber, svc, cat);
                }
            }
        } else {
            // If pricing version already exists, ensure rules for BIKE are also seeded
            List<PassengerPricingVersion> allVersions = versionRepository.findAll();
            Optional<PassengerVehicleCategory> bikeOpt = categoryRepository.findByCategoryCode("BIKE");
            if (bikeOpt.isPresent()) {
                PassengerVehicleCategory bike = bikeOpt.get();
                String[] services = {"ONE_WAY", "ROUND_TRIP", "RENTAL", "AIRPORT_TRANSFER"};
                for (PassengerPricingVersion ver : allVersions) {
                    for (String svc : services) {
                        if (ruleRepository.findByPricingVersionIdAndServiceCodeAndVehicleCategoryCode(
                                ver.getVersionNumber(), svc, "BIKE").isEmpty()) {
                            savePricingRule(ver.getVersionNumber(), svc, bike);
                        }
                    }
                }
            }
        }
    }

    private void savePricingRule(String versionNumber, String svc, PassengerVehicleCategory cat) {
        BigDecimal base = cat.getBaseFare();
        BigDecimal perKm = cat.getPerKmRate();
        BigDecimal minKm = cat.getMinimumKm();
        BigDecimal minFare = cat.getMinimumFare();
        BigDecimal allowance = cat.getDriverAllowance();
        boolean isBike = "BIKE".equalsIgnoreCase(cat.getCategoryCode());

        if ("ROUND_TRIP".equals(svc)) {
            if (!isBike) {
                allowance = allowance.max(new BigDecimal("300.00"));
            }
        } else if ("AIRPORT_TRANSFER".equals(svc)) {
            if (isBike) {
                base = base.max(new BigDecimal("80.00"));
                minKm = minKm.max(new BigDecimal("5.00"));
            } else {
                base = base.max(new BigDecimal("500.00"));
                minKm = minKm.max(new BigDecimal("15.00"));
            }
        }

        int freeWaiting = isBike ? 10 : 15;
        BigDecimal waiting15Min = isBike ? new BigDecimal("20.00") : new BigDecimal("50.00");
        BigDecimal waitingHour = isBike ? new BigDecimal("60.00") : new BigDecimal("150.00");
        BigDecimal nightCharge = isBike ? new BigDecimal("50.00") : new BigDecimal("150.00");
        BigDecimal stopCharge = isBike ? new BigDecimal("20.00") : new BigDecimal("50.00");
        BigDecimal commPct = isBike ? new BigDecimal("15.00") : new BigDecimal("20.00");

        PassengerPricingRule rule = PassengerPricingRule.builder()
                .pricingVersionId(versionNumber)
                .serviceCode(svc)
                .vehicleCategoryCode(cat.getCategoryCode())
                .baseFare(base)
                .minimumKm(minKm)
                .perKmRate(perKm)
                .minimumFare(minFare)
                .driverAllowance(allowance)
                .freeWaitingMinutes(freeWaiting)
                .waitingChargePer15Min(waiting15Min)
                .waitingChargePerHour(waitingHour)
                .nightChargeFixed(nightCharge)
                .nightStartHour(23)
                .nightEndHour(5)
                .firstStopFree(true)
                .additionalStopCharge(stopCharge)
                .tollHandling("ACTUAL")
                .fixedTollAmount(BigDecimal.ZERO)
                .parkingHandling("ACTUAL")
                .fixedParkingAmount(BigDecimal.ZERO)
                .driverCommissionPercentage(commPct)
                .taxPercentage(new BigDecimal("5.00"))
                .build();
        ruleRepository.save(rule);
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
