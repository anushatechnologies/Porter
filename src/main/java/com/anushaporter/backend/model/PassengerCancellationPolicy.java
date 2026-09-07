package com.anushaporter.backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "passenger_cancellation_policies")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PassengerCancellationPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "service_code", length = 50)
    @Builder.Default
    private String serviceCode = "ALL";

    @Column(name = "free_cancellation_minutes")
    @Builder.Default
    private Integer freeCancellationMinutes = 5;

    @Column(name = "cancellation_fee_before_arrival", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal cancellationFeeBeforeArrival = new BigDecimal("50.00");

    @Column(name = "cancellation_fee_after_arrival", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal cancellationFeeAfterArrival = new BigDecimal("100.00");

    @Column(name = "customer_no_show_fee", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal customerNoShowFee = new BigDecimal("150.00");

    @Column(name = "driver_waiting_fee", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal driverWaitingFee = new BigDecimal("50.00");

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;
}
