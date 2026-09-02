package com.anushaporter.backend.controller;

import com.anushaporter.backend.model.Driver;
import com.anushaporter.backend.model.DriverWallet;
import com.anushaporter.backend.model.WithdrawalRequest;
import com.anushaporter.backend.model.Order;
import com.anushaporter.backend.repository.DriverRepository;
import com.anushaporter.backend.repository.OrderRepository;
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
import java.util.Optional;

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
    private DriverRepository driverRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private DriverAPIController driverAPIController;

    private Driver getAuthDriver(HttpServletRequest request) {
        return driverAPIController.getAuthenticatedDriver(request);
    }

    @GetMapping({"/wallet", "/me/wallet"})
    public ResponseEntity<?> getWallet(HttpServletRequest request) {
        Driver driver = getAuthDriver(request);
        if (driver == null) {
            return ResponseEntity.status(401).body(Map.of("success", false, "message", "Unauthorized"));
        }

        String driverId = String.valueOf(driver.getId());
        DriverWallet wallet = driverWalletService.getWallet(driverId);
        double commissionPercent = driverWalletService.getCommissionPercentage();
        double minRequiredBalance = driverWalletService.getMinRequiredBalance();
        boolean isEligible = driverWalletService.isDriverEligibleForRides(driverId);
        String eligibilityReason = driverWalletService.getEligibilityReason(driverId);

        Map<String, Object> walletData = new LinkedHashMap<>();
        walletData.put("availableBalance", wallet.getAvailableBalance());
        walletData.put("pendingBalance", wallet.getPendingBalance());
        walletData.put("totalEarned", wallet.getTotalEarned());
        walletData.put("totalWithdrawn", wallet.getTotalWithdrawn());
        walletData.put("platformCommission", wallet.getPlatformCommission());
        walletData.put("commissionPercentage", commissionPercent);
        walletData.put("minRequiredBalance", minRequiredBalance);
        walletData.put("isEligible", isEligible);
        walletData.put("eligibilityReason", eligibilityReason);
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

    @GetMapping({"/wallet/transactions", "/transactions"})
    public ResponseEntity<?> getWalletTransactions(HttpServletRequest request) {
        Driver driver = getAuthDriver(request);
        if (driver == null) {
            return ResponseEntity.status(401).body(Map.of("success", false, "message", "Unauthorized"));
        }

        String driverId = String.valueOf(driver.getId());
        List<com.anushaporter.backend.model.WalletTransaction> txList =
                walletTransactionRepository.findByDriverIdOrderByCreatedAtDesc(driverId);

        List<Map<String, Object>> formattedTx = new java.util.ArrayList<>();
        for (com.anushaporter.backend.model.WalletTransaction tx : txList) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", tx.getId());
            item.put("orderId", tx.getOrderId());
            item.put("transactionType", tx.getTransactionType());
            item.put("amount", tx.getAmount());
            item.put("grossAmount", tx.getGrossAmount());
            item.put("commissionAmount", tx.getCommissionAmount());
            item.put("balanceBefore", tx.getBalanceBefore());
            item.put("balanceAfter", tx.getBalanceAfter());
            item.put("description", tx.getDescription());
            item.put("status", tx.getStatus());
            item.put("referenceId", tx.getReferenceId());
            item.put("createdAt", tx.getCreatedAt() != null ? tx.getCreatedAt().toString() : null);
            formattedTx.add(item);
        }

        return ResponseEntity.ok(Map.of(
                "success", true,
                "transactions", formattedTx
        ));
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

    /**
     * Top-up the exact remaining amount (or custom amount) required for accepting a ride.
     * POST /api/driver/orders/{bookingId}/recharge-remaining
     */
    @PostMapping("/orders/{bookingId}/recharge-remaining")
    public ResponseEntity<?> rechargeRemainingForRide(
            HttpServletRequest request,
            @PathVariable String bookingId,
            @RequestBody(required = false) Map<String, Object> body
    ) {
        Driver driver = getAuthDriver(request);
        if (driver == null) {
            return ResponseEntity.status(401).body(Map.of("success", false, "message", "Unauthorized"));
        }

        // Check eligibility & remaining amount for the order
        Optional<Order> orderOpt = orderRepository.findByBookingId(bookingId);
        if (orderOpt.isEmpty()) {
            try { orderOpt = orderRepository.findById(Long.valueOf(bookingId)); } catch (Exception ignored) {}
        }
        if (orderOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("success", false, "message", "Order not found: " + bookingId));
        }

        Order order = orderOpt.get();
        Map<String, Object> eligibility = driverWalletService.checkRideAcceptanceEligibility(driver, order);
        double remaining = ((Number) eligibility.getOrDefault("remainingAmount", 0.0)).doubleValue();

        double amountToRecharge = remaining;
        if (body != null && body.containsKey("amount")) {
            try {
                double customAmount = Double.parseDouble(String.valueOf(body.get("amount")));
                if (customAmount > 0) {
                    amountToRecharge = customAmount;
                }
            } catch (Exception ignored) {}
        }

        if (amountToRecharge <= 0) {
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Driver already has sufficient balance to accept this ride",
                    "canAccept", true,
                    "walletBalance", driver.getWalletBalance()
            ));
        }

        String paymentRef = body != null && body.get("paymentId") != null
                ? String.valueOf(body.get("paymentId"))
                : "RECHARGE_RIDE_" + bookingId + "_" + System.currentTimeMillis();

        DriverWallet wallet = driverWalletService.rechargeWallet(
                String.valueOf(driver.getId()),
                amountToRecharge,
                paymentRef,
                "Ride Top-up to accept " + (order.getBookingId() != null ? order.getBookingId() : bookingId)
        );

        Driver updatedDriver = driverRepository.findById(driver.getId()).orElse(driver);
        Map<String, Object> recheckedEligibility = driverWalletService.checkRideAcceptanceEligibility(updatedDriver, order);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("success", true);
        resp.put("rechargedAmount", amountToRecharge);
        resp.put("newWalletBalance", wallet.getAvailableBalance());
        resp.put("driverStatus", updatedDriver.getStatus());
        resp.put("canAccept", recheckedEligibility.get("canAccept"));
        resp.put("remainingAmount", recheckedEligibility.get("remainingAmount"));
        resp.put("message", "Recharged ₹" + amountToRecharge + " successfully. You can now accept this ride!");
        return ResponseEntity.ok(resp);
    }

    /**
     * Direct wallet recharge for authenticated driver.
     * POST /api/driver/wallet/recharge
     */
    @PostMapping("/wallet/recharge")
    public ResponseEntity<?> rechargeSelfWallet(
            HttpServletRequest request,
            @RequestBody Map<String, Object> payload
    ) {
        Driver driver = getAuthDriver(request);
        if (driver == null) {
            return ResponseEntity.status(401).body(Map.of("success", false, "message", "Unauthorized"));
        }

        if (payload == null || !payload.containsKey("amount")) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "amount is required"));
        }

        double amount;
        try {
            amount = Double.parseDouble(String.valueOf(payload.get("amount")));
            if (amount <= 0) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Amount must be greater than 0"));
            }
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Invalid amount"));
        }

        String paymentId = payload.get("paymentId") != null ? String.valueOf(payload.get("paymentId")) : "WALLET_RECHARGE_" + System.currentTimeMillis();
        String description = payload.get("description") != null ? String.valueOf(payload.get("description")) : "Driver Wallet Recharge";

        DriverWallet wallet = driverWalletService.rechargeWallet(String.valueOf(driver.getId()), amount, paymentId, description);
        Driver updatedDriver = driverRepository.findById(driver.getId()).orElse(driver);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("success", true);
        resp.put("rechargedAmount", amount);
        resp.put("walletBalance", wallet.getAvailableBalance());
        resp.put("driverStatus", updatedDriver.getStatus());
        resp.put("message", "Wallet recharged successfully");
        return ResponseEntity.ok(resp);
    }
}