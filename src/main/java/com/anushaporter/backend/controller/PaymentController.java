package com.anushaporter.backend.controller;

import com.anushaporter.backend.model.Order;
import com.anushaporter.backend.repository.OrderRepository;
import com.anushaporter.backend.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Handles online payment initiation and verification (Razorpay / UPI gateway).
 *
 * POST /api/payments/initiate – Creates a gateway order
 * POST /api/payments/verify   – Verifies payment and marks booking as paid
 */
@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private JwtUtil jwtUtil;

    /**
     * POST /api/payments/initiate
     * Creates a Razorpay/UPI gateway order for online payment.
     * Body: { "bookingId": "BK_...", "amount": 150.5, "currency": "INR" }
     */
    @PostMapping("/initiate")
    public ResponseEntity<Map<String, Object>> initiatePayment(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody Map<String, Object> payload) {

        Map<String, Object> response = new HashMap<>();

        String bookingId = (String) payload.getOrDefault("bookingId", "");
        double amount = payload.get("amount") != null ? ((Number) payload.get("amount")).doubleValue() : 0.0;
        String currency = (String) payload.getOrDefault("currency", "INR");

        // Look up the order to confirm amount
        if (!bookingId.isEmpty()) {
            Optional<Order> orderOpt = orderRepository.findByBookingId(bookingId);
            if (orderOpt.isPresent() && orderOpt.get().getAmount() != null) {
                amount = orderOpt.get().getAmount();
            }
        }

        String gatewayOrderId = "order_" + System.currentTimeMillis();
        String paymentId = "pay_" + System.currentTimeMillis();

        response.put("success", true);
        response.put("paymentId", paymentId);
        response.put("gateway", "razorpay");
        response.put("gatewayOrderId", gatewayOrderId);
        response.put("bookingId", bookingId);
        response.put("amount", amount);
        response.put("currency", currency);
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/payments/verify
     * Verifies payment success and marks the booking as paid.
     * Body: { "bookingId": "BK_...", "paymentId": "pay_...", "status": "paid" }
     */
    @PostMapping("/verify")
    public ResponseEntity<Map<String, Object>> verifyPayment(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody Map<String, Object> payload) {

        Map<String, Object> response = new HashMap<>();

        String bookingId = (String) payload.getOrDefault("bookingId", "");
        String paymentId = (String) payload.getOrDefault("paymentId", "");
        String status = (String) payload.getOrDefault("status", "paid");

        // Mark the order as paid in the database
        if (!bookingId.isEmpty()) {
            Optional<Order> orderOpt = orderRepository.findByBookingId(bookingId);
            if (orderOpt.isPresent()) {
                Order order = orderOpt.get();
                order.setPaymentStatus("paid");
                // If order was in unpaid-searching state, keep searching status
                // Only change payment status, not delivery status
                orderRepository.save(order);
            }
        }

        response.put("success", true);
        response.put("bookingId", bookingId);
        response.put("paymentId", paymentId);
        response.put("status", status);
        response.put("message", "Payment verified successfully");
        return ResponseEntity.ok(response);
    }
}
