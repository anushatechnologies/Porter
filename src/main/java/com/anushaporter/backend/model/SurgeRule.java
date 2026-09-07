package com.anushaporter.backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalTime;

@Entity
@Table(name = "passenger_surge_rules")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SurgeRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "surge_name", nullable = false, length = 100)
    private String surgeName; // e.g. "Evening Peak", "Festival Surge"

    @Column(name = "surge_type", nullable = false, length = 50)
    private String surgeType; // NORMAL, PEAK, NIGHT, WEEKEND, FESTIVAL, HIGH_DEMAND, CUSTOM

    @Column(name = "multiplier", precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal multiplier = BigDecimal.ONE; // e.g. 1.20 for 20% surge

    @Column(name = "percentage", precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal percentage = BigDecimal.ZERO; // e.g. 20.00 for 20%

    @Column(name = "fixed_amount", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal fixedAmount = BigDecimal.ZERO;

    @Column(name = "start_time")
    private LocalTime startTime;

    @Column(name = "end_time")
    private LocalTime endTime;

    @Column(name = "days_of_week", length = 100)
    private String daysOfWeek; // e.g. "SATURDAY,SUNDAY" or null for all

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;
}
