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
}
