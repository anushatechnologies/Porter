package com.anushaporter.backend.service.payment;

import com.anushaporter.backend.model.LedgerEntry;
import com.anushaporter.backend.model.LedgerType;
import com.anushaporter.backend.repository.LedgerEntryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class FinancialLedgerService {

    @Autowired
    private LedgerEntryRepository ledgerRepository;

    @Transactional(propagation = Propagation.MANDATORY)
    public LedgerEntry recordEntry(
            String bookingId,
            String paymentId,
            String transactionId,
            String driverId,
            String customerId,
            LedgerType type,
            String entryType, // "CREDIT" or "DEBIT"
            Double amount,
            Double balanceAfter,
            String description,
            String referenceId,
            String createdBy
    ) {
        LedgerEntry entry = new LedgerEntry();
        entry.setEntryNumber("LED-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase());
        entry.setBookingId(bookingId);
        entry.setPaymentId(paymentId);
        entry.setTransactionId(transactionId);
        entry.setDriverId(driverId);
        entry.setCustomerId(customerId);
        entry.setType(type);
        entry.setEntryType(entryType);
        entry.setAmount(amount != null ? Math.round(amount * 100.0) / 100.0 : 0.0);
        entry.setBalanceAfter(balanceAfter != null ? Math.round(balanceAfter * 100.0) / 100.0 : null);
        entry.setDescription(description);
        entry.setReferenceId(referenceId);
        entry.setCreatedBy(createdBy != null ? createdBy : "SYSTEM");

        // Compute SHA-256 integrity hash
        entry.setImmutableHash(computeIntegrityHash(entry));

        return ledgerRepository.save(entry);
    }

    private String computeIntegrityHash(LedgerEntry entry) {
        try {
            String raw = String.join("|",
                    entry.getEntryNumber() != null ? entry.getEntryNumber() : "",
                    entry.getBookingId() != null ? entry.getBookingId() : "",
                    entry.getPaymentId() != null ? entry.getPaymentId() : "",
                    entry.getType() != null ? entry.getType().name() : "",
                    entry.getEntryType() != null ? entry.getEntryType() : "",
                    String.valueOf(entry.getAmount()),
                    entry.getCreatedBy() != null ? entry.getCreatedBy() : ""
            );
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedhash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(encodedhash);
        } catch (Exception e) {
            return UUID.randomUUID().toString();
        }
    }
}
