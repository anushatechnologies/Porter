package com.anushaporter.backend.service;

import com.anushaporter.backend.model.Driver;
import com.anushaporter.backend.model.DriverWallet;
import com.anushaporter.backend.model.GlobalSettings;
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

    public double getMinRequiredBalance() {
        Object val = getAdminWalletSettings().get("minRequiredBalance");
        return val instanceof Number ? ((Number) val).doubleValue() : DEFAULT_MIN_REQUIRED_BALANCE;
    }

    public boolean isDriverEligibleForRides(String driverId) {
        DriverWallet wallet = getWallet(driverId);
        double minRequired = getMinRequiredBalance();
        return wallet.getAvailableBalance() >= minRequired;
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

        // Record Transaction: RECHARGE
        WalletTransaction rechargeTx = new WalletTransaction();
        rechargeTx.setDriverId(driverId);
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

        // Record Transaction: Gross Earning
        WalletTransaction earningTx = new WalletTransaction();
        earningTx.setDriverId(driverId);
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
        WalletTransaction commissionTx = new WalletTransaction();
        commissionTx.setDriverId(driverId);
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
                Long dId = Long.parseLong(driverId.replaceAll("\\D+", ""));
                Optional<Driver> driverOpt = driverRepository.findById(dId);
                if (driverOpt.isPresent()) {
                    Driver d = driverOpt.get();
                    d.setStatus("offline");
                    driverRepository.save(d);
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
