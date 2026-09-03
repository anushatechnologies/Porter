package com.anushaporter.backend.controller;

import com.anushaporter.backend.service.DriverWalletService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class AdminWalletSettingsController {

    @Autowired
    private DriverWalletService driverWalletService;

    /**
     * GET /api/admin/settings/wallet or GET /api/settings/wallet
     * Returns platform commission rate, minimum balance required, minimum recharge amount, and auto-offline triggers.
     */
    @GetMapping({"/api/admin/settings/wallet", "/api/settings/wallet"})
    public ResponseEntity<?> getWalletSettings() {
        Map<String, Object> settings = driverWalletService.getAdminWalletSettings();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("commissionPercentage", settings.get("commissionPercentage"));
        response.put("minRequiredBalance", settings.get("minRequiredBalance"));
        response.put("minimumBalance", settings.get("minRequiredBalance"));
        response.put("minRechargeAmount", settings.get("minRechargeAmount"));
        response.put("walletRequiredForRides", settings.get("walletRequiredForRides"));
        response.put("autoOfflineWhenBalanceInsufficient", settings.get("autoOfflineWhenBalanceInsufficient"));

        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/admin/settings/wallet or PUT /api/admin/settings/wallet
     * Updates platform wallet & commission settings.
     */
    @PostMapping({"/api/admin/settings/wallet", "/api/settings/wallet"})
    public ResponseEntity<?> updateWalletSettingsPost(@RequestBody Map<String, Object> payload) {
        return updateWalletSettingsInternal(payload);
    }

    @PutMapping({"/api/admin/settings/wallet", "/api/settings/wallet"})
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
        response.put("minimumBalance", updated.get("minRequiredBalance"));
        response.put("minRechargeAmount", updated.get("minRechargeAmount"));
        response.put("walletRequiredForRides", updated.get("walletRequiredForRides"));
        response.put("autoOfflineWhenBalanceInsufficient", updated.get("autoOfflineWhenBalanceInsufficient"));

        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/admin/wallet/minimum-balance
     * Returns the current minimum required wallet balance / recharge amount.
     */
    @GetMapping({"/api/admin/wallet/minimum-balance", "/api/admin/settings/wallet/minimum-balance", "/api/wallet/minimum-balance"})
    public ResponseEntity<?> getMinimumBalance() {
        Map<String, Object> settings = driverWalletService.getAdminWalletSettings();
        double minBalance = (double) settings.get("minRequiredBalance");
        double minRecharge = (double) settings.get("minRechargeAmount");

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("minRequiredBalance", minBalance);
        response.put("minimumBalance", minBalance);
        response.put("minRechargeAmount", minRecharge);
        return ResponseEntity.ok(response);
    }

    /**
     * PUT /api/admin/wallet/minimum-balance
     * Modifies the minimum balance with optional driver retro-application.
     */
    @RequestMapping(
            value = {"/api/admin/wallet/minimum-balance", "/api/admin/settings/wallet/minimum-balance", "/api/wallet/minimum-balance"},
            method = {RequestMethod.PUT, RequestMethod.POST}
    )
    public ResponseEntity<?> updateMinimumBalance(@RequestBody Map<String, Object> payload) {
        double minBalance = 1000.0;
        if (payload.get("minimumBalance") != null) {
            minBalance = Double.parseDouble(String.valueOf(payload.get("minimumBalance")));
        } else if (payload.get("minRequiredBalance") != null) {
            minBalance = Double.parseDouble(String.valueOf(payload.get("minRequiredBalance")));
        } else if (payload.get("minRechargeAmount") != null) {
            minBalance = Double.parseDouble(String.valueOf(payload.get("minRechargeAmount")));
        }

        boolean applyToExistingDrivers = Boolean.parseBoolean(String.valueOf(payload.getOrDefault("applyToExistingDrivers", false)));
        String reason = (String) payload.get("reason");

        Map<String, Object> result = driverWalletService.updateMinimumBalance(minBalance, applyToExistingDrivers, reason);
        return ResponseEntity.ok(result);
    }

    /**
     * PUT / POST /api/admin/drivers/{id}/wallet
     * Direct driver wallet modification by Admin.
     */
    @RequestMapping(
            value = {"/api/admin/drivers/{id}/wallet", "/api/drivers/{id}/wallet"},
            method = {RequestMethod.PUT, RequestMethod.POST}
    )
    public ResponseEntity<?> modifyDriverWallet(
            @PathVariable String id,
            @RequestBody Map<String, Object> payload
    ) {
        try {
            Double targetBalance = payload.get("walletBalance") != null
                    ? Double.parseDouble(String.valueOf(payload.get("walletBalance")))
                    : null;
            Double deltaAmount = payload.get("amount") != null
                    ? Double.parseDouble(String.valueOf(payload.get("amount")))
                    : null;
            String action = (String) payload.get("action");
            if (action == null && targetBalance != null) {
                action = "set";
            }
            String reason = (String) payload.get("reason");

            Map<String, Object> res = driverWalletService.adminAdjustDriverWallet(id, targetBalance, deltaAmount, action, reason);
            return ResponseEntity.ok(res);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /**
     * PUT / POST /api/admin/customers/{id}/wallet
     * Direct customer wallet modification by Admin with AppUser synchronization.
     */
    @RequestMapping(
            value = {"/api/admin/customers/{id}/wallet", "/api/customers/{id}/wallet"},
            method = {RequestMethod.PUT, RequestMethod.POST}
    )
    public ResponseEntity<?> modifyCustomerWallet(
            @PathVariable String id,
            @RequestBody Map<String, Object> payload
    ) {
        try {
            Double targetBalance = payload.get("walletBalance") != null
                    ? Double.parseDouble(String.valueOf(payload.get("walletBalance")))
                    : null;
            Double deltaAmount = payload.get("amount") != null
                    ? Double.parseDouble(String.valueOf(payload.get("amount")))
                    : null;
            String action = (String) payload.get("action");
            if (action == null && targetBalance != null) {
                action = "set";
            }
            String reason = (String) payload.get("reason");

            Map<String, Object> res = driverWalletService.adminAdjustCustomerWallet(id, targetBalance, deltaAmount, action, reason);
            return ResponseEntity.ok(res);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /**
     * POST /api/admin/wallet/modify
     * Unified wallet modification endpoint for drivers and customers.
     */
    @PostMapping({"/api/admin/wallet/modify", "/api/wallet/modify"})
    public ResponseEntity<?> unifiedModifyWallet(@RequestBody Map<String, Object> payload) {
        try {
            String userType = (String) payload.get("userType");
            String id = String.valueOf(payload.get("id"));
            Double targetBalance = payload.get("walletBalance") != null
                    ? Double.parseDouble(String.valueOf(payload.get("walletBalance")))
                    : null;
            Double amount = payload.get("amount") != null
                    ? Double.parseDouble(String.valueOf(payload.get("amount")))
                    : null;
            String action = (String) payload.get("action");
            if (action == null && targetBalance != null) {
                action = "set";
            } else if ("set".equalsIgnoreCase(action) && targetBalance == null && amount != null) {
                targetBalance = amount;
            }
            String reason = (String) payload.get("reason");

            if ("driver".equalsIgnoreCase(userType)) {
                return ResponseEntity.ok(driverWalletService.adminAdjustDriverWallet(id, targetBalance, amount, action, reason));
            } else if ("customer".equalsIgnoreCase(userType) || "user".equalsIgnoreCase(userType)) {
                return ResponseEntity.ok(driverWalletService.adminAdjustCustomerWallet(id, targetBalance, amount, action, reason));
            } else {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Invalid userType: " + userType + ". Must be 'driver' or 'customer'"));
            }
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /**
     * POST /api/admin/settings/wallet/clean-duplicates
     * Cleans up any duplicate COMMISSION records and recalculates balances for affected drivers.
     */
    @PostMapping({"/api/admin/settings/wallet/clean-duplicates", "/api/settings/wallet/clean-duplicates"})
    public ResponseEntity<?> cleanDuplicates() {
        Map<String, Object> result = driverWalletService.cleanDuplicateCommissionTransactions();
        return ResponseEntity.ok(result);
    }

    /**
     * POST /api/admin/settings/wallet/recalculate/{driverId}
     * Recalculates wallet balance for a specific driver from transaction history.
     */
    @PostMapping({"/api/admin/settings/wallet/recalculate/{driverId}", "/api/settings/wallet/recalculate/{driverId}"})
    public ResponseEntity<?> recalculateDriverBalance(@PathVariable String driverId) {
        double newBalance = driverWalletService.recalculateDriverWalletBalance(driverId);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "driverId", driverId,
                "recalculatedBalance", newBalance,
                "message", "Driver wallet balance successfully recalculated from transaction history."
        ));
    }
}
