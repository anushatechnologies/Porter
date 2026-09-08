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

import java.util.*;

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
    public ResponseEntity<Map<String, Object>> createBooking(@RequestBody PassengerBookingCreateRequest request) {
        PassengerBooking booking = bookingService.createBooking(request);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("booking", booking);
        // Include direct booking fields for backward compatibility
        response.put("id", booking.getBookingNumber());
        response.put("bookingNumber", booking.getBookingNumber());
        response.put("trackingNumber", booking.getTrackingNumber());
        response.put("status", booking.getStatus().name());
        response.put("startOtp", booking.getStartOtp());
        return ResponseEntity.status(201).body(response);
    }

    @GetMapping("/bookings/{id}")
    public ResponseEntity<Map<String, Object>> getBookingById(@PathVariable String id) {
        Optional<PassengerBooking> bookingOpt = findBookingByIdOrNumber(id);
        if (bookingOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of(
                "success", true,
                "booking", bookingOpt.get()
        ));
    }

    @GetMapping("/bookings/by-number/{bookingNumber}")
    public ResponseEntity<Map<String, Object>> getBookingByNumber(@PathVariable String bookingNumber) {
        Optional<PassengerBooking> bookingOpt = bookingRepository.findByBookingNumber(bookingNumber);
        if (bookingOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of(
                "success", true,
                "booking", bookingOpt.get()
        ));
    }

    @GetMapping("/bookings/customer/{phone}")
    public ResponseEntity<List<PassengerBooking>> getCustomerBookings(@PathVariable String phone) {
        return ResponseEntity.ok(bookingRepository.findByCustomerPhoneOrderByCreatedAtDesc(phone));
    }

    @PostMapping("/bookings/{id}/cancel")
    public ResponseEntity<Map<String, Object>> cancelBooking(
            @PathVariable String id,
            @RequestBody(required = false) Map<String, String> payload
    ) {
        Optional<PassengerBooking> bookingOpt = findBookingByIdOrNumber(id);
        if (bookingOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        String cancelledBy = (payload != null && payload.containsKey("cancelledBy")) ? payload.get("cancelledBy") : "CUSTOMER";
        String reason = (payload != null && payload.containsKey("reason")) ? payload.get("reason") : "Ride cancelled by user";
        PassengerBooking cancelled = bookingService.cancelBooking(bookingOpt.get().getId(), cancelledBy, reason);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("message", "Ride cancelled successfully");
        response.put("cancellationFee", cancelled.getCancellationFee() != null ? cancelled.getCancellationFee() : 0);
        response.put("booking", cancelled);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/bookings/{id}/payment")
    public ResponseEntity<PassengerBooking> processPayment(
            @PathVariable String id,
            @RequestBody(required = false) Map<String, String> payload
    ) {
        Optional<PassengerBooking> bookingOpt = findBookingByIdOrNumber(id);
        if (bookingOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        String paymentMethod = (payload != null && payload.containsKey("paymentMethod")) ? payload.get("paymentMethod") : "ONLINE";
        String txRef = (payload != null && payload.containsKey("transactionRef")) ? payload.get("transactionRef") : "TX-" + System.currentTimeMillis();
        PassengerBooking paid = bookingService.completePayment(bookingOpt.get().getId(), paymentMethod, txRef);
        return ResponseEntity.ok(paid);
    }

    @PostMapping({"/bookings/{id}/review", "/bookings/{id}/rate"})
    public ResponseEntity<Map<String, Object>> rateTrip(
            @PathVariable String id,
            @RequestBody(required = false) Map<String, Object> payload
    ) {
        Optional<PassengerBooking> bookingOpt = findBookingByIdOrNumber(id);
        if (bookingOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Integer rating = (payload != null && payload.containsKey("rating")) ? Integer.valueOf(payload.get("rating").toString()) : 5;
        String notes = (payload != null && payload.containsKey("feedback")) ? payload.get("feedback").toString()
                : (payload != null && payload.containsKey("notes") ? payload.get("notes").toString() : "");
        PassengerBooking rated = bookingService.rateTrip(bookingOpt.get().getId(), rating, notes);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Review submitted successfully",
                "booking", rated
        ));
    }

    @GetMapping({"/categories", "/vehicles"})
    public ResponseEntity<Map<String, Object>> getVehicleCategories() {
        List<PassengerVehicleCategory> categories = vehicleCategoryRepository.findByActiveTrueOrderByDisplayOrderAsc();
        List<Map<String, Object>> data = categories.stream().map(c -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", String.valueOf(c.getId()));
            m.put("code", c.getCategoryCode());
            m.put("name", c.getDisplayName());
            m.put("description", c.getDescription());
            m.put("imageUrl", c.getImageUrl() != null ? c.getImageUrl() : "");
            m.put("passengerCapacity", c.getPassengerCapacity());
            m.put("luggageCapacity", c.getLuggageCapacity());
            m.put("basePrice", c.getBaseFare());
            m.put("perKmRate", c.getPerKmRate());
            m.put("isActive", Boolean.TRUE.equals(c.getActive()));
            m.put("displayOrder", c.getDisplayOrder());
            return m;
        }).toList();

        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", data
        ));
    }

    @GetMapping("/services")
    public ResponseEntity<List<PassengerServiceEntity>> getServices() {
        return ResponseEntity.ok(serviceRepository.findByActiveTrueOrderByDisplayOrderAsc());
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

    private Optional<PassengerBooking> findBookingByIdOrNumber(String id) {
        try {
            Long num = Long.parseLong(id);
            Optional<PassengerBooking> byId = bookingRepository.findById(num);
            if (byId.isPresent()) return byId;
        } catch (NumberFormatException ignored) {}

        Optional<PassengerBooking> byNum = bookingRepository.findByBookingNumber(id);
        if (byNum.isPresent()) return byNum;

        // Strip prefixes like TRK-PASS-
        String sanitized = id.replace("TRK-PASS-", "").replace("TRK-", "").replace("PB-", "");
        try {
            Long num = Long.parseLong(sanitized);
            Optional<PassengerBooking> bySanitized = bookingRepository.findById(num);
            if (bySanitized.isPresent()) return bySanitized;
        } catch (NumberFormatException ignored) {}

        return bookingRepository.findByBookingNumber("AP-CAR-" + sanitized);
    }
}
