package com.anushaporter.backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "passenger_bookings", indexes = {
        @Index(name = "idx_pbooking_num", columnList = "booking_number", unique = true),
        @Index(name = "idx_pbooking_status", columnList = "status"),
        @Index(name = "idx_pbooking_driver", columnList = "driver_id"),
        @Index(name = "idx_pbooking_customer", columnList = "customer_phone")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PassengerBooking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "booking_number", unique = true, nullable = false, length = 50)
    private String bookingNumber; // e.g. "AP-CAR-100245"

    @Column(name = "customer_id")
    private Long customerId;

    @Column(name = "customer_name", length = 100)
    private String customerName;

    @Column(name = "customer_phone", length = 20)
    private String customerPhone;

    @Column(name = "customer_email", length = 100)
    private String customerEmail;

    @Column(name = "service_type", nullable = false, length = 50)
    private String serviceType; // ONE_WAY, ROUND_TRIP, RENTAL, AIRPORT_TRANSFER

    @Column(name = "vehicle_category_code", nullable = false, length = 50)
    private String vehicleCategoryCode; // HATCHBACK, SEDAN, SUV, PREMIUM_SUV, LUXURY

    @Column(name = "passenger_count", nullable = false)
    @Builder.Default
    private Integer passengerCount = 1;

    @Column(name = "luggage_count")
    @Builder.Default
    private Integer luggageCount = 1;

    @Column(name = "pickup_address", nullable = false, length = 500)
    private String pickupAddress;

    @Column(name = "pickup_latitude")
    private Double pickupLatitude;

    @Column(name = "pickup_longitude")
    private Double pickupLongitude;

    @Column(name = "drop_address", length = 500)
    private String dropAddress;

    @Column(name = "drop_latitude")
    private Double dropLatitude;

    @Column(name = "drop_longitude")
    private Double dropLongitude;

    @Column(name = "round_trip")
    @Builder.Default
    private Boolean roundTrip = false;

    @Column(name = "scheduled_pickup_time")
    private LocalDateTime scheduledPickupTime;

    @Column(name = "return_time")
    private LocalDateTime returnTime;

    @Column(name = "rental_package_id")
    private Long rentalPackageId;

    @Column(name = "rental_package_name", length = 100)
    private String rentalPackageName;

    @Column(name = "distance_km", precision = 8, scale = 2)
    @Builder.Default
    private BigDecimal distanceKm = BigDecimal.ZERO;

    @Column(name = "duration_minutes")
    @Builder.Default
    private Integer durationMinutes = 0;

    @Column(name = "pricing_version_id", nullable = false, length = 50)
    private String pricingVersionId; // Snapshot e.g. PV-2026-09-07-01

    @Column(name = "fare_lock_token", length = 100)
    private String fareLockToken;

    @Column(name = "fare_lock_expires_at")
    private LocalDateTime fareLockExpiresAt;

    @Embedded
    private BookingFareBreakdown fareBreakdown;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    @Builder.Default
    private PassengerBookingStatus status = PassengerBookingStatus.REQUESTED;

    @Column(name = "driver_id")
    private Long driverId;

    @Column(name = "driver_name", length = 100)
    private String driverName;

    @Column(name = "driver_phone", length = 20)
    private String driverPhone;

    @Column(name = "vehicle_number", length = 30)
    private String vehicleNumber;

    @Column(name = "vehicle_model", length = 50)
    private String vehicleModel;

    @Column(name = "driver_assigned_at")
    private LocalDateTime driverAssignedAt;

    @Column(name = "trip_started_at")
    private LocalDateTime tripStartedAt;

    @Column(name = "trip_completed_at")
    private LocalDateTime tripCompletedAt;

    @Column(name = "payment_status", length = 30)
    @Builder.Default
    private String paymentStatus = "PENDING"; // PENDING, PAID, FAILED, REFUNDED

    @Column(name = "payment_method", length = 30)
    @Builder.Default
    private String paymentMethod = "CASH"; // CASH, ONLINE, WALLET

    @Column(name = "cancellation_reason", length = 255)
    private String cancellationReason;

    @Column(name = "cancelled_by", length = 50)
    private String cancelledBy;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "cancellation_fee", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal cancellationFee = BigDecimal.ZERO;

    @Column(name = "driver_rating")
    private Integer driverRating;

    @Column(name = "review_notes", length = 500)
    private String reviewNotes;

    @Version
    @Column(name = "opt_lock_version")
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
