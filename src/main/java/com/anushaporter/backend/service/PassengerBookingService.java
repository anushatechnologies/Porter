package com.anushaporter.backend.service;

import com.anushaporter.backend.dto.PassengerBookingCreateRequest;
import com.anushaporter.backend.dto.PassengerFareEstimateRequest;
import com.anushaporter.backend.dto.PassengerFareEstimateResponse;
import com.anushaporter.backend.model.*;
import com.anushaporter.backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
@Slf4j
public class PassengerBookingService {

    private final PassengerBookingRepository bookingRepository;
    private final BookingStopRepository stopRepository;
    private final PassengerVehicleCategoryRepository vehicleCategoryRepository;
    private final PassengerPricingEngine pricingEngine;
    private final PassengerPricingVersionService versionService;
    private final PassengerCancellationPolicyRepository cancellationPolicyRepository;
    private final DriverRepository driverRepository;
    private final PassengerNotificationService notificationService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private OrderRepository orderRepository;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private AutoAssignmentService autoAssignmentService;

    @Transactional
    public PassengerBooking createBooking(PassengerBookingCreateRequest req) {
        String serviceCode = req.getServiceType() != null ? req.getServiceType().toUpperCase() : "ONE_WAY";
        String categoryCode = PassengerPricingEngine.normalizeCategoryCode(req.getVehicleCategoryCode());

        PassengerVehicleCategory category = vehicleCategoryRepository.findByCategoryCode(categoryCode)
                .orElseThrow(() -> new IllegalArgumentException("Vehicle category not found: " + categoryCode));

        int passengers = req.getPassengerCount() != null ? req.getPassengerCount() : 1;
        if (passengers > category.getPassengerCapacity()) {
            throw new PassengerCapacityExceededException(
                    "Please select a larger vehicle for this number of passengers."
            );
        }

        // Revalidate Fare via Central Pricing Engine to prevent stale pricing (Fare Locking)
        PassengerFareEstimateRequest estimateReq = PassengerFareEstimateRequest.builder()
                .serviceType(serviceCode)
                .vehicleCategoryCode(categoryCode)
                .pickupAddress(req.getPickupAddress())
                .pickupLat(req.getPickupLat())
                .pickupLng(req.getPickupLng())
                .dropAddress(req.getDropAddress())
                .dropLat(req.getDropLat())
                .dropLng(req.getDropLng())
                .passengerCount(passengers)
                .luggageCount(req.getLuggageCount())
                .scheduledPickupTime(req.getScheduledPickupTime())
                .roundTrip(req.getRoundTrip())
                .rentalPackageId(req.getRentalPackageId())
                .additionalStops(req.getAdditionalStops())
                .couponCode(req.getCouponCode())
                .build();

        PassengerFareEstimateResponse estimate = pricingEngine.calculateFare(estimateReq);

        String bookingNumber = generateBookingNumber();
        String activeVersionId = estimate.getPricingVersionId();
        String startOtp = String.format("%04d", ThreadLocalRandom.current().nextInt(1000, 10000));

        PassengerBooking booking = PassengerBooking.builder()
                .bookingNumber(bookingNumber)
                .startOtp(startOtp)
                .customerId(req.getCustomerId())
                .customerName(req.getCustomerName() != null ? req.getCustomerName() : "Customer")
                .customerPhone(req.getCustomerPhone() != null ? req.getCustomerPhone() : "N/A")
                .customerEmail(req.getCustomerEmail())
                .serviceType(serviceCode)
                .vehicleCategoryCode(categoryCode)
                .passengerCount(passengers)
                .luggageCount(req.getLuggageCount() != null ? req.getLuggageCount() : 1)
                .pickupAddress(req.getPickupAddress())
                .pickupLatitude(req.getPickupLat())
                .pickupLongitude(req.getPickupLng())
                .dropAddress(req.getDropAddress())
                .dropLatitude(req.getDropLat())
                .dropLongitude(req.getDropLng())
                .roundTrip(Boolean.TRUE.equals(req.getRoundTrip()))
                .scheduledPickupTime(req.getScheduledPickupTime() != null ? req.getScheduledPickupTime() : LocalDateTime.now())
                .returnTime(req.getReturnTime())
                .rentalPackageId(req.getRentalPackageId())
                .distanceKm(estimate.getDistanceKm())
                .durationMinutes(estimate.getDurationMinutes())
                .pricingVersionId(activeVersionId) // Snapshot of exact version used
                .fareLockToken(estimate.getFareLockToken())
                .fareLockExpiresAt(estimate.getFareLockExpiresAt())
                .fareBreakdown(estimate.getBreakdown())
                .status(PassengerBookingStatus.DRIVER_SEARCHING)
                .paymentStatus("PENDING")
                .paymentMethod(req.getPaymentMethod() != null ? req.getPaymentMethod() : "CASH")
                .build();

        PassengerBooking savedBooking = bookingRepository.save(booking);

        // Save Intermediate Stops
        if (req.getAdditionalStops() != null && !req.getAdditionalStops().isEmpty()) {
            int order = 1;
            for (String stopAddress : req.getAdditionalStops()) {
                BookingStop stop = BookingStop.builder()
                        .bookingId(savedBooking.getId())
                        .stopOrder(order++)
                        .address(stopAddress)
                        .build();
                stopRepository.save(stop);
            }
        }

        notificationService.sendEvent(
                PassengerNotificationService.EventType.BOOKING_CREATED,
                savedBooking,
                savedBooking.getCustomerPhone(),
                savedBooking.getCustomerEmail(),
                "Your passenger car booking has been requested successfully."
        );

        if (orderRepository != null) {
            try {
                Order order = new Order();
                order.setBookingId(savedBooking.getBookingNumber());
                order.setUserEmail(savedBooking.getCustomerEmail());
                order.setServiceName(categoryCode);
                order.setServiceType("PASSENGER");
                order.setPassengerCount(passengers);
                order.setPickupAddress(savedBooking.getPickupAddress());
                order.setDropAddress(savedBooking.getDropAddress());
                order.setPickupLat(savedBooking.getPickupLatitude());
                order.setPickupLng(savedBooking.getPickupLongitude());
                order.setDropLat(savedBooking.getDropLatitude());
                order.setDropLng(savedBooking.getDropLongitude());
                order.setAmount(estimate.getBreakdown() != null && estimate.getBreakdown().getTotalFare() != null
                        ? estimate.getBreakdown().getTotalFare().doubleValue() : 50.0);
                order.setStatus("searching");
                order.setDeliveryOtp(startOtp);
                order.setStartOtp(startOtp);
                order.setCreatedAt(LocalDateTime.now());
                orderRepository.save(order);

                if (autoAssignmentService != null) {
                    autoAssignmentService.startAutoAssignment(savedBooking.getBookingNumber());
                }
            } catch (Exception e) {
                log.warn("Failed to auto-assign passenger order {}: {}", savedBooking.getBookingNumber(), e.getMessage());
            }
        }

        return savedBooking;
    }

    @Transactional
    public PassengerBooking assignDriver(Long bookingId, Long driverId, String adminNotes) {
        PassengerBooking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found: " + bookingId));

        if (!booking.getStatus().canTransitionTo(PassengerBookingStatus.DRIVER_ASSIGNED)) {
            throw new IllegalStateException("Cannot assign driver to booking in status: " + booking.getStatus());
        }

        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new IllegalArgumentException("Driver not found: " + driverId));

        // Assign driver details atomically
        booking.setDriverId(driver.getId());
        booking.setDriverName(driver.getName() != null ? driver.getName() : "Driver #" + driver.getId());
        booking.setDriverPhone(driver.getPhone());
        booking.setVehicleNumber(driver.getVehicleNumber() != null ? driver.getVehicleNumber() : "AP-" + driver.getId());
        booking.setVehicleModel(driver.getVehicle() != null ? driver.getVehicle() : booking.getVehicleCategoryCode());
        booking.setDriverAssignedAt(LocalDateTime.now());
        booking.setStatus(PassengerBookingStatus.DRIVER_ASSIGNED);

        PassengerBooking updated = bookingRepository.save(booking);

        notificationService.sendEvent(
                PassengerNotificationService.EventType.DRIVER_ASSIGNED,
                updated,
                updated.getCustomerPhone(),
                updated.getCustomerEmail(),
                "Driver " + updated.getDriverName() + " has been assigned to your ride."
        );

        return updated;
    }

    @Transactional
    public PassengerBooking updateBookingStatus(Long bookingId, PassengerBookingStatus nextStatus) {
        return updateStatus(bookingId, nextStatus);
    }

    @Transactional
    public PassengerBooking updateStatus(Long bookingId, PassengerBookingStatus nextStatus) {
        PassengerBooking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found: " + bookingId));

        if (!booking.getStatus().canTransitionTo(nextStatus)) {
            throw new IllegalStateException(String.format("Invalid state transition from %s to %s",
                    booking.getStatus(), nextStatus));
        }

        booking.setStatus(nextStatus);

        if (nextStatus == PassengerBookingStatus.TRIP_STARTED) {
            booking.setTripStartedAt(LocalDateTime.now());
            notificationService.sendEvent(PassengerNotificationService.EventType.TRIP_STARTED, booking,
                    booking.getCustomerPhone(), booking.getCustomerEmail(), "Trip has started.");
        } else if (nextStatus == PassengerBookingStatus.TRIP_COMPLETED) {
            booking.setTripCompletedAt(LocalDateTime.now());
            booking.setPaymentStatus("PAID"); // default on completion unless flagged
            notificationService.sendEvent(PassengerNotificationService.EventType.TRIP_COMPLETED, booking,
                    booking.getCustomerPhone(), booking.getCustomerEmail(), "Trip completed. Thank you for riding with us!");
        } else if (nextStatus == PassengerBookingStatus.DRIVER_ARRIVED) {
            notificationService.sendEvent(PassengerNotificationService.EventType.DRIVER_ARRIVED, booking,
                    booking.getCustomerPhone(), booking.getCustomerEmail(), "Your driver has arrived at pickup.");
        } else if (nextStatus == PassengerBookingStatus.DRIVER_ARRIVING) {
            notificationService.sendEvent(PassengerNotificationService.EventType.DRIVER_ARRIVING, booking,
                    booking.getCustomerPhone(), booking.getCustomerEmail(), "Driver is on the way.");
        }

        return bookingRepository.save(booking);
    }

    @Transactional
    public PassengerBooking cancelBooking(Long bookingId, String cancelledBy, String reason) {
        PassengerBooking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found: " + bookingId));

        PassengerBookingStatus targetStatus;
        if ("DRIVER".equalsIgnoreCase(cancelledBy)) {
            targetStatus = PassengerBookingStatus.CANCELLED_BY_DRIVER;
        } else if ("ADMIN".equalsIgnoreCase(cancelledBy)) {
            targetStatus = PassengerBookingStatus.CANCELLED_BY_ADMIN;
        } else {
            targetStatus = PassengerBookingStatus.CANCELLED_BY_CUSTOMER;
        }

        if (!booking.getStatus().canTransitionTo(targetStatus)) {
            throw new IllegalStateException("Booking in status " + booking.getStatus() + " cannot be cancelled.");
        }

        // Calculate cancellation fee based on policy
        BigDecimal fee = BigDecimal.ZERO;
        Optional<PassengerCancellationPolicy> policyOpt = cancellationPolicyRepository
                .findFirstByServiceCodeAndActiveTrue(booking.getServiceType())
                .or(cancellationPolicyRepository::findFirstByActiveTrue);

        if (policyOpt.isPresent()) {
            PassengerCancellationPolicy policy = policyOpt.get();
            if (booking.getStatus() == PassengerBookingStatus.DRIVER_ARRIVED) {
                fee = policy.getCancellationFeeAfterArrival();
            } else if (booking.getStatus() == PassengerBookingStatus.DRIVER_ASSIGNED || booking.getStatus() == PassengerBookingStatus.DRIVER_ARRIVING) {
                fee = policy.getCancellationFeeBeforeArrival();
            }
        }

        booking.setStatus(targetStatus);
        booking.setCancelledBy(cancelledBy != null ? cancelledBy : "CUSTOMER");
        booking.setCancellationReason(reason);
        booking.setCancelledAt(LocalDateTime.now());
        booking.setCancellationFee(fee);

        PassengerBooking updated = bookingRepository.save(booking);

        notificationService.sendEvent(
                PassengerNotificationService.EventType.BOOKING_CANCELLED,
                updated,
                updated.getCustomerPhone(),
                updated.getCustomerEmail(),
                "Booking cancelled. Cancellation fee: ₹" + fee
        );

        return updated;
    }

    @Transactional
    public PassengerBooking completePayment(Long bookingId, String paymentMethod, String txRef) {
        PassengerBooking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found: " + bookingId));
        booking.setPaymentStatus("PAID");
        if (paymentMethod != null) {
            booking.setPaymentMethod(paymentMethod);
        }
        return bookingRepository.save(booking);
    }

    @Transactional
    public PassengerBooking rateTrip(Long bookingId, Integer rating, String reviewNotes) {
        PassengerBooking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found: " + bookingId));
        booking.setDriverRating(rating);
        booking.setReviewNotes(reviewNotes);
        return bookingRepository.save(booking);
    }

    private String generateBookingNumber() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyMMddHHmmss"));
        int rand = ThreadLocalRandom.current().nextInt(1000, 9999);
        return "AP-CAR-" + timestamp + "-" + rand;
    }
}
