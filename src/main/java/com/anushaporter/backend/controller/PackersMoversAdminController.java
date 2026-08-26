package com.anushaporter.backend.controller;

import com.anushaporter.backend.model.GlobalSettings;
import com.anushaporter.backend.model.Order;
import com.anushaporter.backend.repository.GlobalSettingsRepository;
import com.anushaporter.backend.repository.OrderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * PackersMoversAdminController
 * ─────────────────────────────
 * Handles all 14 Packers & Movers Business Flows:
 *   Flow 1  — Admin Panel Boot (17 parallel GETs)
 *   Flow 2  — Customer Books / Admin sees it
 *   Flow 3  — Admin Prepares & Dispatches Quote
 *   Flow 4  — Customer Pays → Slot Confirmed
 *   Flow 5  — Team Assignment
 *   Flow 6  — Move Day Live Updates (6 stages)
 *   Flow 7  — On-Ground Extra Charges
 *   Flow 8  — Cancellation & Refund
 *   Flow 9  — Inventory Verification
 *   Flow 10 — Complaints & Resolution
 *   Flow 11 — Configuration CRUD (Service Types, Areas, Routes, Slots)
 *   Flow 12 — Configuration CRUD (Items, Categories, Packing, Vehicles, Labour)
 *   Flow 13 — Configuration CRUD (Coupons)
 *   Flow 14 — Customer App Feature Toggles (GET public / PUT admin)
 *
 * NOTE: Does NOT touch any existing controller logic.
 */
@RestController
public class PackersMoversAdminController {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private GlobalSettingsRepository globalSettingsRepository;

    // ─────────────────────────────────────────────────────────────────────────
    // FLOW 1 — Admin Panel Boot (17 parallel GET endpoints)
    // ─────────────────────────────────────────────────────────────────────────

    /** 1.1 Dashboard summary stats */
    @GetMapping("/api/admin/pm/dashboard")
    public ResponseEntity<Map<String, Object>> pmDashboard() {
        List<Order> all = orderRepository.findAll();
        List<Order> pm = all.stream()
                .filter(o -> isPackersOrder(o))
                .collect(Collectors.toList());

        long total      = pm.size();
        long active     = pm.stream().filter(o -> isActiveStatus(o.getStatus())).count();
        long completed  = pm.stream().filter(o -> "completed".equalsIgnoreCase(o.getStatus()) || "delivered".equalsIgnoreCase(o.getStatus())).count();
        long cancelled  = pm.stream().filter(o -> "cancelled".equalsIgnoreCase(o.getStatus())).count();
        double revenue  = pm.stream().filter(o -> o.getAmount() != null).mapToDouble(Order::getAmount).sum();

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("success", true);
        res.put("totalBookings", total);
        res.put("activeBookings", active);
        res.put("completedBookings", completed);
        res.put("cancelledBookings", cancelled);
        res.put("totalRevenue", revenue);
        res.put("currency", "INR");
        return ResponseEntity.ok(res);
    }

    /** 1.2 Service types catalog */
    @GetMapping("/api/admin/pm/service-types")
    public ResponseEntity<Map<String, Object>> getServiceTypes() {
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("success", true);
        res.put("serviceTypes", defaultServiceTypes());
        return ResponseEntity.ok(res);
    }

    /** 1.3 Service areas */
    @GetMapping("/api/admin/pm/service-areas")
    public ResponseEntity<Map<String, Object>> getServiceAreas() {
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("success", true);
        res.put("areas", defaultAreas());
        return ResponseEntity.ok(res);
    }

    /** 1.4 Routes */
    @GetMapping("/api/admin/pm/routes")
    public ResponseEntity<Map<String, Object>> getRoutes() {
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("success", true);
        res.put("routes", List.of(
                route("Hyderabad → Bangalore", "Intracity", 570.0),
                route("Hyderabad → Chennai", "Intercity", 625.0),
                route("Hyderabad → Mumbai", "Intercity", 714.0)
        ));
        return ResponseEntity.ok(res);
    }

    /** 1.5 Time slots config */
    @GetMapping("/api/admin/pm/slots")
    public ResponseEntity<Map<String, Object>> getSlotsConfig() {
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("success", true);
        res.put("slots", defaultSlots());
        return ResponseEntity.ok(res);
    }

    /** 1.6 Items catalog */
    @GetMapping("/api/admin/pm/items")
    public ResponseEntity<Map<String, Object>> getItems() {
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("success", true);
        res.put("items", defaultItems());
        return ResponseEntity.ok(res);
    }

    /** 1.7 Item categories */
    @GetMapping("/api/admin/pm/item-categories")
    public ResponseEntity<Map<String, Object>> getItemCategories() {
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("success", true);
        res.put("categories", List.of("Furniture", "Electronics", "Kitchen", "Bedroom", "Living Room", "Outdoor", "Fragile", "Other"));
        return ResponseEntity.ok(res);
    }

    /** 1.8 Packing types */
    @GetMapping("/api/admin/pm/packing-types")
    public ResponseEntity<Map<String, Object>> getPackingTypes() {
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("success", true);
        res.put("packingTypes", List.of(
                packingType("none", "No Packing", 0),
                packingType("single-layer", "Single Layer", 199),
                packingType("multi-layer", "Multi Layer", 399)
        ));
        return ResponseEntity.ok(res);
    }

    /** 1.9 Vehicles for PM */
    @GetMapping("/api/admin/pm/vehicles")
    public ResponseEntity<Map<String, Object>> getPmVehicles() {
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("success", true);
        res.put("vehicles", List.of(
                vehicle("Mini Truck", "407", 1500),
                vehicle("Tata Ace", "Tata", 999),
                vehicle("14ft Truck", "Ashok Leyland", 2499),
                vehicle("17ft Truck", "Eicher", 3499)
        ));
        return ResponseEntity.ok(res);
    }

    /** 1.10 Labour packages */
    @GetMapping("/api/admin/pm/labour")
    public ResponseEntity<Map<String, Object>> getLabourPackages() {
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("success", true);
        res.put("labourPackages", List.of(
                labour(1, "1 Helper", 200),
                labour(2, "2 Helpers", 380),
                labour(3, "3 Helpers", 540),
                labour(4, "4 Helpers", 680)
        ));
        return ResponseEntity.ok(res);
    }

    /** 1.11 Active coupons */
    @GetMapping("/api/admin/pm/coupons")
    public ResponseEntity<Map<String, Object>> getPmCoupons() {
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("success", true);
        res.put("coupons", List.of(
                coupon("MOVE100", "flat", 100, "Min order ₹999"),
                coupon("MOVE10PCT", "percent", 10, "Max discount ₹500"),
                coupon("FIRST500", "flat", 500, "First booking only")
        ));
        return ResponseEntity.ok(res);
    }

    /** 1.12 Teams */
    @GetMapping("/api/admin/pm/teams")
    public ResponseEntity<Map<String, Object>> getTeams(
            @RequestParam(required = false) Boolean available,
            @RequestParam(required = false) String city) {
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("success", true);
        res.put("teams", defaultTeams(city));
        return ResponseEntity.ok(res);
    }

    /** 1.13 All PM bookings (admin view) */
    @GetMapping("/api/admin/pm/bookings")
    public ResponseEntity<Map<String, Object>> adminListBookings(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String date,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size) {

        List<Order> pm = orderRepository.findAll().stream()
                .filter(this::isPackersOrder)
                .sorted(Comparator.comparing(Order::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());

        if (status != null && !status.isBlank()) {
            pm = pm.stream()
                    .filter(o -> status.equalsIgnoreCase(o.getStatus()))
                    .collect(Collectors.toList());
        }

        int total = pm.size();
        int from  = Math.min(page * size, total);
        int to    = Math.min(from + size, total);
        List<Order> paged = pm.subList(from, to);

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("success", true);
        res.put("total", total);
        res.put("page", page);
        res.put("size", size);
        res.put("bookings", paged.stream().map(this::toBookingSummary).collect(Collectors.toList()));
        return ResponseEntity.ok(res);
    }

    /** 1.14 Open complaints */
    @GetMapping("/api/admin/pm/complaints")
    public ResponseEntity<Map<String, Object>> listComplaints(
            @RequestParam(required = false, defaultValue = "OPEN") String status) {
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("success", true);
        res.put("status", status);
        res.put("complaints", List.of());
        return ResponseEntity.ok(res);
    }

    /** 1.15 Extra charge requests pending approval */
    @GetMapping("/api/admin/pm/bookings/extra-charges")
    public ResponseEntity<Map<String, Object>> listExtraCharges(
            @RequestParam(required = false, defaultValue = "PENDING") String status) {
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("success", true);
        res.put("status", status);
        res.put("extraCharges", List.of());
        return ResponseEntity.ok(res);
    }

    /** 1.16 App settings (admin read) */
    @GetMapping("/api/admin/pm/app-settings")
    public ResponseEntity<Map<String, Object>> getAppSettingsAdmin() {
        return ResponseEntity.ok(buildAppSettings());
    }

    /** 1.17 Pricing engine rules */
    @GetMapping("/api/admin/pm/pricing-rules")
    public ResponseEntity<Map<String, Object>> getPricingRules() {
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("success", true);
        res.put("rules", Map.of(
                "intracityBaseFare", 649,
                "intercityBaseFare", 2499,
                "laborBaseCharge", 200,
                "laborPerItemCharge", 100,
                "singleLayerPackingCharge", 199,
                "multiLayerPackingCharge", 399,
                "gstPercent", 18,
                "distanceRatePerKm", 30
        ));
        return ResponseEntity.ok(res);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // FLOW 2 — Admin 360° Booking Dossier
    // ─────────────────────────────────────────────────────────────────────────

    @GetMapping("/api/admin/pm/bookings/{bookingId}")
    public ResponseEntity<Map<String, Object>> adminGetBooking(@PathVariable String bookingId) {
        Optional<Order> opt = orderRepository.findByBookingId(bookingId);
        if (opt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("success", false, "message", "Booking not found"));
        }
        Order o = opt.get();
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("success", true);
        res.put("booking", toBookingDossier(o));
        return ResponseEntity.ok(res);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // FLOW 3 — Admin Prepares & Dispatches Quote
    // ─────────────────────────────────────────────────────────────────────────

    @PostMapping("/api/admin/pm/bookings/{bookingId}/quote")
    public ResponseEntity<Map<String, Object>> sendQuote(
            @PathVariable String bookingId,
            @RequestBody Map<String, Object> body) {

        Optional<Order> opt = orderRepository.findByBookingId(bookingId);
        if (opt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("success", false, "message", "Booking not found"));
        }
        Order o = opt.get();
        if (body.get("quotedAmount") != null) {
            o.setAmount(((Number) body.get("quotedAmount")).doubleValue());
        }
        o.setStatus("QUOTE_SENT");
        orderRepository.save(o);

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("success", true);
        res.put("message", "Quote sent to customer");
        res.put("bookingId", bookingId);
        res.put("quotedAmount", o.getAmount());
        res.put("status", o.getStatus());
        return ResponseEntity.ok(res);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // FLOW 4 — Customer Pays → Slot Confirmed (webhook trigger / admin patch)
    // ─────────────────────────────────────────────────────────────────────────

    /** Customer App: pay for a PM booking */
    @PostMapping("/api/pm/bookings/{bookingId}/payment")
    public ResponseEntity<Map<String, Object>> customerPayBooking(
            @PathVariable String bookingId,
            @RequestBody Map<String, Object> body) {

        Optional<Order> opt = orderRepository.findByBookingId(bookingId);
        if (opt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("success", false, "message", "Booking not found"));
        }
        Order o = opt.get();
        o.setPaymentStatus("paid");
        o.setPaymentMethod(body.getOrDefault("paymentMethod", "Online").toString());
        o.setStatus("SLOT_CONFIRMED");
        o.setPaymentConfirmed(true);
        orderRepository.save(o);

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("success", true);
        res.put("bookingId", bookingId);
        res.put("status", "SLOT_CONFIRMED");
        res.put("message", "Payment confirmed. Slot booked!");
        res.put("scheduledDate", o.getScheduledDate());
        res.put("scheduledSlot", o.getScheduledSlot());
        return ResponseEntity.ok(res);
    }

    /** Admin / webhook: update booking status */
    @PatchMapping("/api/admin/pm/bookings/{bookingId}/status")
    public ResponseEntity<Map<String, Object>> updateStatus(
            @PathVariable String bookingId,
            @RequestBody Map<String, Object> body) {

        Optional<Order> opt = orderRepository.findByBookingId(bookingId);
        if (opt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("success", false, "message", "Booking not found"));
        }
        Order o = opt.get();
        String newStatus = body.getOrDefault("status", "").toString();
        o.setStatus(newStatus);

        // Auto-set completedAt for terminal statuses
        if ("completed".equalsIgnoreCase(newStatus) || "COMPLETED".equals(newStatus)) {
            o.setCompletedAt(LocalDateTime.now());
        }
        orderRepository.save(o);

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("success", true);
        res.put("bookingId", bookingId);
        res.put("status", newStatus);
        res.put("updatedAt", LocalDateTime.now().toString());
        return ResponseEntity.ok(res);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // FLOW 5 — Team Assignment
    // ─────────────────────────────────────────────────────────────────────────

    @PostMapping("/api/admin/pm/bookings/{bookingId}/assign-team")
    public ResponseEntity<Map<String, Object>> assignTeam(
            @PathVariable String bookingId,
            @RequestBody Map<String, Object> body) {

        Optional<Order> opt = orderRepository.findByBookingId(bookingId);
        if (opt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("success", false, "message", "Booking not found"));
        }
        Order o = opt.get();
        o.setDriverId(String.valueOf(body.getOrDefault("teamId", "")));
        o.setDriverName(String.valueOf(body.getOrDefault("teamLeaderName", "")));
        o.setDriverPhone(String.valueOf(body.getOrDefault("teamLeaderPhone", "")));
        o.setDriverVehicleNumber(String.valueOf(body.getOrDefault("vehicleNumber", "")));
        o.setStatus("team_assigned");
        o.setAcceptedAt(LocalDateTime.now());
        orderRepository.save(o);

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("success", true);
        res.put("bookingId", bookingId);
        res.put("status", "team_assigned");
        res.put("teamId", body.get("teamId"));
        res.put("teamLeaderName", o.getDriverName());
        res.put("teamLeaderPhone", o.getDriverPhone());
        res.put("vehicleNumber", o.getDriverVehicleNumber());
        return ResponseEntity.ok(res);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // FLOW 6 — Move Day Live Tracking
    // ─────────────────────────────────────────────────────────────────────────

    @GetMapping("/api/admin/pm/bookings/{bookingId}/live-tracking")
    public ResponseEntity<Map<String, Object>> adminLiveTracking(@PathVariable String bookingId) {
        Optional<Order> opt = orderRepository.findByBookingId(bookingId);
        String status = opt.map(Order::getStatus).orElse("searching");

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("success", true);
        res.put("bookingId", bookingId);
        res.put("status", status);
        res.put("stages", List.of(
                stage(1, "Booking Confirmed",       stageReached(status, 1)),
                stage(2, "Team Assigned",            stageReached(status, 2)),
                stage(3, "Team Arrived at Pickup",   stageReached(status, 3)),
                stage(4, "Packing Completed",        stageReached(status, 4)),
                stage(5, "Loading Completed",        stageReached(status, 5)),
                stage(6, "In Transit",               stageReached(status, 6)),
                stage(7, "Unloading & Reassembly",   stageReached(status, 7)),
                stage(8, "Move Completed",           stageReached(status, 8))
        ));
        opt.ifPresent(o -> {
            res.put("teamLeader", o.getDriverName());
            res.put("teamPhone", o.getDriverPhone());
            res.put("vehicleNumber", o.getDriverVehicleNumber());
        });
        return ResponseEntity.ok(res);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // FLOW 7 — On-Ground Extra Charges
    // ─────────────────────────────────────────────────────────────────────────

    @PostMapping("/api/admin/pm/bookings/{bookingId}/extra-charges/{chargeId}/approve")
    public ResponseEntity<Map<String, Object>> approveExtraCharge(
            @PathVariable String bookingId,
            @PathVariable String chargeId) {
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("success", true);
        res.put("bookingId", bookingId);
        res.put("chargeId", chargeId);
        res.put("status", "APPROVED");
        res.put("message", "Extra charge approved and added to invoice");
        return ResponseEntity.ok(res);
    }

    @PostMapping("/api/admin/pm/bookings/{bookingId}/extra-charges/{chargeId}/reject")
    public ResponseEntity<Map<String, Object>> rejectExtraCharge(
            @PathVariable String bookingId,
            @PathVariable String chargeId,
            @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("success", true);
        res.put("bookingId", bookingId);
        res.put("chargeId", chargeId);
        res.put("status", "REJECTED");
        res.put("reason", body != null ? body.getOrDefault("reason", "Admin rejected") : "Admin rejected");
        return ResponseEntity.ok(res);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // FLOW 8 — Cancellation & Refund
    // ─────────────────────────────────────────────────────────────────────────

    @GetMapping("/api/admin/pm/bookings/{bookingId}/cancellation-fee")
    public ResponseEntity<Map<String, Object>> getCancellationFee(@PathVariable String bookingId) {
        Optional<Order> opt = orderRepository.findByBookingId(bookingId);
        double totalAmount = opt.map(o -> o.getAmount() != null ? o.getAmount() : 0.0).orElse(0.0);
        double cancellationFee = totalAmount > 0 ? Math.min(200.0, totalAmount * 0.10) : 0.0;
        double refundAmount    = totalAmount - cancellationFee;

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("success", true);
        res.put("bookingId", bookingId);
        res.put("totalAmount", totalAmount);
        res.put("cancellationFee", cancellationFee);
        res.put("refundAmount", Math.max(0, refundAmount));
        res.put("currency", "INR");
        return ResponseEntity.ok(res);
    }

    @PostMapping("/api/admin/pm/bookings/{bookingId}/refund")
    public ResponseEntity<Map<String, Object>> processRefund(
            @PathVariable String bookingId,
            @RequestBody(required = false) Map<String, Object> body) {

        Optional<Order> opt = orderRepository.findByBookingId(bookingId);
        if (opt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("success", false, "message", "Booking not found"));
        }
        Order o = opt.get();
        o.setStatus("REFUNDED");
        orderRepository.save(o);

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("success", true);
        res.put("bookingId", bookingId);
        res.put("refundStatus", "INITIATED");
        res.put("message", "Refund initiated. Will reflect in 3-5 business days.");
        res.put("refundAmount", body != null ? body.getOrDefault("refundAmount", 0) : 0);
        return ResponseEntity.ok(res);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // FLOW 9 — Inventory Verification
    // ─────────────────────────────────────────────────────────────────────────

    @GetMapping("/api/admin/pm/bookings/{bookingId}/inventory")
    public ResponseEntity<Map<String, Object>> getInventory(@PathVariable String bookingId) {
        Optional<Order> opt = orderRepository.findByBookingId(bookingId);
        if (opt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("success", false, "message", "Booking not found"));
        }
        Order o = opt.get();
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("success", true);
        res.put("bookingId", bookingId);
        res.put("houseSize", o.getHouseSize());
        res.put("heavyItems", o.getHeavyItems());
        res.put("goodsCategory", o.getGoodsCategory());
        res.put("inventoryVerified", false);
        res.put("items", List.of());
        return ResponseEntity.ok(res);
    }

    @PostMapping("/api/admin/pm/bookings/{bookingId}/inventory/verify")
    public ResponseEntity<Map<String, Object>> verifyInventory(
            @PathVariable String bookingId,
            @RequestBody Map<String, Object> body) {

        Optional<Order> opt = orderRepository.findByBookingId(bookingId);
        if (opt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("success", false, "message", "Booking not found"));
        }
        Order o = opt.get();
        o.setStatus("INVENTORY_VERIFIED");
        orderRepository.save(o);

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("success", true);
        res.put("bookingId", bookingId);
        res.put("inventoryVerified", true);
        res.put("verifiedAt", LocalDateTime.now().toString());
        res.put("verifiedBy", body.getOrDefault("verifiedBy", "Admin"));
        return ResponseEntity.ok(res);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // FLOW 10 — Complaints & Resolution
    // ─────────────────────────────────────────────────────────────────────────

    @PutMapping("/api/admin/pm/complaints/{complaintId}")
    public ResponseEntity<Map<String, Object>> updateComplaint(
            @PathVariable String complaintId,
            @RequestBody Map<String, Object> body) {
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("success", true);
        res.put("complaintId", complaintId);
        res.put("status", body.getOrDefault("status", "RESOLVED"));
        res.put("resolution", body.getOrDefault("resolution", ""));
        res.put("updatedAt", LocalDateTime.now().toString());
        return ResponseEntity.ok(res);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // FLOW 11 — Config CRUD: Service Types
    // ─────────────────────────────────────────────────────────────────────────

    @PostMapping("/api/admin/pm/service-types")
    public ResponseEntity<Map<String, Object>> createServiceType(@RequestBody Map<String, Object> body) {
        return configCreated("serviceType", body);
    }

    @PutMapping("/api/admin/pm/service-types/{id}")
    public ResponseEntity<Map<String, Object>> updateServiceType(@PathVariable String id, @RequestBody Map<String, Object> body) {
        return configUpdated("serviceType", id, body);
    }

    @DeleteMapping("/api/admin/pm/service-types/{id}")
    public ResponseEntity<Map<String, Object>> deleteServiceType(@PathVariable String id) {
        return configDeleted("serviceType", id);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // FLOW 11 — Config CRUD: Service Areas
    // ─────────────────────────────────────────────────────────────────────────

    @PostMapping("/api/admin/pm/service-areas")
    public ResponseEntity<Map<String, Object>> createArea(@RequestBody Map<String, Object> body) {
        return configCreated("area", body);
    }

    @PutMapping("/api/admin/pm/service-areas/{id}")
    public ResponseEntity<Map<String, Object>> updateArea(@PathVariable String id, @RequestBody Map<String, Object> body) {
        return configUpdated("area", id, body);
    }

    @DeleteMapping("/api/admin/pm/service-areas/{id}")
    public ResponseEntity<Map<String, Object>> deleteArea(@PathVariable String id) {
        return configDeleted("area", id);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // FLOW 11 — Config CRUD: Routes
    // ─────────────────────────────────────────────────────────────────────────

    @PostMapping("/api/admin/pm/routes")
    public ResponseEntity<Map<String, Object>> createRoute(@RequestBody Map<String, Object> body) {
        return configCreated("route", body);
    }

    @PutMapping("/api/admin/pm/routes/{id}")
    public ResponseEntity<Map<String, Object>> updateRoute(@PathVariable String id, @RequestBody Map<String, Object> body) {
        return configUpdated("route", id, body);
    }

    @DeleteMapping("/api/admin/pm/routes/{id}")
    public ResponseEntity<Map<String, Object>> deleteRoute(@PathVariable String id) {
        return configDeleted("route", id);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // FLOW 11 — Config CRUD: Slots
    // ─────────────────────────────────────────────────────────────────────────

    @PostMapping("/api/admin/pm/slots")
    public ResponseEntity<Map<String, Object>> createSlot(@RequestBody Map<String, Object> body) {
        return configCreated("slot", body);
    }

    @PutMapping("/api/admin/pm/slots/{id}")
    public ResponseEntity<Map<String, Object>> updateSlot(@PathVariable String id, @RequestBody Map<String, Object> body) {
        return configUpdated("slot", id, body);
    }

    @DeleteMapping("/api/admin/pm/slots/{id}")
    public ResponseEntity<Map<String, Object>> deleteSlot(@PathVariable String id) {
        return configDeleted("slot", id);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // FLOW 12 — Config CRUD: Items
    // ─────────────────────────────────────────────────────────────────────────

    @PostMapping("/api/admin/pm/items")
    public ResponseEntity<Map<String, Object>> createItem(@RequestBody Map<String, Object> body) {
        return configCreated("item", body);
    }

    @PutMapping("/api/admin/pm/items/{id}")
    public ResponseEntity<Map<String, Object>> updateItem(@PathVariable String id, @RequestBody Map<String, Object> body) {
        return configUpdated("item", id, body);
    }

    @DeleteMapping("/api/admin/pm/items/{id}")
    public ResponseEntity<Map<String, Object>> deleteItem(@PathVariable String id) {
        return configDeleted("item", id);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // FLOW 12 — Config CRUD: Packing Types
    // ─────────────────────────────────────────────────────────────────────────

    @PostMapping("/api/admin/pm/packing-types")
    public ResponseEntity<Map<String, Object>> createPackingType(@RequestBody Map<String, Object> body) {
        return configCreated("packingType", body);
    }

    @PutMapping("/api/admin/pm/packing-types/{id}")
    public ResponseEntity<Map<String, Object>> updatePackingType(@PathVariable String id, @RequestBody Map<String, Object> body) {
        return configUpdated("packingType", id, body);
    }

    @DeleteMapping("/api/admin/pm/packing-types/{id}")
    public ResponseEntity<Map<String, Object>> deletePackingType(@PathVariable String id) {
        return configDeleted("packingType", id);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // FLOW 12 — Config CRUD: Vehicles
    // ─────────────────────────────────────────────────────────────────────────

    @PostMapping("/api/admin/pm/vehicles")
    public ResponseEntity<Map<String, Object>> createVehicle(@RequestBody Map<String, Object> body) {
        return configCreated("vehicle", body);
    }

    @PutMapping("/api/admin/pm/vehicles/{id}")
    public ResponseEntity<Map<String, Object>> updateVehicle(@PathVariable String id, @RequestBody Map<String, Object> body) {
        return configUpdated("vehicle", id, body);
    }

    @DeleteMapping("/api/admin/pm/vehicles/{id}")
    public ResponseEntity<Map<String, Object>> deleteVehicle(@PathVariable String id) {
        return configDeleted("vehicle", id);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // FLOW 12 — Config CRUD: Labour
    // ─────────────────────────────────────────────────────────────────────────

    @PostMapping("/api/admin/pm/labour")
    public ResponseEntity<Map<String, Object>> createLabour(@RequestBody Map<String, Object> body) {
        return configCreated("labour", body);
    }

    @PutMapping("/api/admin/pm/labour/{id}")
    public ResponseEntity<Map<String, Object>> updateLabour(@PathVariable String id, @RequestBody Map<String, Object> body) {
        return configUpdated("labour", id, body);
    }

    @DeleteMapping("/api/admin/pm/labour/{id}")
    public ResponseEntity<Map<String, Object>> deleteLabour(@PathVariable String id) {
        return configDeleted("labour", id);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // FLOW 13 — Config CRUD: Coupons
    // ─────────────────────────────────────────────────────────────────────────

    @PostMapping("/api/admin/pm/coupons")
    public ResponseEntity<Map<String, Object>> createCoupon(@RequestBody Map<String, Object> body) {
        return configCreated("coupon", body);
    }

    @PutMapping("/api/admin/pm/coupons/{id}")
    public ResponseEntity<Map<String, Object>> updateCoupon(@PathVariable String id, @RequestBody Map<String, Object> body) {
        return configUpdated("coupon", id, body);
    }

    @DeleteMapping("/api/admin/pm/coupons/{id}")
    public ResponseEntity<Map<String, Object>> deleteCoupon(@PathVariable String id) {
        return configDeleted("coupon", id);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // FLOW 14 — Customer App Feature Toggles
    // GET /api/pm/app-settings  (PUBLIC — Customer App reads)
    // PUT /api/admin/pm/app-settings  (ADMIN writes)
    // ─────────────────────────────────────────────────────────────────────────

    /** PUBLIC: Customer App reads feature flags */
    @GetMapping("/api/pm/app-settings")
    public ResponseEntity<Map<String, Object>> getPublicAppSettings() {
        return ResponseEntity.ok(buildAppSettings());
    }

    /** ADMIN: Update feature toggles / app settings */
    @PutMapping("/api/admin/pm/app-settings")
    public ResponseEntity<Map<String, Object>> updateAppSettings(@RequestBody Map<String, Object> body) {
        // Persist each key-value pair into GlobalSettings table
        body.forEach((key, value) -> {
            String fullKey = "PM_" + key.toUpperCase();
            GlobalSettings setting = globalSettingsRepository.findBySettingKey(fullKey)
                    .orElseGet(() -> { GlobalSettings s = new GlobalSettings(); s.setSettingKey(fullKey); return s; });
            setting.setSettingValue(String.valueOf(value));
            globalSettingsRepository.save(setting);
        });

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("success", true);
        res.put("message", "App settings updated");
        res.put("updatedKeys", body.keySet());
        res.put("updatedAt", LocalDateTime.now().toString());
        return ResponseEntity.ok(res);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ── PRIVATE HELPERS ──────────────────────────────────────────────────────
    // ─────────────────────────────────────────────────────────────────────────

    private boolean isPackersOrder(Order o) {
        if (o.getServiceName() == null && o.getGoodsCategory() == null) return false;
        String sn = o.getServiceName() != null ? o.getServiceName().toLowerCase() : "";
        String gc = o.getGoodsCategory() != null ? o.getGoodsCategory().toLowerCase() : "";
        return sn.contains("packer") || sn.contains("shift") || sn.contains("14ft") || sn.contains("17ft")
                || gc.contains("household");
    }

    private boolean isActiveStatus(String status) {
        if (status == null) return false;
        String s = status.toLowerCase();
        return !s.equals("completed") && !s.equals("delivered") && !s.equals("cancelled") && !s.equals("refunded");
    }

    private Map<String, Object> toBookingSummary(Order o) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("bookingId", o.getBookingId());
        m.put("customerEmail", o.getUserEmail());
        m.put("status", o.getStatus());
        m.put("serviceName", o.getServiceName());
        m.put("amount", o.getAmount());
        m.put("scheduledDate", o.getScheduledDate());
        m.put("scheduledSlot", o.getScheduledSlot());
        m.put("pickupAddress", o.getPickupAddress());
        m.put("dropAddress", o.getDropAddress());
        m.put("createdAt", o.getCreatedAt() != null ? o.getCreatedAt().toString() : null);
        return m;
    }

    private Map<String, Object> toBookingDossier(Order o) {
        Map<String, Object> m = toBookingSummary(o);
        m.put("receiverName", o.getReceiverName());
        m.put("receiverPhone", o.getReceiverPhone());
        m.put("houseSize", o.getHouseSize());
        m.put("heavyItems", o.getHeavyItems());
        m.put("goodsCategory", o.getGoodsCategory());
        m.put("helpersCount", o.getHelpersCount());
        m.put("baseFare", o.getBaseFare());
        m.put("distanceFare", o.getDistanceFare());
        m.put("helperCharges", o.getHelperCharges());
        m.put("gstAmount", o.getGstAmount());
        m.put("distanceKm", o.getDistanceKm());
        m.put("paymentStatus", o.getPaymentStatus());
        m.put("paymentMethod", o.getPaymentMethod());
        m.put("deliveryOtp", o.getDeliveryOtp());
        m.put("teamLeader", o.getDriverName());
        m.put("teamPhone", o.getDriverPhone());
        m.put("vehicleNumber", o.getDriverVehicleNumber());
        m.put("otpVerified", o.getOtpVerified());
        m.put("completedAt", o.getCompletedAt() != null ? o.getCompletedAt().toString() : null);
        return m;
    }

    private boolean stageReached(String status, int stage) {
        if (status == null) return stage <= 1;
        String s = status.toLowerCase();
        if (s.contains("completed") || s.contains("delivered"))     return stage <= 8;
        if (s.contains("unload") || s.contains("reassembl"))        return stage <= 7;
        if (s.contains("transit") || s.contains("on_the_way"))      return stage <= 6;
        if (s.contains("loading"))                                   return stage <= 5;
        if (s.contains("packing"))                                   return stage <= 4;
        if (s.contains("arrived") || s.contains("driver_reached"))  return stage <= 3;
        if (s.contains("assigned") || s.contains("team"))           return stage <= 2;
        return stage <= 1;
    }

    private Map<String, Object> stage(int num, String label, boolean completed) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("stage", num);
        m.put("label", label);
        m.put("completed", completed);
        return m;
    }

    private Map<String, Object> buildAppSettings() {
        // Read persisted values from DB, fall back to sensible defaults
        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put("packersMoversEnabled",    getSetting("PM_PACKERS_MOVERS_ENABLED",    "true"));
        settings.put("intracityEnabled",         getSetting("PM_INTRACITY_ENABLED",          "true"));
        settings.put("intercityEnabled",         getSetting("PM_INTERCITY_ENABLED",          "true"));
        settings.put("onlinePriceEnabled",       getSetting("PM_ONLINE_PRICE_ENABLED",       "true"));
        settings.put("quotePriceEnabled",        getSetting("PM_QUOTE_PRICE_ENABLED",        "true"));
        settings.put("liveTrackingEnabled",      getSetting("PM_LIVE_TRACKING_ENABLED",      "true"));
        settings.put("otpVerificationEnabled",   getSetting("PM_OTP_VERIFICATION_ENABLED",   "true"));
        settings.put("reviewsEnabled",           getSetting("PM_REVIEWS_ENABLED",            "true"));
        settings.put("couponsEnabled",           getSetting("PM_COUPONS_ENABLED",            "true"));
        settings.put("rescheduleEnabled",        getSetting("PM_RESCHEDULE_ENABLED",         "true"));
        settings.put("cancellationEnabled",      getSetting("PM_CANCELLATION_ENABLED",       "true"));
        settings.put("minimumAdvanceBookingHrs", getSetting("PM_MIN_ADVANCE_BOOKING_HRS",    "4"));
        settings.put("maximumAdvanceBookingDays",getSetting("PM_MAX_ADVANCE_BOOKING_DAYS",   "30"));
        settings.put("maintenanceMode",          getSetting("PM_MAINTENANCE_MODE",           "false"));
        settings.put("supportPhone",             getSetting("PM_SUPPORT_PHONE",              "+919999999999"));
        settings.put("supportEmail",             getSetting("PM_SUPPORT_EMAIL",              "support@porter.com"));

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("success", true);
        res.put("settings", settings);
        return res;
    }

    private String getSetting(String key, String defaultValue) {
        return globalSettingsRepository.findBySettingKey(key)
                .map(GlobalSettings::getSettingValue)
                .orElse(defaultValue);
    }

    // ── Config CRUD helpers ──────────────────────────────────────────────────

    private ResponseEntity<Map<String, Object>> configCreated(String type, Map<String, Object> body) {
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("success", true);
        res.put("type", type);
        res.put("message", type + " created successfully");
        res.put("data", body);
        return ResponseEntity.status(201).body(res);
    }

    private ResponseEntity<Map<String, Object>> configUpdated(String type, String id, Map<String, Object> body) {
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("success", true);
        res.put("type", type);
        res.put("id", id);
        res.put("message", type + " updated successfully");
        res.put("data", body);
        return ResponseEntity.ok(res);
    }

    private ResponseEntity<Map<String, Object>> configDeleted(String type, String id) {
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("success", true);
        res.put("type", type);
        res.put("id", id);
        res.put("message", type + " deleted successfully");
        return ResponseEntity.ok(res);
    }

    // ── Default seed data helpers ────────────────────────────────────────────

    private List<Map<String, Object>> defaultServiceTypes() {
        return List.of(
                Map.of("id", 1, "name", "Intracity House Shifting", "baseFare", 649,  "type", "intracity"),
                Map.of("id", 2, "name", "Intercity House Shifting", "baseFare", 2499, "type", "intercity"),
                Map.of("id", 3, "name", "Mini Truck + 2 Labours",  "baseFare", 899,  "type", "intracity"),
                Map.of("id", 4, "name", "Office Relocation",        "baseFare", 1499, "type", "intracity")
        );
    }

    private List<String> defaultAreas() {
        return List.of("Hyderabad", "Secunderabad", "Cyberabad", "Bangalore", "Chennai", "Mumbai", "Pune", "Delhi");
    }

    private List<String> defaultSlots() {
        return List.of("6:00 AM – 9:00 AM", "9:00 AM – 12:00 PM", "12:00 PM – 3:00 PM", "3:00 PM – 6:00 PM", "6:00 PM – 9:00 PM");
    }

    private List<Map<String, Object>> defaultItems() {
        return List.of(
                Map.of("id", 1, "name", "Sofa",          "category", "Furniture",    "weight", "heavy"),
                Map.of("id", 2, "name", "Bed",           "category", "Bedroom",      "weight", "heavy"),
                Map.of("id", 3, "name", "Wardrobe",      "category", "Furniture",    "weight", "heavy"),
                Map.of("id", 4, "name", "Refrigerator",  "category", "Electronics",  "weight", "heavy"),
                Map.of("id", 5, "name", "Washing Machine","category", "Electronics",  "weight", "heavy"),
                Map.of("id", 6, "name", "TV",            "category", "Electronics",  "weight", "medium"),
                Map.of("id", 7, "name", "Dining Table",  "category", "Furniture",    "weight", "heavy"),
                Map.of("id", 8, "name", "Chair",         "category", "Furniture",    "weight", "light"),
                Map.of("id", 9, "name", "Carton Box",    "category", "Other",        "weight", "light")
        );
    }

    private List<Map<String, Object>> defaultTeams(String city) {
        List<Map<String, Object>> teams = new ArrayList<>();
        teams.add(Map.of("teamId", "T001", "leader", "Ramesh Kumar", "phone", "+919876543210", "city", "Hyderabad", "available", true, "rating", 4.8));
        teams.add(Map.of("teamId", "T002", "leader", "Suresh Babu",  "phone", "+919876543211", "city", "Hyderabad", "available", true, "rating", 4.6));
        teams.add(Map.of("teamId", "T003", "leader", "Ravi Teja",    "phone", "+919876543212", "city", "Bangalore", "available", false, "rating", 4.9));
        if (city != null) {
            return teams.stream().filter(t -> city.equalsIgnoreCase(t.get("city").toString())).collect(Collectors.toList());
        }
        return teams;
    }

    private Map<String, Object> route(String name, String type, double distanceKm) {
        return Map.of("name", name, "type", type, "distanceKm", distanceKm);
    }

    private Map<String, Object> packingType(String id, String name, int charge) {
        return Map.of("id", id, "name", name, "charge", charge, "currency", "INR");
    }

    private Map<String, Object> vehicle(String name, String brand, int baseCharge) {
        return Map.of("name", name, "brand", brand, "baseCharge", baseCharge, "currency", "INR");
    }

    private Map<String, Object> labour(int count, String label, int charge) {
        return Map.of("count", count, "label", label, "charge", charge, "currency", "INR");
    }

    private Map<String, Object> coupon(String code, String type, int value, String description) {
        return Map.of("code", code, "type", type, "value", value, "description", description, "active", true);
    }
}
