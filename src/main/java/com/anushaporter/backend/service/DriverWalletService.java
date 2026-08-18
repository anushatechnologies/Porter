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
     * Assigns order to driver and deducts 5% commission from driver's wallet.
     */
    @Transactional
    public Map<String, Object> assignOrderWithCommission(Order order, Driver driver, Double commissionRate) {
        double rate = commissionRate != null ? commissionRate : (getCommissionPercentage() / 100.0);
        double orderFare = order.getAmount() != null ? order.getAmount() : 500.0;
        double commission = Math.round(orderFare * rate * 100.0) / 100.0;

        double walletBalance = driver.getWalletBalance() != null ? driver.getWalletBalance() : 0.0;

        // Check if wallet_balance <= 0
        if (walletBalance <= 0) {
            throw new IllegalStateException("Driver wallet balance is ₹0. Please recharge to accept orders.");
        }

        double newBalance = Math.round((walletBalance - commission) * 100.0) / 100.0;
        driver.setWalletBalance(newBalance);
        driverRepository.save(driver);

        // Sync DriverWallet
        DriverWallet wallet = getWallet(String.valueOf(driver.getId()));
        wallet.setAvailableBalance(newBalance);
        wallet.setPlatformCommission(wallet.getPlatformCommission() + commission);
        driverWalletRepository.save(wallet);

        String orderIdStr = order.getBookingId() != null ? order.getBookingId() : String.valueOf(order.getId());
        String txId = "TXN_W_" + System.currentTimeMillis();

        // Log in wallet_transactions with type = 'COMMISSION_DEDUCTION'
        WalletTransaction commTx = new WalletTransaction();
        commTx.setId(txId);
        commTx.setDriverId(String.valueOf(driver.getId()));
        commTx.setOrderId(orderIdStr);
        commTx.setTransactionType("COMMISSION_DEDUCTION");
        commTx.setGrossAmount(orderFare);
        commTx.setCommissionAmount(commission);
        commTx.setAmount(-commission);
        commTx.setBalanceBefore(walletBalance);
        commTx.setBalanceAfter(newBalance);
        commTx.setStatus("SUCCESS");
        commTx.setDescription("Ride Commission Deduction (" + (rate * 100) + "%) - Order #" + orderIdStr);
        walletTransactionRepository.save(commTx);

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
        response.put("orderFare", orderFare);
        response.put("commissionDeducted", commission);
        response.put("remainingWalletBalance", newBalance);
        response.put("status", "assigned");
        return response;
    }

    /**
     * Processes an order earning, deducting the platform commission and updating wallet metrics.
     */
    @Transactional
    public void processOrderEarning(String driverId, String orderId, double grossAmount) {
        if (grossAmount <= 0) return;

        DriverWallet wallet = getWallet(driverId);
        double commissionRate = getCommissionPercentage() / 100.0;
        double platformCommission = Math.round(grossAmount * commissionRate * 100.0) / 100.0;
        double netAmount = grossAmount - platformCommission;

        double balanceBefore = wallet.getAvailableBalance();
        double balanceAfter = balanceBefore + netAmount;

        // Update Wallet
        wallet.setAvailableBalance(balanceAfter);
        wallet.setTotalEarned(wallet.getTotalEarned() + grossAmount);
        wallet.setPlatformCommission(wallet.getPlatformCommission() + platformCommission);
        driverWalletRepository.save(wallet);

        // Sync Driver entity
        Driver driver = findDriverEntity(driverId);
        if (driver != null) {
            driver.setWalletBalance(balanceAfter);
            driverRepository.save(driver);
        }

        // Record Transaction: Gross Earning
        String earnTxId = "TXN_W_" + System.currentTimeMillis();
        WalletTransaction earningTx = new WalletTransaction();
        earningTx.setId(earnTxId);
        earningTx.setDriverId(driver != null ? String.valueOf(driver.getId()) : driverId);
        earningTx.setOrderId(orderId);
        earningTx.setTransactionType("ORDER_EARNING");
        earningTx.setGrossAmount(grossAmount);
        earningTx.setCommissionAmount(0.0);
        earningTx.setAmount(grossAmount);
        earningTx.setBalanceBefore(balanceBefore);
        earningTx.setBalanceAfter(balanceBefore + grossAmount); // Temporary intermediate balance
        earningTx.setStatus("SUCCESS");
        earningTx.setDescription("Gross earnings for order " + orderId);
        walletTransactionRepository.save(earningTx);

        // Record Transaction: Commission Deduction
        String commTxId = "TXN_W_" + (System.currentTimeMillis() + 1);
        WalletTransaction commissionTx = new WalletTransaction();
        commissionTx.setId(commTxId);
        commissionTx.setDriverId(driver != null ? String.valueOf(driver.getId()) : driverId);
        commissionTx.setOrderId(orderId);
        commissionTx.setTransactionType("COMMISSION");
        commissionTx.setGrossAmount(grossAmount);
        commissionTx.setCommissionAmount(platformCommission);
        commissionTx.setAmount(-platformCommission);
        commissionTx.setBalanceBefore(balanceBefore + grossAmount);
        commissionTx.setBalanceAfter(balanceAfter);
        commissionTx.setStatus("SUCCESS");
        commissionTx.setDescription(String.format("Ride Commission (%.1f%%) - Order #%s", (commissionRate * 100), orderId));
        walletTransactionRepository.save(commissionTx);

        // Check if Driver Balance < Minimum Required and Auto-Offline Trigger
        Map<String, Object> settings = getAdminWalletSettings();
        boolean autoOffline = Boolean.TRUE.equals(settings.get("autoOfflineWhenBalanceInsufficient"));
        double minRequired = getMinRequiredBalance();

        if (autoOffline && wallet.getAvailableBalance() < minRequired) {
            try {
                if (driver != null) {
                    driver.setStatus("offline");
                    driverRepository.save(driver);
                }
            } catch (Exception ignored) {}
        }
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
