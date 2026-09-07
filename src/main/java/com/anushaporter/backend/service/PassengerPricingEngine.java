package com.anushaporter.backend.service;

import com.anushaporter.backend.dto.PassengerFareEstimateRequest;
import com.anushaporter.backend.dto.PassengerFareEstimateResponse;
import com.anushaporter.backend.model.*;
import com.anushaporter.backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PassengerPricingEngine {

    private final PassengerVehicleCategoryRepository vehicleCategoryRepository;
    private final PassengerServiceRepository serviceRepository;
    private final PassengerPricingRuleRepository pricingRuleRepository;
    private final RentalPackageRepository rentalPackageRepository;
    private final PassengerZoneRepository zoneRepository;
    private final SurgeRuleRepository surgeRuleRepository;
    private final CouponRepository couponRepository;
    private final PassengerPricingVersionService versionService;
    private final MapsDirectionsProvider mapsDirectionsProvider;

    /**
     * Central Dynamic Pricing Pipeline.
     */
    public PassengerFareEstimateResponse calculateFare(PassengerFareEstimateRequest req) {
        String serviceCode = req.getServiceType() != null ? req.getServiceType().toUpperCase() : "ONE_WAY";
        String categoryCode = req.getVehicleCategoryCode() != null ? req.getVehicleCategoryCode().toUpperCase() : "SEDAN";

        // 1. Validate Vehicle Category & Passenger Capacity
        PassengerVehicleCategory category = vehicleCategoryRepository.findByCategoryCode(categoryCode)
                .orElseThrow(() -> new IllegalArgumentException("Vehicle category not found: " + categoryCode));

        int passengers = req.getPassengerCount() != null ? req.getPassengerCount() : 1;
        if (passengers > category.getPassengerCapacity()) {
            throw new PassengerCapacityExceededException(
                    "Please select a larger vehicle for this number of passengers."
            );
        }

        // 2. Resolve Active Pricing Version & Rule
        PassengerPricingVersion activeVersion = versionService.getActiveVersion();
        String versionId = activeVersion.getVersionNumber();

        PassengerPricingRule rule = pricingRuleRepository
                .findByPricingVersionIdAndServiceCodeAndVehicleCategoryCode(versionId, serviceCode, categoryCode)
                .orElseGet(() -> pricingRuleRepository
                        .findFirstByServiceCodeAndVehicleCategoryCodeOrderByIdDesc(serviceCode, categoryCode)
                        .orElseGet(() -> createFallbackRule(versionId, serviceCode, category)));

        // 3. Determine Route Distance and Duration
        BigDecimal distanceKm;
        int durationMinutes;

        if (req.getManualDistanceKm() != null && req.getManualDistanceKm().compareTo(BigDecimal.ZERO) > 0) {
            distanceKm = req.getManualDistanceKm().setScale(2, RoundingMode.HALF_UP);
            durationMinutes = req.getManualDurationMinutes() != null ? req.getManualDurationMinutes() :
                    (int) Math.round(distanceKm.doubleValue() * 2);
        } else if (req.getPickupLat() != null && req.getDropLat() != null) {
            MapsDirectionsProvider.RouteDetails route = mapsDirectionsProvider.getRoute(
                    req.getPickupLat(), req.getPickupLng(),
                    req.getDropLat(), req.getDropLng(),
                    req.getAdditionalStops()
            );
            distanceKm = route.getDistanceKm();
            durationMinutes = route.getDurationMinutes();
        } else {
            MapsDirectionsProvider.RouteDetails route = mapsDirectionsProvider.calculateEstimatedRoute(
                    req.getPickupAddress(), req.getDropAddress(), req.getAdditionalStops()
            );
            distanceKm = route.getDistanceKm();
            durationMinutes = route.getDurationMinutes();
        }

        // 4. Calculate Base & Distance Fare based on Service Type
        BigDecimal baseFare = rule.getBaseFare();
        BigDecimal distanceFare = BigDecimal.ZERO;
        BigDecimal timeFare = BigDecimal.ZERO;
        BigDecimal driverAllowance = rule.getDriverAllowance();
        BigDecimal effectiveDistance = distanceKm;

        if ("ROUND_TRIP".equalsIgnoreCase(serviceCode)) {
            effectiveDistance = distanceKm.multiply(BigDecimal.valueOf(2));
            BigDecimal minKm = rule.getMinimumKm();
            if (effectiveDistance.compareTo(minKm) <= 0) {
                distanceFare = BigDecimal.ZERO;
            } else {
                BigDecimal extraKm = effectiveDistance.subtract(minKm);
                distanceFare = extraKm.multiply(rule.getPerKmRate()).setScale(2, RoundingMode.HALF_UP);
            }
            if (driverAllowance.compareTo(BigDecimal.ZERO) == 0) {
                driverAllowance = new BigDecimal("300.00");
            }
        } else if ("RENTAL".equalsIgnoreCase(serviceCode)) {
            // Rental package logic
            RentalPackage rentalPackage = null;
            if (req.getRentalPackageId() != null) {
                rentalPackage = rentalPackageRepository.findById(req.getRentalPackageId()).orElse(null);
            }
            if (rentalPackage == null) {
                List<RentalPackage> packages = rentalPackageRepository.findByVehicleCategoryCodeAndActiveTrue(categoryCode);
                if (!packages.isEmpty()) {
                    rentalPackage = packages.get(0);
                }
            }

            if (rentalPackage != null) {
                baseFare = rentalPackage.getBaseFare();
                driverAllowance = rentalPackage.getDriverAllowance();
                BigDecimal incDist = rentalPackage.getIncludedDistanceKm();
                BigDecimal incHours = rentalPackage.getIncludedHours();

                if (distanceKm.compareTo(incDist) > 0) {
                    BigDecimal excessKm = distanceKm.subtract(incDist);
                    distanceFare = excessKm.multiply(rentalPackage.getExtraKmRate()).setScale(2, RoundingMode.HALF_UP);
                }
                double durationHours = durationMinutes / 60.0;
                if (durationHours > incHours.doubleValue()) {
                    double excessHours = durationHours - incHours.doubleValue();
                    timeFare = BigDecimal.valueOf(excessHours).multiply(rentalPackage.getExtraHourRate()).setScale(2, RoundingMode.HALF_UP);
                }
            } else {
                // Fallback rental calculation (e.g. 40km package)
                baseFare = new BigDecimal("1200.00");
                if (distanceKm.compareTo(new BigDecimal("40.00")) > 0) {
                    BigDecimal excessKm = distanceKm.subtract(new BigDecimal("40.00"));
                    distanceFare = excessKm.multiply(rule.getPerKmRate()).setScale(2, RoundingMode.HALF_UP);
                }
            }
        } else if ("AIRPORT_TRANSFER".equalsIgnoreCase(serviceCode)) {
            // Airport specific base & minimum fare
            baseFare = rule.getBaseFare().max(new BigDecimal("500.00"));
            BigDecimal minKm = rule.getMinimumKm().max(new BigDecimal("15.00"));
            if (distanceKm.compareTo(minKm) > 0) {
                BigDecimal extraKm = distanceKm.subtract(minKm);
                distanceFare = extraKm.multiply(rule.getPerKmRate()).setScale(2, RoundingMode.HALF_UP);
            }
        } else {
            // Standard ONE_WAY
            BigDecimal minKm = rule.getMinimumKm();
            if (distanceKm.compareTo(minKm) <= 0) {
                distanceFare = BigDecimal.ZERO;
            } else {
                BigDecimal extraKm = distanceKm.subtract(minKm);
                distanceFare = extraKm.multiply(rule.getPerKmRate()).setScale(2, RoundingMode.HALF_UP);
            }
        }

        // Check against minimum fare
        BigDecimal baseSubtotal = baseFare.add(distanceFare);
        if (baseSubtotal.compareTo(rule.getMinimumFare()) < 0) {
            baseFare = rule.getMinimumFare();
            distanceFare = BigDecimal.ZERO;
        }

        // Per-minute rate if applicable
        if (rule.getPerMinuteRate() != null && rule.getPerMinuteRate().compareTo(BigDecimal.ZERO) > 0 && !"RENTAL".equalsIgnoreCase(serviceCode)) {
            timeFare = BigDecimal.valueOf(durationMinutes).multiply(rule.getPerMinuteRate()).setScale(2, RoundingMode.HALF_UP);
        }

        // 5. Waiting Charges
        BigDecimal waitingCharge = BigDecimal.ZERO;
        int requestedWaiting = req.getWaitingMinutes() != null ? req.getWaitingMinutes() : 0;
        int freeMins = rule.getFreeWaitingMinutes() != null ? rule.getFreeWaitingMinutes() : 15;
        if (requestedWaiting > freeMins) {
            int chargeableMins = requestedWaiting - freeMins;
            int intervals = (int) Math.ceil(chargeableMins / 15.0);
            waitingCharge = rule.getWaitingChargePer15Min().multiply(BigDecimal.valueOf(intervals)).setScale(2, RoundingMode.HALF_UP);
        }

        // 6. Additional Stop Charges
        BigDecimal additionalStopCharge = BigDecimal.ZERO;
        if (req.getAdditionalStops() != null && !req.getAdditionalStops().isEmpty()) {
            int stopCount = req.getAdditionalStops().size();
            int chargeableStops = Boolean.TRUE.equals(rule.getFirstStopFree()) ? Math.max(0, stopCount - 1) : stopCount;
            additionalStopCharge = rule.getAdditionalStopCharge().multiply(BigDecimal.valueOf(chargeableStops)).setScale(2, RoundingMode.HALF_UP);
        }

        // 7. Toll & Parking Charges
        BigDecimal toll = BigDecimal.ZERO;
        if ("FIXED".equalsIgnoreCase(rule.getTollHandling())) {
            toll = rule.getFixedTollAmount();
        } else if ("ACTUAL".equalsIgnoreCase(rule.getTollHandling())) {
            // Estimated typical toll for distance > 20 km
            if (distanceKm.compareTo(new BigDecimal("20.00")) > 0) {
                toll = new BigDecimal("80.00");
            }
        }

        BigDecimal parking = BigDecimal.ZERO;
        if ("FIXED".equalsIgnoreCase(rule.getParkingHandling())) {
            parking = rule.getFixedParkingAmount();
        } else if ("AIRPORT_TRANSFER".equalsIgnoreCase(serviceCode)) {
            parking = new BigDecimal("100.00"); // Standard Airport parking
        }

        BigDecimal permitCharge = BigDecimal.ZERO;
        if (distanceKm.compareTo(new BigDecimal("100.00")) > 0) {
            permitCharge = new BigDecimal("150.00"); // Outstation interstate permit
        }

        // 8. Night Charges (e.g. 11:00 PM – 5:00 AM)
        BigDecimal nightCharge = BigDecimal.ZERO;
        LocalDateTime tripTime = req.getScheduledPickupTime() != null ? req.getScheduledPickupTime() : LocalDateTime.now();
        int hour = tripTime.getHour();
        int nightStart = rule.getNightStartHour() != null ? rule.getNightStartHour() : 23;
        int nightEnd = rule.getNightEndHour() != null ? rule.getNightEndHour() : 5;

        boolean isNight = (hour >= nightStart || hour < nightEnd);
        if (isNight) {
            if (rule.getNightChargeFixed() != null && rule.getNightChargeFixed().compareTo(BigDecimal.ZERO) > 0) {
                nightCharge = rule.getNightChargeFixed();
            } else if (rule.getNightChargePercentage() != null && rule.getNightChargePercentage().compareTo(BigDecimal.ZERO) > 0) {
                nightCharge = baseSubtotal.multiply(rule.getNightChargePercentage()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            }
        }

        // 9. Surge Pricing
        BigDecimal surgeMultiplier = BigDecimal.ONE;
        BigDecimal surgeCharge = BigDecimal.ZERO;
        SurgeRule activeSurge = resolveSurgeRule(tripTime.toLocalTime());
        if (activeSurge != null) {
            if (activeSurge.getMultiplier() != null && activeSurge.getMultiplier().compareTo(BigDecimal.ONE) > 0) {
                surgeMultiplier = activeSurge.getMultiplier();
                BigDecimal surgeFactor = surgeMultiplier.subtract(BigDecimal.ONE);
                surgeCharge = baseSubtotal.multiply(surgeFactor).setScale(2, RoundingMode.HALF_UP);
            } else if (activeSurge.getPercentage() != null && activeSurge.getPercentage().compareTo(BigDecimal.ZERO) > 0) {
                surgeCharge = baseSubtotal.multiply(activeSurge.getPercentage()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            } else if (activeSurge.getFixedAmount() != null && activeSurge.getFixedAmount().compareTo(BigDecimal.ZERO) > 0) {
                surgeCharge = activeSurge.getFixedAmount();
            }
        }

        // Subtotal before discounts & taxes
        BigDecimal subtotal = baseFare
                .add(distanceFare)
                .add(timeFare)
                .add(driverAllowance)
                .add(waitingCharge)
                .add(additionalStopCharge)
                .add(toll)
                .add(parking)
                .add(permitCharge)
                .add(nightCharge)
                .add(surgeCharge);

        // 10. Coupon & Discounts
        BigDecimal discount = BigDecimal.ZERO;
        String appliedCouponCode = null;
        if (req.getCouponCode() != null && !req.getCouponCode().isBlank()) {
            Optional<Coupon> couponOpt = couponRepository.findByCodeIgnoreCase(req.getCouponCode().trim());
            if (couponOpt.isPresent() && Boolean.TRUE.equals(couponOpt.get().getActive())) {
                Coupon coupon = couponOpt.get();
                double minAmount = coupon.getMinOrderAmount() != null ? coupon.getMinOrderAmount() : 0.0;
                if (subtotal.doubleValue() >= minAmount) {
                    appliedCouponCode = coupon.getCode();
                    if (coupon.getFlatDiscount() != null && coupon.getFlatDiscount() > 0) {
                        discount = BigDecimal.valueOf(coupon.getFlatDiscount()).setScale(2, RoundingMode.HALF_UP);
                    } else if (coupon.getDiscountPercentage() != null && coupon.getDiscountPercentage() > 0) {
                        BigDecimal pctDiscount = subtotal.multiply(BigDecimal.valueOf(coupon.getDiscountPercentage()))
                                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                        if (coupon.getMaxDiscount() != null && coupon.getMaxDiscount() > 0) {
                            pctDiscount = pctDiscount.min(BigDecimal.valueOf(coupon.getMaxDiscount()));
                        }
                        discount = pctDiscount;
                    }
                    // Discount cannot exceed subtotal
                    discount = discount.min(subtotal);
                }
            }
        }

        // 11. Applicable Taxes (e.g. 5% GST)
        BigDecimal taxableAmount = subtotal.subtract(discount).max(BigDecimal.ZERO);
        BigDecimal taxPercentage = rule.getTaxPercentage() != null ? rule.getTaxPercentage() : new BigDecimal("5.00");
        BigDecimal tax = taxableAmount.multiply(taxPercentage)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        // 12. Final Total Customer Fare
        BigDecimal totalFare = taxableAmount.add(tax).setScale(2, RoundingMode.HALF_UP);

        // 13. Financial Distribution (Driver Earnings vs Company Commission)
        BigDecimal commissionPercentage = rule.getDriverCommissionPercentage() != null ?
                rule.getDriverCommissionPercentage() : new BigDecimal("20.00");
        BigDecimal commissionableBase = totalFare.subtract(toll).subtract(parking).subtract(tax).max(BigDecimal.ZERO);
        BigDecimal companyCommission = commissionableBase.multiply(commissionPercentage)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal driverEarnings = totalFare.subtract(companyCommission).subtract(tax).subtract(toll).subtract(parking).max(BigDecimal.ZERO);

        BookingFareBreakdown breakdown = BookingFareBreakdown.builder()
                .baseFare(baseFare)
                .distanceFare(distanceFare)
                .timeFare(timeFare)
                .driverAllowance(driverAllowance)
                .waitingCharge(waitingCharge)
                .additionalStopCharge(additionalStopCharge)
                .toll(toll)
                .parking(parking)
                .permitCharge(permitCharge)
                .nightCharge(nightCharge)
                .surgeCharge(surgeCharge)
                .discount(discount)
                .tax(tax)
                .totalFare(totalFare)
                .driverEarnings(driverEarnings)
                .companyCommission(companyCommission)
                .appliedCouponCode(appliedCouponCode)
                .surgeMultiplier(surgeMultiplier)
                .build();

        String token = UUID.randomUUID().toString();
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(5);

        return PassengerFareEstimateResponse.builder()
                .fareLockToken(token)
                .fareLockExpiresAt(expiresAt)
                .pricingVersionId(versionId)
                .serviceType(serviceCode)
                .vehicleCategoryCode(categoryCode)
                .vehicleDisplayName(category.getDisplayName())
                .passengerCapacity(category.getPassengerCapacity())
                .luggageCapacity(category.getLuggageCapacity())
                .distanceKm(effectiveDistance)
                .durationMinutes(durationMinutes)
                .breakdown(breakdown)
                .message("Fare locked for 5 minutes")
                .build();
    }

    private SurgeRule resolveSurgeRule(LocalTime time) {
        List<SurgeRule> activeSurges = surgeRuleRepository.findByActiveTrue();
        for (SurgeRule s : activeSurges) {
            if (s.getStartTime() != null && s.getEndTime() != null) {
                if (!time.isBefore(s.getStartTime()) && !time.isAfter(s.getEndTime())) {
                    return s;
                }
            } else if ("NORMAL".equalsIgnoreCase(s.getSurgeType()) && s.getMultiplier().compareTo(BigDecimal.ONE) > 0) {
                return s;
            }
        }
        return null;
    }

    private PassengerPricingRule createFallbackRule(String versionId, String serviceCode, PassengerVehicleCategory cat) {
        return PassengerPricingRule.builder()
                .pricingVersionId(versionId)
                .serviceCode(serviceCode)
                .vehicleCategoryCode(cat.getCategoryCode())
                .baseFare(cat.getBaseFare() != null ? cat.getBaseFare() : new BigDecimal("300.00"))
                .minimumKm(cat.getMinimumKm() != null ? cat.getMinimumKm() : new BigDecimal("10.00"))
                .perKmRate(cat.getPerKmRate() != null ? cat.getPerKmRate() : new BigDecimal("14.00"))
                .minimumFare(cat.getMinimumFare() != null ? cat.getMinimumFare() : new BigDecimal("300.00"))
                .driverAllowance(cat.getDriverAllowance() != null ? cat.getDriverAllowance() : BigDecimal.ZERO)
                .freeWaitingMinutes(15)
                .waitingChargePer15Min(new BigDecimal("50.00"))
                .waitingChargePerHour(new BigDecimal("150.00"))
                .nightChargeFixed(new BigDecimal("150.00"))
                .nightStartHour(23)
                .nightEndHour(5)
                .firstStopFree(true)
                .additionalStopCharge(new BigDecimal("50.00"))
                .tollHandling("ACTUAL")
                .parkingHandling("ACTUAL")
                .driverCommissionPercentage(new BigDecimal("20.00"))
                .taxPercentage(new BigDecimal("5.00"))
                .build();
    }
}
