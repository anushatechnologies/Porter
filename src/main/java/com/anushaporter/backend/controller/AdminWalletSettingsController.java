package com.anushaporter.backend.controller;

import com.anushaporter.backend.service.DriverWalletService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping({"/api/admin/settings/wallet", "/api/settings/wallet"})
public class AdminWalletSettingsController {

    @Autowired
    private DriverWalletService driverWalletService;

    /**
     * GET /api/admin/settings/wallet or GET /api/settings/wallet
     * Returns platform commission rate, minimum balance required, and auto-offline triggers.
     */
    @GetMapping
    public ResponseEntity<?> getWalletSettings() {
        Map<String, Object> settings = driverWalletService.getAdminWalletSettings();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("commissionPercentage", settings.get("commissionPercentage"));
        response.put("minRequiredBalance", settings.get("minRequiredBalance"));
        response.put("walletRequiredForRides", settings.get("walletRequiredForRides"));
        response.put("autoOfflineWhenBalanceInsufficient", settings.get("autoOfflineWhenBalanceInsufficient"));

        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/admin/settings/wallet or PUT /api/admin/settings/wallet
     * Updates platform wallet & commission settings.
     */
    @PostMapping
    public ResponseEntity<?> updateWalletSettingsPost(@RequestBody Map<String, Object> payload) {
        return updateWalletSettingsInternal(payload);
    }

    @PutMapping
    public ResponseEntity<?> updateWalletSettingsPut(@RequestBody Map<String, Object> payload) {
        return updateWalletSettingsInternal(payload);
    }

    private ResponseEntity<?> updateWalletSettingsInternal(Map<String, Object> payload) {
        Map<String, Object> updated = driverWalletService.updateAdminWalletSettings(payload);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("message", "Wallet and commission settings updated successfully");
        response.put("commissionPercentage", updated.get("commissionPercentage"));
        response.put("minRequiredBalance", updated.get("minRequiredBalance"));
        response.put("walletRequiredForRides", updated.get("walletRequiredForRides"));
        response.put("autoOfflineWhenBalanceInsufficient", updated.get("autoOfflineWhenBalanceInsufficient"));

        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/admin/settings/wallet/clean-duplicates
     * Cleans up any duplicate COMMISSION records and recalculates balances for affected drivers.
     */
    @PostMapping("/clean-duplicates")
    public ResponseEntity<?> cleanDuplicates() {
        Map<String, Object> result = driverWalletService.cleanDuplicateCommissionTransactions();
        return ResponseEntity.ok(result);
    }

    /**
     * POST /api/admin/settings/wallet/recalculate/{driverId}
     * Recalculates wallet balance for a specific driver from transaction history.
     */
    @PostMapping("/recalculate/{driverId}")
    public ResponseEntity<?> recalculateDriverBalance(@PathVariable String driverId) {
        double newBalance = driverWalletService.recalculateDriverWalletBalance(driverId);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "driverId", driverId,
                "recalculatedBalance", newBalance,
                "message", "Driver wallet balance successfully recalculated from transaction history."
        ));
    }

    /**
     * GET /api/admin/settings/wallet/minimum-balance
     * Returns current minimum required balance for drivers.
     */
    @GetMapping("/minimum-balance")
    public ResponseEntity<?> getMinimumBalance() {
        double minBalance = driverWalletService.getMinRequiredBalance();
        return ResponseEntity.ok(Map.of(
                "success", true,
                "minRequiredBalance", minBalance,
                "minimumBalance", minBalance
        ));
    }

    /**
     * PUT or POST /api/admin/settings/wallet/minimum-balance
     * Admin modifies the minimum wallet balance for all drivers.
     * Payload: { "minimumBalance": 1000.0, "applyToExistingDrivers": false }
     */
    @RequestMapping(value = "/minimum-balance", method = {RequestMethod.PUT, RequestMethod.POST, RequestMethod.PATCH})
    public ResponseEntity<?> updateMinimumBalance(@RequestBody Map<String, Object> payload) {
        Object minObj = payload.get("minimumBalance");
        if (minObj == null) minObj = payload.get("minRequiredBalance");
        if (minObj == null) minObj = payload.get("minWalletBalance");
        if (minObj == null) minObj = payload.get("amount");

        if (minObj == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Minimum balance amount is required"));
        }

        double minVal;
        try {
            minVal = Double.parseDouble(String.valueOf(minObj));
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Invalid minimum balance amount"));
        }

        boolean applyToExisting = Boolean.TRUE.equals(payload.get("applyToExistingDrivers"));
        String reason = (String) payload.getOrDefault("reason", "Admin updated minimum wallet balance");

        driverWalletService.updateAdminWalletSettings(Map.of(
                "minRequiredBalance", minVal,
                "applyToExistingDrivers", applyToExisting,
                "reason", reason
        ));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("minRequiredBalance", minVal);
        response.put("minimumBalance", minVal);
        response.put("message", "Minimum wallet balance updated to ₹" + minVal + " for all drivers");

        if (applyToExisting) {
            Map<String, Object> bulkResult = driverWalletService.applyMinimumBalanceToExistingDrivers(minVal, reason);
            response.put("appliedToExistingDrivers", true);
            response.put("driversUpdated", bulkResult.get("driversUpdated"));
            response.put("totalAmountCredited", bulkResult.get("totalAmountCredited"));
        }

        return ResponseEntity.ok(response);
    }
}

