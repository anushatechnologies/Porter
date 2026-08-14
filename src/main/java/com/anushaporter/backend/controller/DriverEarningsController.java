package com.anushaporter.backend.controller;

import com.anushaporter.backend.model.*;
import com.anushaporter.backend.service.DriverAuthService;
import com.anushaporter.backend.service.payment.DriverPayoutService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/drivers/me")
public class DriverEarningsController {

    @Autowired
    private DriverPayoutService payoutService;

    @Autowired
    private DriverAuthService driverAuthService;

    private Driver getAuthenticatedDriver(HttpServletRequest request) {
        return driverAuthService.resolveAuthenticatedDriver(request);
    }

    /**
     * GET /api/drivers/me/earnings
     * Returns Today's trips, gross earnings, platform commission, net earnings, available balance, pending balance, and paid balance.
     */
    @GetMapping("/earnings")
    public ResponseEntity<?> getEarningsSummary(HttpServletRequest request) {
        Driver driver = getAuthenticatedDriver(request);
        if (driver == null) {
            return ResponseEntity.status(401).body(Map.of("success", false, "message", "Unauthorized or Driver profile not found"));
        }

        Map<String, Object> summary = payoutService.getDriverEarningsSummary(driver.getId().toString());
        summary.put("success", true);
        return ResponseEntity.ok(summary);
    }

    /**
     * GET /api/drivers/me/earnings/history
     * Returns ride-by-ride breakdown of completed trips with gross fare, platform commission, and net driver pay.
     */
    @GetMapping("/earnings/history")
    public ResponseEntity<?> getEarningsHistory(HttpServletRequest request) {
        Driver driver = getAuthenticatedDriver(request);
        if (driver == null) {
            return ResponseEntity.status(401).body(Map.of("success", false, "message", "Unauthorized or Driver profile not found"));
        }

        List<DriverEarnings> history = payoutService.getDriverEarningsHistory(driver.getId().toString());
        return ResponseEntity.ok(Map.of(
                "success", true,
                "count", history.size(),
                "history", history
        ));
    }

    /**
     * GET /api/drivers/me/balance
     * Returns separate ledger-based driver balance buckets.
     */
    @GetMapping("/balance")
    public ResponseEntity<?> getDriverBalance(HttpServletRequest request) {
        Driver driver = getAuthenticatedDriver(request);
        if (driver == null) {
            return ResponseEntity.status(401).body(Map.of("success", false, "message", "Unauthorized or Driver profile not found"));
        }

        Map<String, Object> balance = payoutService.getDriverBalance(driver.getId().toString());
        balance.put("success", true);
        return ResponseEntity.ok(balance);
    }

    /**
     * GET /api/drivers/me/payouts
     * Returns driver payout history with status and bank UTR references.
     */
    @GetMapping("/payouts")
    public ResponseEntity<?> getPayouts(HttpServletRequest request) {
        Driver driver = getAuthenticatedDriver(request);
        if (driver == null) {
            return ResponseEntity.status(401).body(Map.of("success", false, "message", "Unauthorized or Driver profile not found"));
        }

        List<DriverPayoutRecord> payouts = payoutService.getDriverPayouts(driver.getId().toString());
        return ResponseEntity.ok(Map.of(
                "success", true,
                "count", payouts.size(),
                "payouts", payouts
        ));
    }

    /**
     * GET /api/drivers/me/payouts/{payoutId}
     * Returns specific payout transaction details with settlement UTR & timeline.
     */
    @GetMapping("/payouts/{payoutId}")
    public ResponseEntity<?> getPayoutDetails(HttpServletRequest request, @PathVariable String payoutId) {
        Driver driver = getAuthenticatedDriver(request);
        if (driver == null) {
            return ResponseEntity.status(401).body(Map.of("success", false, "message", "Unauthorized or Driver profile not found"));
        }

        Optional<DriverPayoutRecord> payoutOpt = payoutService.getPayoutById(driver.getId().toString(), payoutId);
        if (payoutOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("success", false, "message", "Payout record not found"));
        }

        return ResponseEntity.ok(Map.of(
                "success", true,
                "payout", payoutOpt.get()
        ));
    }

    /**
     * GET /api/drivers/me/payout-account
     * Returns masked bank account (XXXX XXXX 4582) and UPI details.
     */
    @GetMapping("/payout-account")
    public ResponseEntity<?> getPayoutAccount(HttpServletRequest request) {
        Driver driver = getAuthenticatedDriver(request);
        if (driver == null) {
            return ResponseEntity.status(401).body(Map.of("success", false, "message", "Unauthorized or Driver profile not found"));
        }

        Optional<DriverPayoutAccount> accountOpt = payoutService.getPayoutAccount(driver.getId().toString());
        if (accountOpt.isEmpty()) {
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "hasAccount", false,
                    "account", Map.of()
            ));
        }

        DriverPayoutAccount acc = accountOpt.get();
        Map<String, Object> accountData = new LinkedHashMap<>();
        accountData.put("accountHolderName", acc.getAccountHolderName());
        accountData.put("bankName", acc.getBankName());
        accountData.put("accountNumberMasked", acc.getAccountNumberMasked());
        accountData.put("ifscCode", acc.getIfscCode());
        accountData.put("upiId", acc.getUpiId());
        accountData.put("verificationStatus", acc.getVerificationStatus());

        return ResponseEntity.ok(Map.of(
                "success", true,
                "hasAccount", true,
                "account", accountData
        ));
    }

    /**
     * POST /api/drivers/me/payout-account or PUT /api/drivers/me/payout-account
     * Securely registers or updates driver bank account and UPI details.
     */
    @RequestMapping(value = "/payout-account", method = {RequestMethod.POST, RequestMethod.PUT})
    public ResponseEntity<?> savePayoutAccount(
            HttpServletRequest request,
            @RequestBody Map<String, String> payload
    ) {
        Driver driver = getAuthenticatedDriver(request);
        if (driver == null) {
            return ResponseEntity.status(401).body(Map.of("success", false, "message", "Unauthorized or Driver profile not found"));
        }

        String accountHolder = payload.get("accountHolderName");
        String bankName = payload.get("bankName");
        String accountNumber = payload.get("accountNumber");
        String ifscCode = payload.get("ifscCode");
        String upiId = payload.get("upiId");

        if ((accountNumber == null || accountNumber.isBlank()) && (upiId == null || upiId.isBlank())) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Either bank accountNumber or upiId is required"));
        }

        DriverPayoutAccount acc = payoutService.savePayoutAccount(
                driver.getId().toString(),
                accountHolder != null ? accountHolder : driver.getName(),
                bankName,
                accountNumber,
                ifscCode,
                upiId
        );

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Payout account registered and verified successfully",
                "accountNumberMasked", acc.getAccountNumberMasked(),
                "upiId", acc.getUpiId() != null ? acc.getUpiId() : "",
                "verificationStatus", acc.getVerificationStatus()
        ));
    }

    /**
     * POST /api/drivers/me/payout-request or POST /api/drivers/me/payouts/request
     * Requests instant/manual settlement of driver available earnings.
     */
    @PostMapping({"/payout-request", "/payouts/request"})
    public ResponseEntity<?> requestPayout(
            HttpServletRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody(required = false) Map<String, Object> payload
    ) {
        Driver driver = getAuthenticatedDriver(request);
        if (driver == null) {
            return ResponseEntity.status(401).body(Map.of("success", false, "message", "Unauthorized or Driver profile not found"));
        }

        Double amount = null;
        String mode = "MANUAL";

        if (payload != null) {
            if (payload.get("amount") != null) {
                try { amount = Double.parseDouble(payload.get("amount").toString()); } catch (Exception ignored) {}
            }
            if (payload.get("payoutMode") != null) mode = String.valueOf(payload.get("payoutMode"));
            if (idempotencyKey == null && payload.get("idempotencyKey") != null) {
                idempotencyKey = String.valueOf(payload.get("idempotencyKey"));
            }
        }

        try {
            DriverPayoutRecord payout = payoutService.requestPayout(driver.getId().toString(), amount, mode, idempotencyKey);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "payoutId", payout.getPayoutId(),
                    "amount", payout.getAmount(),
                    "status", payout.getStatus().name(),
                    "destination", payout.getDestinationMasked(),
                    "utr", payout.getUtr() != null ? payout.getUtr() : "",
                    "message", "Payout processed successfully"
            ));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("success", false, "message", "Payout processing failed: " + e.getMessage()));
        }
    }
}
