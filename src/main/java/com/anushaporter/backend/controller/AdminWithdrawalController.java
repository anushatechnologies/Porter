package com.anushaporter.backend.controller;

import com.anushaporter.backend.service.DriverWalletService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/withdrawals")
public class AdminWithdrawalController {

    @Autowired
    private DriverWalletService driverWalletService;

    @PutMapping("/{id}/approve")
    public ResponseEntity<?> approveWithdrawal(
            @PathVariable String id, 
            @RequestBody(required = false) Map<String, Object> payload) {
        try {
            String payoutProvider = payload != null && payload.get("payoutProvider") != null 
                ? String.valueOf(payload.get("payoutProvider")) : "MANUAL";
            String payoutReference = payload != null && payload.get("payoutReference") != null 
                ? String.valueOf(payload.get("payoutReference")) : "REF_" + System.currentTimeMillis();
            
            var req = driverWalletService.approveWithdrawal(id, payoutProvider, payoutReference);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Withdrawal approved successfully",
                    "request", req
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PutMapping("/{id}/reject")
    public ResponseEntity<?> rejectWithdrawal(
            @PathVariable String id, 
            @RequestBody(required = false) Map<String, Object> payload) {
        try {
            String reason = payload != null && payload.get("reason") != null 
                ? String.valueOf(payload.get("reason")) : "Admin rejected request";
                
            var req = driverWalletService.rejectWithdrawal(id, reason);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Withdrawal rejected, funds returned to driver's available balance",
                    "request", req
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }
}
