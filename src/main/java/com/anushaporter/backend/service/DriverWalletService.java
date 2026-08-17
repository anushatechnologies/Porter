package com.anushaporter.backend.service;

import com.anushaporter.backend.model.DriverWallet;
import com.anushaporter.backend.model.WalletTransaction;
import com.anushaporter.backend.model.WithdrawalRequest;
import com.anushaporter.backend.repository.DriverWalletRepository;
import com.anushaporter.backend.repository.WalletTransactionRepository;
import com.anushaporter.backend.repository.WithdrawalRequestRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class DriverWalletService {

    @Autowired
    private DriverWalletRepository driverWalletRepository;

    @Autowired
    private WalletTransactionRepository walletTransactionRepository;

    @Autowired
    private WithdrawalRequestRepository withdrawalRequestRepository;

    private static final double COMMISSION_RATE = 0.05; // 5% Platform Commission

    public DriverWallet getWallet(String driverId) {
        return driverWalletRepository.findByDriverId(driverId).orElseGet(() -> {
            DriverWallet newWallet = new DriverWallet();
            newWallet.setDriverId(driverId);
            return driverWalletRepository.save(newWallet);
        });
    }

    /**
     * Processes an order earning, deducting the 5% platform commission and crediting the 95% to the driver.
     */
    @Transactional
    public void processOrderEarning(String driverId, String orderId, double grossAmount) {
        if (grossAmount <= 0) return;

        DriverWallet wallet = getWallet(driverId);
        double platformCommission = Math.round(grossAmount * COMMISSION_RATE * 100.0) / 100.0;
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
        commissionTx.setDescription("Platform commission (5%) for order " + orderId);
        walletTransactionRepository.save(commissionTx);
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
