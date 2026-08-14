package com.anushaporter.backend.service.payment;

import com.anushaporter.backend.model.*;
import com.anushaporter.backend.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

@Service
public class DriverPayoutService {

    @Autowired
    private DriverPayoutAccountRepository accountRepository;

    @Autowired
    private DriverPayoutRecordRepository payoutRecordRepository;

    @Autowired
    private DriverEarningsRepository driverEarningsRepository;

    @Autowired
    private DriverRepository driverRepository;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private FinancialLedgerService ledgerService;

    @Autowired
    private PaymentProvider paymentProvider;

    public Optional<DriverPayoutAccount> getPayoutAccount(String driverId) {
        return accountRepository.findByDriverId(driverId);
    }

    @Transactional
    public DriverPayoutAccount savePayoutAccount(
            String driverId,
            String accountHolderName,
            String bankName,
            String accountNumber,
            String ifscCode,
            String upiId
    ) {
        DriverPayoutAccount account = accountRepository.findByDriverId(driverId)
                .orElse(new DriverPayoutAccount());

        account.setDriverId(driverId);
        if (accountHolderName != null) account.setAccountHolderName(accountHolderName);
        if (bankName != null) account.setBankName(bankName);
        if (accountNumber != null) account.setAccountNumber(accountNumber);
        if (ifscCode != null) account.setIfscCode(ifscCode.toUpperCase());
        if (upiId != null) account.setUpiId(upiId);
        account.setVerificationStatus("VERIFIED");

        return accountRepository.save(account);
    }

    public Map<String, Object> getDriverEarningsSummary(String driverId) {
        LocalDateTime startOfDay = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);

        Long todayTrips = driverEarningsRepository.countTripsSinceByDriverId(driverId, startOfDay);
        Double todayNet = driverEarningsRepository.sumEarningsSinceByDriverId(driverId, startOfDay);
        Double todayFee = driverEarningsRepository.sumPlatformCommissionSinceByDriverId(driverId, startOfDay);
        Double todayGross = (todayNet != null ? todayNet : 0.0) + (todayFee != null ? todayFee : 0.0);

        Double totalNetEarnings = driverEarningsRepository.sumTotalNetEarningsByDriverId(driverId);
        Double paidOut = payoutRecordRepository.sumPaidOutByDriverId(driverId);
        Double processing = payoutRecordRepository.sumProcessingPayoutsByDriverId(driverId);

        double totalNet = totalNetEarnings != null ? totalNetEarnings : 0.0;
        double paid = paidOut != null ? paidOut : 0.0;
        double proc = processing != null ? processing : 0.0;
        double available = Math.max(0.0, totalNet - paid - proc);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("driverId", driverId);
        summary.put("todayTrips", todayTrips != null ? todayTrips : 0L);
        summary.put("todayGrossEarnings", Math.round(todayGross * 100.0) / 100.0);
        summary.put("todayPlatformFee", Math.round((todayFee != null ? todayFee : 0.0) * 100.0) / 100.0);
        summary.put("todayNetEarnings", Math.round((todayNet != null ? todayNet : 0.0) * 100.0) / 100.0);
        summary.put("totalNetEarnings", Math.round(totalNet * 100.0) / 100.0);
        summary.put("availableBalance", Math.round(available * 100.0) / 100.0);
        summary.put("pendingBalance", Math.round(proc * 100.0) / 100.0);
        summary.put("paidBalance", Math.round(paid * 100.0) / 100.0);
        return summary;
    }

    public List<DriverEarnings> getDriverEarningsHistory(String driverId) {
        return driverEarningsRepository.findByDriverIdOrderByCreatedAtDesc(driverId);
    }

    public List<DriverPayoutRecord> getDriverPayouts(String driverId) {
        return payoutRecordRepository.findByDriverIdOrderByRequestedAtDesc(driverId);
    }

    @Transactional
    public DriverPayoutRecord requestPayout(String driverId, Double requestedAmount, String payoutMode, String idempotencyKey) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<DriverPayoutRecord> existing = payoutRecordRepository.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                return existing.get();
            }
        }

        Map<String, Object> summary = getDriverEarningsSummary(driverId);
        double available = (Double) summary.get("availableBalance");

        double amount = requestedAmount != null && requestedAmount > 0 ? requestedAmount : available;
        if (amount <= 0) {
            throw new IllegalArgumentException("Payout amount must be greater than zero.");
        }
        if (amount > available) {
            throw new IllegalArgumentException("Insufficient available balance. Requested: ₹" + amount + ", Available: ₹" + available);
        }

        DriverPayoutAccount account = accountRepository.findByDriverId(driverId)
                .orElseThrow(() -> new IllegalStateException("No verified bank/UPI payout account registered. Please add bank details first."));

        String payoutId = "PO_" + LocalDateTime.now().getYear() + "_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        DriverPayoutRecord payout = new DriverPayoutRecord();
        payout.setPayoutId(payoutId);
        payout.setDriverId(driverId);
        payout.setAmount(amount);
        payout.setPayoutMode(payoutMode != null ? payoutMode : "MANUAL");
        payout.setIdempotencyKey(idempotencyKey != null ? idempotencyKey : payoutId);

        if (account.getUpiId() != null && !account.getUpiId().isBlank() && "UPI".equalsIgnoreCase(payoutMode)) {
            payout.setDestinationType("UPI");
            payout.setDestinationMasked(account.getUpiId());
        } else {
            payout.setDestinationType("BANK_ACCOUNT");
            payout.setDestinationMasked(account.getBankName() != null ? account.getBankName() + " " + account.getAccountNumberMasked() : account.getAccountNumberMasked());
        }

        payout.setStatus(PayoutStatus.PROCESSING);
        DriverPayoutRecord savedPayout = payoutRecordRepository.save(payout);

        // Execute Settlement with Gateway/Provider
        Map<String, Object> gatewayRes = paymentProvider.initiatePayout(savedPayout, account);
        if (Boolean.TRUE.equals(gatewayRes.get("success"))) {
            savedPayout.setStatus(PayoutStatus.SUCCESS);
            savedPayout.setGatewayPayoutId((String) gatewayRes.get("gatewayPayoutId"));
            savedPayout.setUtr((String) gatewayRes.get("utr"));
            savedPayout.setSettledAt(LocalDateTime.now());
            payoutRecordRepository.save(savedPayout);

            // Record Payout Debit in Financial Ledger
            ledgerService.recordEntry(
                    null,
                    null,
                    savedPayout.getPayoutId(),
                    driverId,
                    null,
                    LedgerType.PAYOUT,
                    "DEBIT",
                    amount,
                    available - amount,
                    "Driver payout settlement via " + savedPayout.getDestinationMasked() + " (UTR: " + savedPayout.getUtr() + ")",
                    savedPayout.getUtr(),
                    "SYSTEM"
            );
        } else {
            savedPayout.setStatus(PayoutStatus.FAILED);
            savedPayout.setFailureReason("Gateway rejected payout request");
            payoutRecordRepository.save(savedPayout);
        }

        return savedPayout;
    }
}
