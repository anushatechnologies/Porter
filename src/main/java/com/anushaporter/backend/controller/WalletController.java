package com.anushaporter.backend.controller;

import com.anushaporter.backend.model.AppUser;
import com.anushaporter.backend.repository.AppUserRepository;
import com.anushaporter.backend.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.time.LocalDateTime;

@RestController

public class WalletController {

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private JwtUtil jwtUtil;

    /**
     * Get wallet balance.
     * GET /api/wallet
     */
    @GetMapping("/api/wallet")
    public ResponseEntity<Map<String, Object>> getWallet(
            @RequestHeader("Authorization") String authHeader) {

        Map<String, Object> response = new HashMap<>();
        String email = extractEmail(authHeader);
        if (email == null) {
            response.put("success", false);
            response.put("message", "Unauthorized");
            return ResponseEntity.status(401).body(response);
        }

        Optional<AppUser> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            response.put("success", false);
            response.put("message", "User not found");
            return ResponseEntity.status(404).body(response);
        }

        AppUser user = userOpt.get();
        double balance = user.getWalletBalance() != null ? user.getWalletBalance() : 0.0;

        List<Map<String, Object>> mockTransactions = new ArrayList<>();
        if (balance > 0) {
            Map<String, Object> t1 = new HashMap<>();
            t1.put("id", UUID.randomUUID().toString());
            t1.put("amount", balance);
            t1.put("type", "CREDIT");
            t1.put("description", "Wallet Top-up");
            t1.put("date", LocalDateTime.now().minusDays(1).toString());
            mockTransactions.add(t1);
        }

        response.put("success", true);
        response.put("balance", balance);
        response.put("currency", "INR");
        response.put("transactions", mockTransactions);
        return ResponseEntity.ok(response);
    }

    /**
     * Top up wallet.
     * POST /api/wallet/topup
     */
    @PostMapping("/api/wallet/topup")
    public ResponseEntity<Map<String, Object>> topUp(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, Object> body) {

        Map<String, Object> response = new HashMap<>();
        String email = extractEmail(authHeader);
        if (email == null) {
            response.put("success", false);
            response.put("message", "Unauthorized");
            return ResponseEntity.status(401).body(response);
        }

        Optional<AppUser> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            response.put("success", false);
            response.put("message", "User not found");
            return ResponseEntity.status(404).body(response);
        }

        double amount = 0;
        if (body.get("amount") != null) {
            amount = ((Number) body.get("amount")).doubleValue();
        }

        AppUser user = userOpt.get();
        double currentBalance = user.getWalletBalance() != null ? user.getWalletBalance() : 0.0;
        user.setWalletBalance(currentBalance + amount);
        userRepository.save(user);

        response.put("success", true);
        response.put("balance", user.getWalletBalance());
        return ResponseEntity.ok(response);
    }

    private String extractEmail(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) return null;
        try {
            return jwtUtil.getUsernameFromToken(authHeader.substring(7));
        } catch (Exception e) {
            return null;
        }
    }
}
