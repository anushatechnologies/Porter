package com.anushaporter.backend.repository;

import com.anushaporter.backend.model.DriverPayoutRecord;
import com.anushaporter.backend.model.PayoutStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DriverPayoutRecordRepository extends JpaRepository<DriverPayoutRecord, Long> {
    Optional<DriverPayoutRecord> findByPayoutId(String payoutId);
    Optional<DriverPayoutRecord> findByIdempotencyKey(String idempotencyKey);

    List<DriverPayoutRecord> findByDriverIdOrderByRequestedAtDesc(String driverId);
    List<DriverPayoutRecord> findByStatusOrderByRequestedAtDesc(PayoutStatus status);
    List<DriverPayoutRecord> findAllByOrderByRequestedAtDesc();

    @Query("SELECT COALESCE(SUM(p.amount), 0.0) FROM DriverPayoutRecord p WHERE p.driverId = :driverId AND p.status = 'SUCCESS'")
    Double sumPaidOutByDriverId(String driverId);

    @Query("SELECT COALESCE(SUM(p.amount), 0.0) FROM DriverPayoutRecord p WHERE p.driverId = :driverId AND (p.status = 'PENDING' OR p.status = 'PROCESSING')")
    Double sumProcessingPayoutsByDriverId(String driverId);
}
