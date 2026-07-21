package com.anushaporter.backend.controller;

import com.anushaporter.backend.model.Coupon;
import com.anushaporter.backend.repository.CouponRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

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
            c.setDiscountPercentage(10.0);
            c.setMaxDiscount(250.0);
            c.setActive(true);
            repository.save(c);
        }
    }

    @PostMapping("/validate")
    public ResponseEntity<Map<String, Object>> validateCoupon(@RequestBody Map<String, Object> payload) {
        String code = (String) payload.getOrDefault("couponCode", "");
        Number amountObj = (Number) payload.getOrDefault("amount", 0);
        double amount = amountObj != null ? amountObj.doubleValue() : 0.0;

        Optional<Coupon> opt = repository.findByCode(code.toUpperCase());
        if (opt.isPresent() && opt.get().getActive()) {
            Coupon coupon = opt.get();
            double discount = amount * (coupon.getDiscountPercentage() / 100.0);
            if (discount > coupon.getMaxDiscount()) {
                discount = coupon.getMaxDiscount();
            }
            
            return ResponseEntity.ok(Map.of(
                "code", code.toUpperCase(),
                "applied", true,
                "message", "Coupon applied successfully!",
                "discount", discount
            ));
        }

        return ResponseEntity.ok(Map.of(
            "code", code,
            "applied", false,
            "message", "Coupon not applicable at this time.",
            "discount", 0
        ));
    }
}
