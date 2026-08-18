package com.anushaporter.backend.controller;

import com.anushaporter.backend.model.Driver;
import com.anushaporter.backend.model.DriverWallet;
import com.anushaporter.backend.model.WithdrawalRequest;
import com.anushaporter.backend.repository.WalletTransactionRepository;
import com.anushaporter.backend.repository.WithdrawalRequestRepository;
import com.anushaporter.backend.service.DriverWalletService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/driver")
public class DriverWalletController {

    @Autowired
    private DriverWalletService driverWalletService;

    @Autowired
    private WalletTransactionRepository walletTransactionRepository;

    @Autowired
    private WithdrawalRequestRepository withdrawalRequestRepository;

    @Autowired
    private DriverAPIController driverAPIController;

    private Driver getAuthDriver(HttpServletRequest request) {
        return driverAPIController.getAuthenticatedDriver(request);
    }

    @GetMapping("/wallet")
    public ResponseEntity<?> getWallet(HttpServletRequest request) {
        Driver driver = getAuthDriver(request);
        if (driver == null) {
            return ResponseEntity.status(401).body(Map.of("success", false, "message", "Unauthorized"));
        }

        DriverWallet wallet = driverWalletService.getWallet(String.valueOf(driver.getId()));

        Map<String, Object> walletData = new LinkedHashMap<>();
        walletData.put("availableBalance", wallet.getAvailableBalance());
        walletData.put("pendingBalance", wallet.getPendingBalance());
        walletData.put("totalEarned", wallet.getTotalEarned());
        walletData.put("totalWithdrawn", wallet.getTotalWithdrawn());
        walletData.put("platformCommission", wallet.getPlatformCommission());
        walletData.put("commissionPercentage", 5);
        walletData.put("minPayoutAmount", 100.00);
        walletData.put("isPayoutEligible", wallet.getAvailableBalance() >= 100.00);

        double needsMore = 100.00 - wallet.getAvailableBalance();
        walletData.put("needsMoreForPayout", needsMore > 0 ? needsMore : 0.0);

        boolean hasVerifiedAccount = driver.getAccountNumber() != null && !driver.getAccountNumber().isEmpty();
        walletData.put("hasVerifiedAccount", hasVerifiedAccount);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("availableBalance", wallet.getAvailableBalance());
        response.put("pendingBalance", wallet.getPendingBalance());
        response.put("totalEarned", wallet.getTotalEarned());
        response.put("totalWithdrawn", wallet.getTotalWithdrawn());
        response.put("platformCommission", wallet.getPlatformCommission());
        response.put("wallet", walletData);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/withdrawals")
    public ResponseEntity<?> requestWithdrawal(HttpServletRequest request, @RequestBody Map<String, Object> payload) {
        Driver driver = getAuthDriver(request);
        if (driver == null) {
            return ResponseEntity.status(401).body(Map.of("success", false, "message", "Unauthorized"));
        }

        try {
            double amount = Double.parseDouble(String.valueOf(payload.get("amount")));
            String bankAccountId = payload.get("bankAccountId") != null ? String.valueOf(payload.get("bankAccountId")) : driver.getAccountNumber();

            if (amount < 100.0) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Minimum payout amount is 100.00"));
            }

            WithdrawalRequest req = driverWalletService.requestWithdrawal(String.valueOf(driver.getId()), amount, bankAccountId);
            DriverWallet wallet = driverWalletService.getWallet(String.valueOf(driver.getId()));

            Map<String, Object> reqData = new LinkedHashMap<>();
            reqData.put("id", req.getId());
            reqData.put("amount", req.getAmount());
            reqData.put("heldAmount", req.getHeldAmount());
            reqData.put("status", req.getStatus());
            reqData.put("requestedAt", req.getRequestedAt());

            return ResponseEntity.status(201).body(Map.of(
                    "success", true,
                    "message", "Withdrawal request submitted for Admin approval. Amount is held.",
                    "availableBalance", wallet.getAvailableBalance(),
                    "heldAmount", wallet.getPendingBalance(),
                    "request", reqData
            ));

        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("success", false, "message", "Internal server error: " + e.getMessage()));
        }
    }

    @GetMapping("/withdrawals/active")
    public ResponseEntity<?> getActiveWithdrawal(HttpServletRequest request) {
        Driver driver = getAuthDriver(request);
        if (driver == null) {
            return ResponseEntity.status(401).body(Map.of("success", false, "message", "Unauthorized"));
        }

        List<WithdrawalRequest> requests = withdrawalRequestRepository.findByDriverIdAndStatusInOrderByRequestedAtDesc(
                String.valueOf(driver.getId()),
                List.of("PENDING_ADMIN_APPROVAL", "PROCESSING")
        );

        if (requests.isEmpty()) {
            Map<String, Object> res = new LinkedHashMap<>();
            res.put("success", true);
            res.put("request", null);
            return ResponseEntity.ok(res);
        }

        WithdrawalRequest req = requests.get(0);
        Map<String, Object> reqData = new LinkedHashMap<>();
        reqData.put("id", req.getId());
        reqData.put("amount", req.getAmount());
        reqData.put("heldAmount", req.getHeldAmount());
        reqData.put("status", req.getStatus());
        reqData.put("bankName", driver.getBankName() != null ? driver.getBankName() : "Unknown Bank");

        String accountNum = req.getBankAccountId() != null ? req.getBankAccountId() : driver.getAccountNumber();
        String mask = accountNum != null && accountNum.length() >= 4
                ? "•••• " + accountNum.substring(accountNum.length() - 4)
                : accountNum;
        reqData.put("accountNumberMasked", mask);
        reqData.put("requestedAt", req.getRequestedAt());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("request", reqData);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/withdrawals/history")
    public ResponseEntity<?> getWithdrawalHistory(HttpServletRequest request) {
        Driver driver = getAuthDriver(request);
        if (driver == null) {
            return ResponseEntity.status(401).body(Map.of("success", false, "message", "Unauthorized"));
        }

        List<WithdrawalRequest> list = withdrawalRequestRepository.findByDriverIdAndStatusInOrderByRequestedAtDesc(
                String.valueOf(driver.getId()),
                List.of("COMPLETED", "REJECTED", "CANCELLED")
        );
        return ResponseEntity.ok(Map.of(
                "success", true,
                "withdrawals", list
        ));
    }
}