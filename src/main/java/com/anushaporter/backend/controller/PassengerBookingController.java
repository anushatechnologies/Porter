package com.anushaporter.backend.controller;

import com.anushaporter.backend.dto.PassengerBookingCreateRequest;
import com.anushaporter.backend.dto.PassengerFareEstimateRequest;
import com.anushaporter.backend.dto.PassengerFareEstimateResponse;
import com.anushaporter.backend.model.PassengerBooking;
import com.anushaporter.backend.model.PassengerServiceEntity;
import com.anushaporter.backend.model.PassengerVehicleCategory;
import com.anushaporter.backend.model.RentalPackage;
import com.anushaporter.backend.repository.PassengerBookingRepository;
import com.anushaporter.backend.repository.PassengerServiceRepository;
import com.anushaporter.backend.repository.PassengerVehicleCategoryRepository;
import com.anushaporter.backend.repository.RentalPackageRepository;
import com.anushaporter.backend.service.PassengerBookingService;
import com.anushaporter.backend.service.PassengerPricingEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/passenger")
@RequiredArgsConstructor
@Slf4j
public class PassengerBookingController {

    private final PassengerPricingEngine pricingEngine;
    private final PassengerBookingService bookingService;
    private final PassengerBookingRepository bookingRepository;
    private final PassengerServiceRepository serviceRepository;
    private final PassengerVehicleCategoryRepository vehicleCategoryRepository;
    private final RentalPackageRepository rentalPackageRepository;

    @PostMapping("/fare-estimate")
    public ResponseEntity<PassengerFareEstimateResponse> getFareEstimate(@RequestBody PassengerFareEstimateRequest request) {
        PassengerFareEstimateResponse response = pricingEngine.calculateFare(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/bookings")
    public ResponseEntity<PassengerBooking> createBooking(@RequestBody PassengerBookingCreateRequest request) {
        PassengerBooking booking = bookingService.createBooking(request);
        return ResponseEntity.ok(booking);
    }

    @GetMapping("/bookings/{id}")
    public ResponseEntity<PassengerBooking> getBookingById(@PathVariable Long id) {
        return bookingRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/bookings/by-number/{bookingNumber}")
    public ResponseEntity<PassengerBooking> getBookingByNumber(@PathVariable String bookingNumber) {
        return bookingRepository.findByBookingNumber(bookingNumber)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/bookings/customer/{phone}")
    public ResponseEntity<List<PassengerBooking>> getCustomerBookings(@PathVariable String phone) {
        return ResponseEntity.ok(bookingRepository.findByCustomerPhoneOrderByCreatedAtDesc(phone));
    }

    @PostMapping("/bookings/{id}/cancel")
    public ResponseEntity<PassengerBooking> cancelBooking(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> payload
    ) {
        String cancelledBy = (payload != null && payload.containsKey("cancelledBy")) ? payload.get("cancelledBy") : "CUSTOMER";
        String reason = (payload != null && payload.containsKey("reason")) ? payload.get("reason") : "Customer requested cancellation";
        PassengerBooking cancelled = bookingService.cancelBooking(id, cancelledBy, reason);
        return ResponseEntity.ok(cancelled);
    }

    @PostMapping("/bookings/{id}/payment")
    public ResponseEntity<PassengerBooking> processPayment(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> payload
    ) {
        String paymentMethod = (payload != null && payload.containsKey("paymentMethod")) ? payload.get("paymentMethod") : "ONLINE";
        String txRef = (payload != null && payload.containsKey("transactionRef")) ? payload.get("transactionRef") : "TX-" + System.currentTimeMillis();
        PassengerBooking paid = bookingService.completePayment(id, paymentMethod, txRef);
        return ResponseEntity.ok(paid);
    }

    @PostMapping("/bookings/{id}/rate")
    public ResponseEntity<PassengerBooking> rateTrip(
            @PathVariable Long id,
            @RequestBody Map<String, Object> payload
    ) {
        Integer rating = payload.containsKey("rating") ? Integer.valueOf(payload.get("rating").toString()) : 5;
        String notes = payload.containsKey("notes") ? payload.get("notes").toString() : "";
        PassengerBooking rated = bookingService.rateTrip(id, rating, notes);
        return ResponseEntity.ok(rated);
    }

    @GetMapping("/services")
    public ResponseEntity<List<PassengerServiceEntity>> getServices() {
        return ResponseEntity.ok(serviceRepository.findByActiveTrueOrderByDisplayOrderAsc());
    }

    @GetMapping("/vehicles")
    public ResponseEntity<List<PassengerVehicleCategory>> getVehicles() {
        return ResponseEntity.ok(vehicleCategoryRepository.findByActiveTrueOrderByDisplayOrderAsc());
    }

    @GetMapping("/vehicles/available")
    public ResponseEntity<List<PassengerVehicleCategory>> getAvailableVehicles(
            @RequestParam(required = false) Integer passengers
    ) {
        if (passengers != null && passengers > 0) {
            return ResponseEntity.ok(vehicleCategoryRepository
                    .findByActiveTrueAndPassengerCapacityGreaterThanEqualOrderByDisplayOrderAsc(passengers));
        }
        return ResponseEntity.ok(vehicleCategoryRepository.findByActiveTrueOrderByDisplayOrderAsc());
    }

    @GetMapping("/rental-packages")
    public ResponseEntity<List<RentalPackage>> getRentalPackages(
            @RequestParam(required = false) String vehicleCategoryCode
    ) {
        if (vehicleCategoryCode != null && !vehicleCategoryCode.isBlank()) {
            return ResponseEntity.ok(rentalPackageRepository
                    .findByVehicleCategoryCodeAndActiveTrue(vehicleCategoryCode.toUpperCase()));
        }
        return ResponseEntity.ok(rentalPackageRepository.findByActiveTrue());
    }
}
