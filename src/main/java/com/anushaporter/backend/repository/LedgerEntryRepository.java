package com.anushaporter.backend.repository;

import com.anushaporter.backend.model.LedgerEntry;
import com.anushaporter.backend.model.LedgerType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {
    Optional<LedgerEntry> findByEntryNumber(String entryNumber);
    List<LedgerEntry> findByBookingId(String bookingId);
    List<LedgerEntry> findByPaymentId(String paymentId);
    List<LedgerEntry> findByDriverIdOrderByCreatedAtDesc(String driverId);
    List<LedgerEntry> findByTypeOrderByCreatedAtDesc(LedgerType type);
    List<LedgerEntry> findAllByOrderByCreatedAtDesc();

    @Query("SELECT COALESCE(SUM(l.amount), 0.0) FROM LedgerEntry l WHERE l.driverId = :driverId AND l.type = 'DRIVER_EARNING' AND l.entryType = 'CREDIT'")
    Double sumDriverGrossCredits(String driverId);

    @Query("SELECT COALESCE(SUM(l.amount), 0.0) FROM LedgerEntry l WHERE l.driverId = :driverId AND l.type = 'PAYOUT' AND l.entryType = 'DEBIT'")
    Double sumDriverPayoutDebits(String driverId);

    @Query("SELECT COALESCE(SUM(l.amount), 0.0) FROM LedgerEntry l WHERE l.type = 'PLATFORM_COMMISSION' AND l.entryType = 'CREDIT'")
    Double sumTotalPlatformCommission();

    @Query("SELECT COALESCE(SUM(l.amount), 0.0) FROM LedgerEntry l WHERE l.type = 'PAYMENT_RECEIVED' AND l.entryType = 'CREDIT'")
    Double sumTotalGrossRevenue();
}
