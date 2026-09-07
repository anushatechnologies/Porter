package com.anushaporter.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Embeddable
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingFareBreakdown {

    @Column(name = "base_fare", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal baseFare = BigDecimal.ZERO;

    @Column(name = "distance_fare", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal distanceFare = BigDecimal.ZERO;

    @Column(name = "time_fare", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal timeFare = BigDecimal.ZERO;

    @Column(name = "driver_allowance", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal driverAllowance = BigDecimal.ZERO;

    @Column(name = "waiting_charge", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal waitingCharge = BigDecimal.ZERO;

    @Column(name = "additional_stop_charge", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal additionalStopCharge = BigDecimal.ZERO;

    @Column(name = "toll", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal toll = BigDecimal.ZERO;

    @Column(name = "parking", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal parking = BigDecimal.ZERO;

    @Column(name = "permit_charge", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal permitCharge = BigDecimal.ZERO;

    @Column(name = "night_charge", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal nightCharge = BigDecimal.ZERO;

    @Column(name = "surge_charge", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal surgeCharge = BigDecimal.ZERO;

    @Column(name = "discount", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal discount = BigDecimal.ZERO;

    @Column(name = "tax", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal tax = BigDecimal.ZERO;

    @Column(name = "total_fare", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal totalFare = BigDecimal.ZERO;

    @Column(name = "driver_earnings", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal driverEarnings = BigDecimal.ZERO;

    @Column(name = "company_commission", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal companyCommission = BigDecimal.ZERO;

    @Column(name = "applied_coupon_code", length = 50)
    private String appliedCouponCode;

    @Column(name = "surge_multiplier", precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal surgeMultiplier = BigDecimal.ONE;
}
