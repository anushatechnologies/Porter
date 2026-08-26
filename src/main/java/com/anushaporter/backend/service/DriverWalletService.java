package com.anushaporter.backend.service;

import com.anushaporter.backend.model.Driver;
import com.anushaporter.backend.model.DriverWallet;
import com.anushaporter.backend.model.GlobalSettings;
import com.anushaporter.backend.model.Order;
import com.anushaporter.backend.model.WalletTransaction;
import com.anushaporter.backend.model.WithdrawalRequest;
import com.anushaporter.backend.repository.DriverRepository;
import com.anushaporter.backend.repository.DriverWalletRepository;
import com.anushaporter.backend.repository.GlobalSettingsRepository;
import com.anushaporter.backend.repository.WalletTransactionRepository;
import com.anushaporter.backend.repository.WithdrawalRequestRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class DriverWalletService {

    @Autowired
    private DriverWalletRepository driverWalletRepository;

    @Autowired
    private WalletTransactionRepository walletTransactionRepository;

    @Autowired
    private WithdrawalRequestRepository withdrawalRequestRepository;

    @Autowired
    private GlobalSettingsRepository globalSettingsRepository;

    @Autowired
    private DriverRepository driverRepository;

    private static final double DEFAULT_COMMISSION_PERCENTAGE = 5.0; // 5% Platform Commission
    private static final double DEFAULT_MIN_REQUIRED_BALANCE = 0.0;

    public DriverWallet getWallet(String driverId) {
        return driverWalletRepository.findByDriverId(driverId).orElseGet(() -> {
            DriverWallet newWallet = new DriverWallet();
            newWallet.setDriverId(driverId);
            return driverWalletRepository.save(newWallet);
        });
    }

    /**
     * Reads Admin Wallet & Commission Settings from GlobalSettings
     */
    public Map<String, Object> getAdminWalletSettings() {
        Map<String, Object> settings = new LinkedHashMap<>();

        double commissionPercentage = DEFAULT_COMMISSION_PERCENTAGE;
        double minRequiredBalance = DEFAULT_MIN_REQUIRED_BALANCE;
        boolean walletRequiredForRides = true;
        boolean autoOfflineWhenBalanceInsufficient = true;

        if (globalSettingsRepository != null) {
            Optional<GlobalSettings> commOpt = globalSettingsRepository.findBySettingKey("wallet_commission_percentage");
            if (commOpt.isPresent() && commOpt.get().getSettingValue() != null) {
                try { commissionPercentage = Double.parseDouble(commOpt.get().getSettingValue()); } catch (Exception ignored) {}
            }

            Optional<GlobalSettings> minBalOpt = globalSettingsRepository.findBySettingKey("wallet_min_required_balance");
            if (minBalOpt.isPresent() && minBalOpt.get().getSettingValue() != null) {
                try { minRequiredBalance = Double.parseDouble(minBalOpt.get().getSettingValue()); } catch (Exception ignored) {}
            }

            Optional<GlobalSettings> reqOpt = globalSettingsRepository.findBySettingKey("wallet_required_for_rides");
            if (reqOpt.isPresent() && reqOpt.get().getSettingValue() != null) {
                walletRequiredForRides = Boolean.parseBoolean(reqOpt.get().getSettingValue());
            }

            Optional<GlobalSettings> autoOffOpt = globalSettingsRepository.findBySettingKey("wallet_auto_offline_insufficient");
            if (autoOffOpt.isPresent() && autoOffOpt.get().getSettingValue() != null) {
                autoOfflineWhenBalanceInsufficient = Boolean.parseBoolean(autoOffOpt.get().getSettingValue());
            }
        }

        settings.put("commissionPercentage", commissionPercentage);
        settings.put("minRequiredBalance", minRequiredBalance);
        settings.put("walletRequiredForRides", walletRequiredForRides);
        settings.put("autoOfflineWhenBalanceInsufficient", autoOfflineWhenBalanceInsufficient);
        return settings;
    }

    /**
     * Updates Admin Wallet & Commission Settings
     */
    @Transactional
    public Map<String, Object> updateAdminWalletSettings(Map<String, Object> payload) {
        if (globalSettingsRepository == null || payload == null) {
            return getAdminWalletSettings();
        }

        if (payload.containsKey("commissionPercentage")) {
            saveSetting("wallet_commission_percentage", String.valueOf(payload.get("commissionPercentage")));
        }
        if (payload.containsKey("minRequiredBalance")) {
            saveSetting("wallet_min_required_balance", String.valueOf(payload.get("minRequiredBalance")));
        }
        if (payload.containsKey("walletRequiredForRides")) {
            saveSetting("wallet_required_for_rides", String.valueOf(payload.get("walletRequiredForRides")));
        }
        if (payload.containsKey("autoOfflineWhenBalanceInsufficient")) {
            saveSetting("wallet_auto_offline_insufficient", String.valueOf(payload.get("autoOfflineWhenBalanceInsufficient")));
        }

        return getAdminWalletSettings();
    }

    private void saveSetting(String key, String value) {
        Optional<GlobalSettings> existing = globalSettingsRepository.findBySettingKey(key);
        GlobalSettings s = existing.orElseGet(GlobalSettings::new);
        s.setSettingKey(key);
        s.setSettingValue(value);
        globalSettingsRepository.save(s);
    }

    public double getCommissionPercentage() {
        Object val = getAdminWalletSettings().get("commissionPercentage");
        return val instanceof Number ? ((Number) val).doubleValue() : DEFAULT_COMMISSION_PERCENTAGE;
    }

    @Autowired
    private com.anushaporter.backend.repository.OrderRepository orderRepository;

    public Driver findDriverEntity(String driverIdStr) {
        if (driverIdStr == null || driverIdStr.isBlank()) {
            return null;
        }
        try {
            Long id = Long.parseLong(driverIdStr.replaceAll("\\D+", ""));
            Optional<Driver> opt = driverRepository.findById(id);
            if (opt.isPresent()) return opt.get();
        } catch (Exception ignored) {}

        Optional<Driver> phoneOpt = driverRepository.findByPhone(driverIdStr);
        if (phoneOpt.isPresent()) return phoneOpt.get();

        return driverRepository.findByEmail(driverIdStr).orElse(null);
    }

    public double getMinRequiredBalance() {
        Object val = getAdminWalletSettings().get("minRequiredBalance");
        return val instanceof Number ? ((Number) val).doubleValue() : DEFAULT_MIN_REQUIRED_BALANCE;
    }

    public boolean isDriverEligibleForRides(String driverId) {
        Driver driver = findDriverEntity(driverId);
        double minRequired = getMinRequiredBalance();
        if (driver != null && driver.getWalletBalance() != null) {
            return driver.getWalletBalance() >= minRequired && driver.getWalletBalance() > 0.0;
        }
        DriverWallet wallet = getWallet(driverId);
        return wallet.getAvailableBalance() >= minRequired && wallet.getAvailableBalance() > 0.0;
    }

    public String getEligibilityReason(String driverId) {
        return isDriverEligibleForRides(driverId) ? "Sufficient balance" : "Insufficient balance. Recharge wallet to accept rides.";
    }

    /**
     * Recharges driver's wallet after successful payment (e.g. Razorpay)
     */
    @Transactional
    public DriverWallet rechargeWallet(String driverId, double amount, String paymentId, String description) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Recharge amount must be greater than 0");
        }

        DriverWallet wallet = getWallet(driverId);
        double balanceBefore = wallet.getAvailableBalance();
        double balanceAfter = balanceBefore + amount;

        wallet.setAvailableBalance(balanceAfter);
        driverWalletRepository.save(wallet);

        // Sync Driver entity
        Driver driver = findDriverEntity(driverId);
        if (driver != null) {
            double prevBal = driver.getWalletBalance() != null ? driver.getWalletBalance() : 0.0;
            if (prevBal <= 0.0 && balanceAfter > 0.0) {
                driver.setStatus("online");
            }
            driver.setWalletBalance(balanceAfter);
            driverRepository.save(driver);
        }

        // Record Transaction: RECHARGE
        String txId = "TXN_W_" + System.currentTimeMillis();
        WalletTransaction rechargeTx = new WalletTransaction();
        rechargeTx.setId(txId);
        rechargeTx.setDriverId(driver != null ? String.valueOf(driver.getId()) : driverId);
        rechargeTx.setTransactionType("RECHARGE");
        rechargeTx.setGrossAmount(amount);
        rechargeTx.setCommissionAmount(0.0);
        rechargeTx.setAmount(amount);
        rechargeTx.setBalanceBefore(balanceBefore);
        rechargeTx.setBalanceAfter(balanceAfter);
        rechargeTx.setStatus("SUCCESS");
        rechargeTx.setReferenceId(paymentId);
        rechargeTx.setDescription(description != null && !description.isBlank() ? description : "Wallet Recharge (Razorpay)");
        walletTransactionRepository.save(rechargeTx);

        return wallet;
    }

    /**
     * Direct driver wallet top-up / recharge by Admin or Driver
     */
    @Transactional
    public Map<String, Object> rechargeDriverWalletDirect(String driverIdStr, double amount, String paymentReference, String notes) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Recharge amount must be greater than 0");
        }

        Driver driver = findDriverEntity(driverIdStr);
        if (driver == null) {
            throw new IllegalArgumentException("Driver not found with identifier: " + driverIdStr);
        }

        double previousBalance = driver.getWalletBalance() != null ? driver.getWalletBalance() : 0.0;
        double newBalance = Math.round((previousBalance + amount) * 100.0) / 100.0;

        if (previousBalance <= 0.0 && newBalance > 0.0) {
            driver.setStatus("online");
        }
        driver.setWalletBalance(newBalance);
        driverRepository.save(driver);

        // Sync DriverWallet entity
        DriverWallet wallet = getWallet(String.valueOf(driver.getId()));
        wallet.setAvailableBalance(newBalance);
        driverWalletRepository.save(wallet);

        // Record WalletTransaction
        String txId = "TXN_W_" + System.currentTimeMillis();
        WalletTransaction rechargeTx = new WalletTransaction();
        rechargeTx.setId(txId);
        rechargeTx.setDriverId(String.valueOf(driver.getId()));
        rechargeTx.setTransactionType("RECHARGE");
        rechargeTx.setGrossAmount(amount);
        rechargeTx.setCommissionAmount(0.0);
        rechargeTx.setAmount(amount);
        rechargeTx.setBalanceBefore(previousBalance);
        rechargeTx.setBalanceAfter(newBalance);
        rechargeTx.setStatus("SUCCESS");
        rechargeTx.setReferenceId(paymentReference);
        rechargeTx.setDescription(notes != null && !notes.isBlank() ? notes : "Admin Wallet Top-up / UPI Payment Received");
        walletTransactionRepository.save(rechargeTx);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("driverId", "DRV-" + driver.getId());
        response.put("previousBalance", previousBalance);
        response.put("rechargedAmount", amount);
        response.put("newWalletBalance", newBalance);
        response.put("transactionId", txId);
        return response;
    }

    /**
     * Assigns order to driver after verifying active wallet balance (wallet_balance > 0.00).
     * DO NOT deduct wallet balance or create wallet_transactions here.
     */
    @Transactional
    public Map<String, Object> assignOrder(Order order, Driver driver) {
        DriverWallet wallet = getWallet(String.valueOf(driver.getId()));
        double walletBalance = driver.getWalletBalance() != null && driver.getWalletBalance() > 0
                ? driver.getWalletBalance()
                : (wallet.getAvailableBalance() != null ? wallet.getAvailableBalance() : 0.0);

        // Check if wallet_balance <= 0
        if (walletBalance <= 0) {
            throw new IllegalStateException("Driver wallet balance is ₹0 or negative. Driver must recharge before taking orders.");
        }

        String orderIdStr = order.getBookingId() != null ? order.getBookingId() : String.valueOf(order.getId());

        // Update Order
        order.setDriverId(String.valueOf(driver.getId()));
        order.setDriverName(driver.getName());
        order.setDriverPhone(driver.getPhone());
        order.setDriverEmail(driver.getEmail());
        order.setDriverVehicleNumber(driver.getVehicleNumber());
        order.setStatus("assigned");
        orderRepository.save(order);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("orderId", orderIdStr);
        response.put("driverId", "DRV-" + driver.getId());
        response.put("orderFare", order.getAmount() != null ? order.getAmount() : 0.0);
        response.put("status", "assigned");
        response.put("walletBalance", walletBalance);
        response.put("remainingWalletBalance", walletBalance);
        response.put("message", "Driver assigned successfully");
        return response;
    }

    /**
     * Legacy signature forwarding to assignOrder.
     */
    @Transactional
    public Map<String, Object> assignOrderWithCommission(Order order, Driver driver, Double commissionRate) {
        return assignOrder(order, driver);
    }

    /**
     * Deducts 5% Commission Cut on Order Completion / Confirm Payment:
     * 1. Calculate 5% Commission Cut: commission = order.total_amount * 0.05
     * 2. Deduct 5% from Driver's Wallet: wallet_balance = wallet_balance - commission
     * 3. Log the Transaction in wallet_transactions with type = 'COMMISSION_DEDUCTION'
     * 4. If balance <= 0, automatically switch driver to offline
     */
    @Transactional
    public WalletTransaction deductCommissionOnCompletion(String driverIdStr, String orderId, double totalAmount) {
        Driver driver = findDriverEntity(driverIdStr);
        if (driver == null || totalAmount <= 0) {
            return null;
        }

        // Idempotency: Prevent duplicate COMMISSION_DEDUCTION for the same order and driver
        if (orderId != null && !orderId.isBlank()) {
            Optional<WalletTransaction> existingTx = walletTransactionRepository
                    .findFirstByDriverIdAndOrderIdAndTransactionType(String.valueOf(driver.getId()), orderId, "COMMISSION_DEDUCTION");
            if (existingTx.isPresent()) {
                return existingTx.get();
            }
        }

        DriverWallet wallet = getWallet(String.valueOf(driver.getId()));
        double commissionRate = getCommissionPercentage() / 100.0;
        if (commissionRate <= 0) commissionRate = 0.05;
        double commission = Math.round(totalAmount * commissionRate * 100.0) / 100.0;

        double balanceBefore = (driver.getWalletBalance() != null && driver.getWalletBalance() != 0.0)
                ? driver.getWalletBalance()
                : (wallet.getAvailableBalance() != null && wallet.getAvailableBalance() != 0.0
                    ? wallet.getAvailableBalance()
                    : (driver.getWalletBalance() != null ? driver.getWalletBalance() : 0.0));
        double balanceAfter = Math.round((balanceBefore - commission) * 100.0) / 100.0;

        driver.setWalletBalance(balanceAfter);
        if (balanceAfter <= 0.0) {
            driver.setStatus("offline");
        }
        driverRepository.save(driver);

        // Sync DriverWallet
        wallet.setAvailableBalance(balanceAfter);
        wallet.setPlatformCommission((wallet.getPlatformCommission() != null ? wallet.getPlatformCommission() : 0.0) + commission);
        wallet.setTotalEarned((wallet.getTotalEarned() != null ? wallet.getTotalEarned() : 0.0) + totalAmount);
        driverWalletRepository.save(wallet);

        String txId = "TXN_W_" + System.currentTimeMillis();

        // Log in wallet_transactions with type = 'COMMISSION_DEDUCTION'
        WalletTransaction commTx = new WalletTransaction();
        commTx.setId(txId);
        commTx.setDriverId(String.valueOf(driver.getId()));
        commTx.setOrderId(orderId);
        commTx.setTransactionType("COMMISSION_DEDUCTION");
        commTx.setGrossAmount(totalAmount);
        commTx.setCommissionAmount(commission);
        commTx.setAmount(-commission);
        commTx.setBalanceBefore(balanceBefore);
        commTx.setBalanceAfter(balanceAfter);
        commTx.setStatus("SUCCESS");
        commTx.setDescription("5% Platform Commission Cut on Ride Completion");
        commTx.setCreatedAt(LocalDateTime.now());
        WalletTransaction savedTx = walletTransactionRepository.save(commTx);

        // Check if Driver Balance < Minimum Required and Auto-Offline Trigger
        Map<String, Object> settings = getAdminWalletSettings();
        boolean autoOffline = Boolean.TRUE.equals(settings.get("autoOfflineWhenBalanceInsufficient"));
        double minRequired = getMinRequiredBalance();

        if (autoOffline && balanceAfter < minRequired) {
            try {
                driver.setStatus("offline");
                driverRepository.save(driver);
            } catch (Exception ignored) {}
        }

        return savedTx;
    }

    /**
     * Processes order commission deduction on completion.
     */
    @Transactional
    public void processOrderEarning(String driverId, String orderId, double grossAmount) {
        deductCommissionOnCompletion(driverId, orderId, grossAmount);
    }

    /**
     * Cleans up any legacy duplicate COMMISSION / double-deduction transactions and
     * recalculates driver wallet balances accurately.
     */
    @Transactional
    public Map<String, Object> cleanDuplicateCommissionTransactions() {
        List<WalletTransaction> allTx = walletTransactionRepository.findAll();
        int deletedCount = 0;
        java.util.Set<String> affectedDriverIds = new java.util.HashSet<>();

        // Group by driverId and orderId
        Map<String, List<WalletTransaction>> byDriverAndOrder = new java.util.HashMap<>();
        for (WalletTransaction tx : allTx) {
            if (tx.getOrderId() != null && !tx.getOrderId().isBlank()) {
                String key = tx.getDriverId() + "::" + tx.getOrderId();
                byDriverAndOrder.computeIfAbsent(key, k -> new java.util.ArrayList<>()).add(tx);
            }
        }

        for (Map.Entry<String, List<WalletTransaction>> entry : byDriverAndOrder.entrySet()) {
            List<WalletTransaction> txs = entry.getValue();
            List<WalletTransaction> commDeductions = txs.stream()
                    .filter(t -> "COMMISSION_DEDUCTION".equalsIgnoreCase(t.getTransactionType()))
                    .collect(java.util.stream.Collectors.toList());
            List<WalletTransaction> legacyCommissions = txs.stream()
                    .filter(t -> "COMMISSION".equalsIgnoreCase(t.getTransactionType()))
                    .collect(java.util.stream.Collectors.toList());

            double amountToRestore = 0.0;

            // 1. If COMMISSION_DEDUCTION exists, delete all legacy COMMISSION rows for this order and restore deducted amount
            if (!commDeductions.isEmpty() && !legacyCommissions.isEmpty()) {
                for (WalletTransaction legacy : legacyCommissions) {
                    double amt = legacy.getAmount() != null ? Math.abs(legacy.getAmount()) : (legacy.getCommissionAmount() != null ? legacy.getCommissionAmount() : 0.0);
                    amountToRestore += amt;
                    walletTransactionRepository.delete(legacy);
                    deletedCount++;
                    affectedDriverIds.add(legacy.getDriverId());
                }
            } else if (legacyCommissions.size() > 1) {
                // If multiple legacy COMMISSION rows exist, keep only one and convert to COMMISSION_DEDUCTION
                WalletTransaction keep = legacyCommissions.get(0);
                keep.setTransactionType("COMMISSION_DEDUCTION");
                keep.setDescription("5% Platform Commission Cut on Ride Completion");
                walletTransactionRepository.save(keep);
                for (int i = 1; i < legacyCommissions.size(); i++) {
                    WalletTransaction extra = legacyCommissions.get(i);
                    double amt = extra.getAmount() != null ? Math.abs(extra.getAmount()) : (extra.getCommissionAmount() != null ? extra.getCommissionAmount() : 0.0);
                    amountToRestore += amt;
                    walletTransactionRepository.delete(extra);
                    deletedCount++;
                    affectedDriverIds.add(extra.getDriverId());
                }
            } else if (legacyCommissions.size() == 1 && commDeductions.isEmpty()) {
                // Normalize type to COMMISSION_DEDUCTION
                WalletTransaction legacy = legacyCommissions.get(0);
                legacy.setTransactionType("COMMISSION_DEDUCTION");
                legacy.setDescription("5% Platform Commission Cut on Ride Completion");
                walletTransactionRepository.save(legacy);
            }

            // 2. If multiple duplicate COMMISSION_DEDUCTION rows exist for the same order, keep only 1
            if (commDeductions.size() > 1) {
                for (int i = 1; i < commDeductions.size(); i++) {
                    WalletTransaction extra = commDeductions.get(i);
                    double amt = extra.getAmount() != null ? Math.abs(extra.getAmount()) : (extra.getCommissionAmount() != null ? extra.getCommissionAmount() : 0.0);
                    amountToRestore += amt;
                    walletTransactionRepository.delete(extra);
                    deletedCount++;
                    affectedDriverIds.add(extra.getDriverId());
                }
            }

            if (amountToRestore > 0 && !txs.isEmpty()) {
                String dId = txs.get(0).getDriverId();
                Driver driver = findDriverEntity(dId);
                if (driver != null) {
                    double currentBal = driver.getWalletBalance() != null ? driver.getWalletBalance() : 0.0;
                    double fixedBal = Math.round((currentBal + amountToRestore) * 100.0) / 100.0;
                    driver.setWalletBalance(fixedBal);
                    driverRepository.save(driver);

                    DriverWallet wallet = getWallet(String.valueOf(driver.getId()));
                    wallet.setAvailableBalance(fixedBal);
                    wallet.setPlatformCommission(Math.max(0.00, Math.round((wallet.getPlatformCommission() - amountToRestore) * 100.0) / 100.0));
                    driverWalletRepository.save(wallet);
                }
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("deletedDuplicatesCount", deletedCount);
        result.put("affectedDriversCount", affectedDriverIds.size());
        result.put("affectedDriverIds", affectedDriverIds);
        result.put("message", "Duplicate commission records cleaned up and wallet balances restored.");
        return result;
    }

    /**
     * Recalculates driver wallet balance from transaction history.
     */
    @Transactional
    public double recalculateDriverWalletBalance(String driverIdStr) {
        Driver driver = findDriverEntity(driverIdStr);
        if (driver == null) return 0.0;

        List<WalletTransaction> txs = walletTransactionRepository.findByDriverIdOrderByCreatedAtDesc(String.valueOf(driver.getId()));

        double totalRecharges = 0.0;
        double totalCommissions = 0.0;
        double totalWithdrawals = 0.0;
        double totalRefunds = 0.0;
        double totalEarned = 0.0;

        for (WalletTransaction tx : txs) {
            String type = tx.getTransactionType() != null ? tx.getTransactionType().toUpperCase() : "";
            double amt = tx.getAmount() != null ? Math.abs(tx.getAmount()) : 0.0;
            if ("RECHARGE".equals(type)) {
                totalRecharges += amt;
            } else if ("COMMISSION_DEDUCTION".equals(type) || "COMMISSION".equals(type)) {
                totalCommissions += amt;
                if (tx.getGrossAmount() != null) totalEarned += tx.getGrossAmount();
            } else if ("WITHDRAWAL".equals(type)) {
                totalWithdrawals += amt;
            } else if ("REFUND".equals(type)) {
                totalRefunds += amt;
            }
        }

        double calculatedBalance = Math.max(0.00, Math.round((totalRecharges + totalRefunds - totalCommissions - totalWithdrawals) * 100.0) / 100.0);

        driver.setWalletBalance(calculatedBalance);
        driverRepository.save(driver);

        DriverWallet wallet = getWallet(String.valueOf(driver.getId()));
        wallet.setAvailableBalance(calculatedBalance);
        wallet.setPlatformCommission(Math.round(totalCommissions * 100.0) / 100.0);
        wallet.setTotalEarned(Math.round(totalEarned * 100.0) / 100.0);
        wallet.setTotalWithdrawn(Math.round(totalWithdrawals * 100.0) / 100.0);
        driverWalletRepository.save(wallet);

        return calculatedBalance;
    }

    /**
     * Driver requests a withdrawal
     */
    @Transactional
    public WithdrawalRequest requestWithdrawal(String driverId, double amount, String bankAccountId) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Withdrawal amount must be greater than 0");
        }

        DriverWallet wallet = getWallet(driverId);
        if (wallet.getAvailableBalance() < amount) {
            throw new IllegalArgumentException("Insufficient available balance");
        }

        // Check if there's already a pending request
        List<WithdrawalRequest> pendingRequests = withdrawalRequestRepository.findByDriverIdAndStatus(driverId, "PENDING_ADMIN_APPROVAL");
        if (!pendingRequests.isEmpty()) {
            throw new IllegalStateException("You already have a pending withdrawal request");
        }

        // Deduct from available, add to pending
        wallet.setAvailableBalance(wallet.getAvailableBalance() - amount);
        wallet.setPendingBalance(wallet.getPendingBalance() + amount);
        driverWalletRepository.save(wallet);

        WithdrawalRequest request = new WithdrawalRequest();
        request.setDriverId(driverId);
        request.setBankAccountId(bankAccountId);
        request.setAmount(amount);
        request.setHeldAmount(amount);
        request.setStatus("PENDING_ADMIN_APPROVAL");
        
        return withdrawalRequestRepository.save(request);
    }

    /**
     * Admin approves a withdrawal request
     */
    @Transactional
    public WithdrawalRequest approveWithdrawal(String requestId, String payoutProvider, String payoutReference) {
        WithdrawalRequest request = withdrawalRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Withdrawal request not found"));

        if (!"PENDING_ADMIN_APPROVAL".equals(request.getStatus()) && !"ADMIN_APPROVED".equals(request.getStatus()) && !"PROCESSING".equals(request.getStatus())) {
            throw new IllegalStateException("Cannot approve a request in state: " + request.getStatus());
        }

        request.setStatus("COMPLETED");
        request.setPayoutProvider(payoutProvider);
        request.setPayoutReference(payoutReference);
        request.setProcessedAt(LocalDateTime.now());
        withdrawalRequestRepository.save(request);

        // Update Wallet Metrics
        DriverWallet wallet = getWallet(request.getDriverId());
        wallet.setPendingBalance(wallet.getPendingBalance() - request.getHeldAmount());
        wallet.setTotalWithdrawn(wallet.getTotalWithdrawn() + request.getHeldAmount());
        driverWalletRepository.save(wallet);

        // Record Transaction
        WalletTransaction tx = new WalletTransaction();
        tx.setDriverId(request.getDriverId());
        tx.setTransactionType("WITHDRAWAL");
        tx.setAmount(-request.getHeldAmount());
        tx.setBalanceBefore(wallet.getAvailableBalance()); 
        tx.setBalanceAfter(wallet.getAvailableBalance()); // Doesn't change available balance (already deducted)
        tx.setStatus("SUCCESS");
        tx.setReferenceId(payoutReference);
        tx.setDescription("Withdrawal completed. Ref: " + payoutReference);
        walletTransactionRepository.save(tx);

        return request;
    }

    /**
     * Admin rejects a withdrawal request, returning funds to available balance
     */
    @Transactional
    public WithdrawalRequest rejectWithdrawal(String requestId, String reason) {
        WithdrawalRequest request = withdrawalRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Withdrawal request not found"));

        if (!"PENDING_ADMIN_APPROVAL".equals(request.getStatus())) {
            throw new IllegalStateException("Cannot reject a request in state: " + request.getStatus());
        }

        request.setStatus("REJECTED");
        request.setRejectionReason(reason);
        request.setProcessedAt(LocalDateTime.now());
        withdrawalRequestRepository.save(request);

        // Return funds to available balance
        DriverWallet wallet = getWallet(request.getDriverId());
        wallet.setAvailableBalance(wallet.getAvailableBalance() + request.getHeldAmount());
        wallet.setPendingBalance(wallet.getPendingBalance() - request.getHeldAmount());
        driverWalletRepository.save(wallet);

        // Record Transaction for Refund
        WalletTransaction tx = new WalletTransaction();
        tx.setDriverId(request.getDriverId());
        tx.setTransactionType("REFUND");
        tx.setAmount(request.getHeldAmount());
        tx.setBalanceBefore(wallet.getAvailableBalance() - request.getHeldAmount());
        tx.setBalanceAfter(wallet.getAvailableBalance());
        tx.setStatus("SUCCESS");
        tx.setDescription("Withdrawal rejected. Funds returned. Reason: " + reason);
        walletTransactionRepository.save(tx);

        return request;
    }
}
