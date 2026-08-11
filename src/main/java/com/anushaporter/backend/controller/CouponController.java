package com.anushaporter.backend.controller;

import com.anushaporter.backend.model.Coupon;
import com.anushaporter.backend.repository.CouponRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Coupon & Promo Code Validation API
 *
 * POST /api/coupons/validate  - Validates promo code and returns discount + final payable price
 * GET  /api/coupons           - Fetches list of active promo codes for frontend
 */
@RestController
@RequestMapping("/api/coupons")
public class CouponController {

    @Autowired
    private CouponRepository repository;

    @PostConstruct
    public void seedCoupons() {
        if (repository.findByCode("SAVE10").isEmpty()) {
            Coupon c = new Coupon();
            c.setCode("SAVE10");
            c.setDescription("Get 10% off up to ₹250 on all rides");
            c.setDiscountPercentage(10.0);
            c.setMaxDiscount(250.0);
            c.setMinOrderAmount(100.0);
            c.setActive(true);
            repository.save(c);
        }
        if (repository.findByCode("FIRST50").isEmpty()) {
            Coupon c = new Coupon();
            c.setCode("FIRST50");
            c.setDescription("50% off up to ₹100 on your first booking");
            c.setDiscountPercentage(50.0);
            c.setMaxDiscount(100.0);
            c.setMinOrderAmount(50.0);
            c.setActive(true);
            repository.save(c);
        }
        if (repository.findByCode("WELCOME20").isEmpty()) {
            Coupon c = new Coupon();
            c.setCode("WELCOME20");
            c.setDescription("Get 20% off up to ₹150");
            c.setDiscountPercentage(20.0);
            c.setMaxDiscount(150.0);
            c.setMinOrderAmount(150.0);
            c.setActive(true);
            repository.save(c);
        }
        if (repository.findByCode("PORTER100").isEmpty()) {
            Coupon c = new Coupon();
            c.setCode("PORTER100");
            c.setDescription("Flat ₹100 off on mini truck & full truck bookings");
            c.setFlatDiscount(100.0);
            c.setMinOrderAmount(300.0);
            c.setActive(true);
            repository.save(c);
        }
    }

    /**
     * GET /api/coupons
     * List all active coupons for the checkout screen.
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getActiveCoupons() {
        List<Coupon> activeCoupons = repository.findAll().stream()
                .filter(c -> Boolean.TRUE.equals(c.getActive()))
                .collect(Collectors.toList());

        List<Map<String, Object>> items = activeCoupons.stream().map(c -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", c.getId());
            item.put("code", c.getCode());
            item.put("description", c.getDescription() != null ? c.getDescription() : "");
            item.put("discountPercentage", c.getDiscountPercentage());
            item.put("flatDiscount", c.getFlatDiscount());
            item.put("maxDiscount", c.getMaxDiscount());
            item.put("minOrderAmount", c.getMinOrderAmount());
            return item;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(Map.of(
                "success", true,
                "coupons", items
        ));
    }

    /**
     * POST /api/coupons/validate
     * Validates promo code and returns discount amount + final payable price.
     *
     * Request body:
     * {
     *   "couponCode": "SAVE10", // or "code"
     *   "amount": 500.0         // original fare amount
     * }
     */
    @PostMapping("/validate")
    public ResponseEntity<Map<String, Object>> validateCoupon(@RequestBody Map<String, Object> payload) {
        String code = (String) payload.getOrDefault("couponCode", payload.get("code"));
        if (code == null || code.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "applied", false,
                    "message", "Coupon code is required.",
                    "discount", 0.0,
                    "finalAmount", extractAmount(payload)
            ));
        }

        double originalAmount = extractAmount(payload);
        String cleanCode = code.trim().toUpperCase();

        Optional<Coupon> opt = repository.findByCode(cleanCode);

        if (opt.isEmpty() || !Boolean.TRUE.equals(opt.get().getActive())) {
            return ResponseEntity.ok(Map.of(
                    "success", false,
                    "applied", false,
                    "code", cleanCode,
                    "message", "Invalid or expired promo code.",
                    "discount", 0.0,
                    "originalAmount", originalAmount,
                    "finalAmount", originalAmount
            ));
        }

        Coupon coupon = opt.get();

        // Check minimum order amount requirement
        if (coupon.getMinOrderAmount() != null && originalAmount < coupon.getMinOrderAmount()) {
            return ResponseEntity.ok(Map.of(
                    "success", false,
                    "applied", false,
                    "code", cleanCode,
                    "message", "Minimum order amount of ₹" + coupon.getMinOrderAmount().intValue() + " required to use this coupon.",
                    "discount", 0.0,
                    "originalAmount", originalAmount,
                    "finalAmount", originalAmount
            ));
        }

        // Calculate discount
        double discount = 0.0;

        if (coupon.getFlatDiscount() != null && coupon.getFlatDiscount() > 0) {
            discount = coupon.getFlatDiscount();
        } else if (coupon.getDiscountPercentage() != null && coupon.getDiscountPercentage() > 0) {
            discount = originalAmount * (coupon.getDiscountPercentage() / 100.0);
            if (coupon.getMaxDiscount() != null && discount > coupon.getMaxDiscount()) {
                discount = coupon.getMaxDiscount();
            }
        }

        // Ensure discount doesn't exceed total amount
        discount = Math.min(discount, originalAmount);
        discount = Math.round(discount * 100.0) / 100.0;

        double finalAmount = Math.round((originalAmount - discount) * 100.0) / 100.0;

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("applied", true);
        response.put("code", cleanCode);
        response.put("description", coupon.getDescription() != null ? coupon.getDescription() : "");
        response.put("message", "Coupon '" + cleanCode + "' applied successfully!");
        response.put("discount", discount);
        response.put("originalAmount", originalAmount);
        response.put("finalAmount", finalAmount);
        response.put("currency", "INR");

        return ResponseEntity.ok(response);
    }

    private double extractAmount(Map<String, Object> payload) {
        Object val = payload.get("amount");
        if (val == null) val = payload.get("totalAmount");
        if (val == null) val = payload.get("fare");
        if (val instanceof Number n) {
            return n.doubleValue();
        }
        if (val instanceof String s) {
            try {
                return Double.parseDouble(s);
            } catch (Exception ignored) {}
        }
        return 0.0;
    }
}
