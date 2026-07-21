package com.anushaporter.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    @PostMapping("/initiate")
    public ResponseEntity<Map<String, Object>> initiatePayment(@RequestBody Map<String, Object> payload) {
        // Return a mocked successful initiation for Razorpay
        return ResponseEntity.ok(Map.of(
            "paymentId", "pay_" + System.currentTimeMillis(),
            "gateway", "razorpay",
            "gatewayOrderId", "order_" + System.currentTimeMillis(),
            "amount", payload.getOrDefault("amount", 0),
            "currency", "INR"
        ));
    }

    @PostMapping("/verify")
    public ResponseEntity<Map<String, Object>> verifyPayment(@RequestBody Map<String, Object> payload) {
        String bookingId = (String) payload.getOrDefault("bookingId", "");
        String paymentId = (String) payload.getOrDefault("paymentId", "");
        String status = (String) payload.getOrDefault("status", "paid");
        
        return ResponseEntity.ok(Map.of(
            "bookingId", bookingId,
            "paymentId", paymentId,
            "status", status
        ));
    }
}
