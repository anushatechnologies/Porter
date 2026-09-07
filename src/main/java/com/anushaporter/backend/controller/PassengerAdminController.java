package com.anushaporter.backend.controller;

import com.anushaporter.backend.dto.PassengerDriverAssignRequest;
import com.anushaporter.backend.dto.PassengerFareEstimateRequest;
import com.anushaporter.backend.dto.PassengerFareEstimateResponse;
import com.anushaporter.backend.model.*;
import com.anushaporter.backend.repository.*;
import com.anushaporter.backend.service.PassengerBookingService;
import com.anushaporter.backend.service.PassengerPricingEngine;
import com.anushaporter.backend.service.PassengerPricingVersionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/admin/passenger")
@RequiredArgsConstructor
@Slf4j
public class PassengerAdminController {

    private final PassengerServiceRepository serviceRepository;
    private final PassengerVehicleCategoryRepository vehicleCategoryRepository;
    private final RentalPackageRepository rentalPackageRepository;
    private final PassengerPricingVersionRepository versionRepository;
    private final PassengerPricingRuleRepository ruleRepository;
    private final PricingAuditLogRepository auditLogRepository;
    private final PassengerZoneRepository zoneRepository;
    private final SurgeRuleRepository surgeRuleRepository;
    private final PassengerCancellationPolicyRepository cancellationPolicyRepository;
    private final PassengerBookingRepository bookingRepository;
    private final PassengerPricingEngine pricingEngine;
    private final PassengerPricingVersionService versionService;
    private final PassengerBookingService bookingService;

    // --- 1. SERVICES MANAGEMENT ---
    @GetMapping("/services")
    public ResponseEntity<List<PassengerServiceEntity>> getAllServices() {
        return ResponseEntity.ok(serviceRepository.findAllByOrderByDisplayOrderAsc());
    }

    @PostMapping("/services")
    public ResponseEntity<PassengerServiceEntity> createService(@RequestBody PassengerServiceEntity svc) {
        PassengerServiceEntity saved = serviceRepository.save(svc);
        versionService.logAudit(versionService.getActiveVersion().getVersionNumber(), "SERVICE", saved.getId(),
                "Admin", "admin@anushaporter.com", "CREATE", "serviceCode", "-", saved.getServiceCode(), "Added new service");
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/services/{id}")
    public ResponseEntity<PassengerServiceEntity> updateService(@PathVariable Long id, @RequestBody PassengerServiceEntity updated) {
        return serviceRepository.findById(id).map(s -> {
            versionService.logAudit(versionService.getActiveVersion().getVersionNumber(), "SERVICE", id,
                    "Admin", "admin@anushaporter.com", "UPDATE", "displayName", s.getDisplayName(), updated.getDisplayName(), "Updated service details");
            updated.setId(id);
            return ResponseEntity.ok(serviceRepository.save(updated));
        }).orElse(ResponseEntity.notFound().build());
    }

    // --- 2. VEHICLE CATEGORIES ---
    @GetMapping("/vehicle-categories")
    public ResponseEntity<List<PassengerVehicleCategory>> getAllVehicleCategories() {
        return ResponseEntity.ok(vehicleCategoryRepository.findAllByOrderByDisplayOrderAsc());
    }

    @PostMapping("/vehicle-categories")
    public ResponseEntity<PassengerVehicleCategory> createVehicleCategory(@RequestBody PassengerVehicleCategory cat) {
        PassengerVehicleCategory saved = vehicleCategoryRepository.save(cat);
        versionService.logAudit(versionService.getActiveVersion().getVersionNumber(), "VEHICLE_CATEGORY", saved.getId(),
                "Admin", "admin@anushaporter.com", "CREATE", "categoryCode", "-", saved.getCategoryCode(), "Added vehicle category");
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/vehicle-categories/{id}")
    public ResponseEntity<PassengerVehicleCategory> updateVehicleCategory(@PathVariable Long id, @RequestBody PassengerVehicleCategory updated) {
        return vehicleCategoryRepository.findById(id).map(cat -> {
            versionService.logAudit(versionService.getActiveVersion().getVersionNumber(), "VEHICLE_CATEGORY", id,
                    "Admin", "admin@anushaporter.com", "UPDATE", "perKmRate",
                    String.valueOf(cat.getPerKmRate()), String.valueOf(updated.getPerKmRate()), "Updated vehicle rates");
            updated.setId(id);
            return ResponseEntity.ok(vehicleCategoryRepository.save(updated));
        }).orElse(ResponseEntity.notFound().build());
    }

    // --- 3. DYNAMIC PRICING RULES & VERSION CONTROL ---
    @GetMapping("/pricing")
    public ResponseEntity<List<PassengerPricingRule>> getActivePricingRules() {
        PassengerPricingVersion active = versionService.getActiveVersion();
        return ResponseEntity.ok(ruleRepository.findByPricingVersionId(active.getVersionNumber()));
    }

    @PostMapping("/pricing")
    public ResponseEntity<PassengerPricingRule> savePricingRule(@RequestBody PassengerPricingRule rule) {
        PassengerPricingVersion active = versionService.getActiveVersion();
        rule.setPricingVersionId(active.getVersionNumber());
        PassengerPricingRule saved = ruleRepository.save(rule);
        versionService.logAudit(active.getVersionNumber(), "PRICING_RULE", saved.getId(),
                "Admin", "admin@anushaporter.com", "SAVE", "baseFare", "-", String.valueOf(saved.getBaseFare()), "Saved pricing rule");
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/pricing/{id}")
    public ResponseEntity<PassengerPricingRule> updatePricingRule(@PathVariable Long id, @RequestBody PassengerPricingRule updated) {
        return ruleRepository.findById(id).map(r -> {
            versionService.logAudit(r.getPricingVersionId(), "PRICING_RULE", id,
                    "Admin", "admin@anushaporter.com", "UPDATE", "perKmRate",
                    String.valueOf(r.getPerKmRate()), String.valueOf(updated.getPerKmRate()), "Updated pricing rule values");
            updated.setId(id);
            return ResponseEntity.ok(ruleRepository.save(updated));
        }).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/pricing/versions")
    public ResponseEntity<List<PassengerPricingVersion>> getPricingVersions() {
        return ResponseEntity.ok(versionRepository.findAllByOrderByCreatedAtDesc());
    }

    @PostMapping("/pricing/versions/publish")
    public ResponseEntity<PassengerPricingVersion> publishNewVersion(
            @RequestBody(required = false) Map<String, String> payload
    ) {
        String adminName = (payload != null && payload.containsKey("adminName")) ? payload.get("adminName") : "Admin";
        String notes = (payload != null && payload.containsKey("notes")) ? payload.get("notes") : "Published new dynamic pricing release";
        PassengerPricingVersion version = versionService.publishNewVersion(adminName, "admin@anushaporter.com", notes);
        return ResponseEntity.ok(version);
    }

    @GetMapping("/pricing/history")
    public ResponseEntity<List<PricingAuditLog>> getPricingAuditHistory() {
        return ResponseEntity.ok(auditLogRepository.findAllByOrderByTimestampDesc());
    }

    // --- 4. MANDATORY PRICING PREVIEW TOOL ---
    @PostMapping("/pricing/preview")
    public ResponseEntity<PassengerFareEstimateResponse> previewPricing(@RequestBody PassengerFareEstimateRequest request) {
        PassengerFareEstimateResponse preview = pricingEngine.calculateFare(request);
        return ResponseEntity.ok(preview);
    }

    // --- 5. RENTAL PACKAGES ---
    @GetMapping("/rental-packages")
    public ResponseEntity<List<RentalPackage>> getAllRentalPackages() {
        return ResponseEntity.ok(rentalPackageRepository.findAll());
    }

    @PostMapping("/rental-packages")
    public ResponseEntity<RentalPackage> createRentalPackage(@RequestBody RentalPackage pkg) {
        return ResponseEntity.ok(rentalPackageRepository.save(pkg));
    }

    @PutMapping("/rental-packages/{id}")
    public ResponseEntity<RentalPackage> updateRentalPackage(@PathVariable Long id, @RequestBody RentalPackage updated) {
        return rentalPackageRepository.findById(id).map(pkg -> {
            updated.setId(id);
            return ResponseEntity.ok(rentalPackageRepository.save(updated));
        }).orElse(ResponseEntity.notFound().build());
    }

    // --- 6. SURGE RULES ---
    @GetMapping("/surge-rules")
    public ResponseEntity<List<SurgeRule>> getAllSurgeRules() {
        return ResponseEntity.ok(surgeRuleRepository.findAll());
    }

    @PostMapping("/surge-rules")
    public ResponseEntity<SurgeRule> saveSurgeRule(@RequestBody SurgeRule rule) {
        return ResponseEntity.ok(surgeRuleRepository.save(rule));
    }

    @PutMapping("/surge-rules/{id}")
    public ResponseEntity<SurgeRule> updateSurgeRule(@PathVariable Long id, @RequestBody SurgeRule updated) {
        return surgeRuleRepository.findById(id).map(s -> {
            updated.setId(id);
            return ResponseEntity.ok(surgeRuleRepository.save(updated));
        }).orElse(ResponseEntity.notFound().build());
    }

    // --- 7. BOOKING MANAGEMENT & DRIVER ASSIGNMENT ---
    @GetMapping("/bookings")
    public ResponseEntity<List<PassengerBooking>> getBookings(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String serviceType,
            @RequestParam(required = false) String vehicleCategory
    ) {
        PassengerBookingStatus bookingStatus = null;
        if (status != null && !status.isBlank()) {
            try {
                bookingStatus = PassengerBookingStatus.valueOf(status.toUpperCase());
            } catch (Exception ignored) {}
        }
        return ResponseEntity.ok(bookingRepository.filterBookings(bookingStatus, serviceType, vehicleCategory));
    }

    @PostMapping("/bookings/{id}/assign-driver")
    public ResponseEntity<PassengerBooking> assignDriver(
            @PathVariable Long id,
            @RequestBody PassengerDriverAssignRequest req
    ) {
        PassengerBooking assigned = bookingService.assignDriver(id, req.getDriverId(), req.getAdminNotes());
        return ResponseEntity.ok(assigned);
    }

    @PostMapping("/bookings/{id}/reassign-driver")
    public ResponseEntity<PassengerBooking> reassignDriver(
            @PathVariable Long id,
            @RequestBody PassengerDriverAssignRequest req
    ) {
        PassengerBooking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found: " + id));
        booking.setStatus(PassengerBookingStatus.DRIVER_SEARCHING);
        bookingRepository.save(booking);
        PassengerBooking reassigned = bookingService.assignDriver(id, req.getDriverId(), req.getAdminNotes());
        return ResponseEntity.ok(reassigned);
    }

    @PostMapping("/bookings/{id}/status")
    public ResponseEntity<PassengerBooking> updateBookingStatus(
            @PathVariable Long id,
            @RequestBody Map<String, String> payload
    ) {
        String statusStr = payload.get("status");
        PassengerBookingStatus nextStatus = PassengerBookingStatus.valueOf(statusStr.toUpperCase());
        PassengerBooking updated = bookingService.updateBookingStatus(id, nextStatus);
        return ResponseEntity.ok(updated);
    }

    // --- 8. ANALYTICS & REPORTS ---
    @GetMapping("/reports")
    public ResponseEntity<Map<String, Object>> getAnalyticsSummary() {
        Map<String, Object> report = new LinkedHashMap<>();
        long totalBookings = bookingRepository.count();
        long completedTrips = bookingRepository.countByStatus(PassengerBookingStatus.TRIP_COMPLETED);
        long activeTrips = bookingRepository.countByStatus(PassengerBookingStatus.TRIP_STARTED)
                + bookingRepository.countByStatus(PassengerBookingStatus.DRIVER_ASSIGNED)
                + bookingRepository.countByStatus(PassengerBookingStatus.DRIVER_ARRIVING);
        long cancelledTrips = bookingRepository.countByStatus(PassengerBookingStatus.CANCELLED_BY_CUSTOMER)
                + bookingRepository.countByStatus(PassengerBookingStatus.CANCELLED_BY_DRIVER)
                + bookingRepository.countByStatus(PassengerBookingStatus.CANCELLED_BY_ADMIN);

        BigDecimal totalRevenue = bookingRepository.calculateTotalRevenue();
        BigDecimal driverEarnings = bookingRepository.calculateTotalDriverEarnings();
        BigDecimal companyCommission = bookingRepository.calculateTotalCompanyCommission();

        report.put("totalBookings", totalBookings);
        report.put("completedTrips", completedTrips);
        report.put("activeTrips", activeTrips);
        report.put("cancelledTrips", cancelledTrips);
        report.put("totalRevenue", totalRevenue);
        report.put("driverEarnings", driverEarnings);
        report.put("companyCommission", companyCommission);
        report.put("activePricingVersion", versionService.getActiveVersion().getVersionNumber());

        return ResponseEntity.ok(report);
    }

    @GetMapping("/reports/export-csv")
    public ResponseEntity<byte[]> exportBookingsCsv() {
        List<PassengerBooking> bookings = bookingRepository.findAllByOrderByCreatedAtDesc();
        StringBuilder csv = new StringBuilder();
        csv.append("BookingNumber,CustomerName,CustomerPhone,Service,Vehicle,DistanceKm,TotalFare,DriverEarnings,Commission,PaymentStatus,Status,CreatedAt\n");

        for (PassengerBooking b : bookings) {
            BigDecimal fare = (b.getFareBreakdown() != null && b.getFareBreakdown().getTotalFare() != null) ? b.getFareBreakdown().getTotalFare() : BigDecimal.ZERO;
            BigDecimal earnings = (b.getFareBreakdown() != null && b.getFareBreakdown().getDriverEarnings() != null) ? b.getFareBreakdown().getDriverEarnings() : BigDecimal.ZERO;
            BigDecimal comm = (b.getFareBreakdown() != null && b.getFareBreakdown().getCompanyCommission() != null) ? b.getFareBreakdown().getCompanyCommission() : BigDecimal.ZERO;

            csv.append(String.format("%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s\n",
                    b.getBookingNumber(),
                    cleanCsv(b.getCustomerName()),
                    cleanCsv(b.getCustomerPhone()),
                    b.getServiceType(),
                    b.getVehicleCategoryCode(),
                    b.getDistanceKm(),
                    fare,
                    earnings,
                    comm,
                    b.getPaymentStatus(),
                    b.getStatus(),
                    b.getCreatedAt()));
        }

        byte[] bytes = csv.toString().getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=passenger_bookings_report.csv")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(bytes);
    }

    private String cleanCsv(String val) {
        if (val == null) return "";
        return val.replace(",", " ").replace("\"", "'");
    }
}
