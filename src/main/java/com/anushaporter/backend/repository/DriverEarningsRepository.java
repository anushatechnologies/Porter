package com.anushaporter.backend.repository;

import com.anushaporter.backend.model.DriverEarnings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface DriverEarningsRepository extends JpaRepository<DriverEarnings, Long> {
    Optional<DriverEarnings> findByBookingId(String bookingId);
    Optional<DriverEarnings> findByPaymentId(String paymentId);

    List<DriverEarnings> findByDriverIdOrderByCreatedAtDesc(String driverId);
    List<DriverEarnings> findByDriverIdAndCreatedAtBetweenOrderByCreatedAtDesc(String driverId, LocalDateTime start, LocalDateTime end);
    List<DriverEarnings> findByDriverIdAndSettlementStatus(String driverId, String settlementStatus);

    @Query("SELECT COALESCE(SUM(e.driverNetEarning), 0.0) FROM DriverEarnings e WHERE e.driverId = :driverId AND e.paymentStatus = 'PAID'")
    Double sumTotalNetEarningsByDriverId(String driverId);

    @Query("SELECT COALESCE(SUM(e.driverNetEarning), 0.0) FROM DriverEarnings e WHERE e.driverId = :driverId AND e.createdAt >= :since AND e.paymentStatus = 'PAID'")
    Double sumEarningsSinceByDriverId(String driverId, LocalDateTime since);

    @Query("SELECT COUNT(e) FROM DriverEarnings e WHERE e.driverId = :driverId AND e.createdAt >= :since AND e.paymentStatus = 'PAID'")
    Long countTripsSinceByDriverId(String driverId, LocalDateTime since);

    @Query("SELECT COALESCE(SUM(e.platformCommission), 0.0) FROM DriverEarnings e WHERE e.driverId = :driverId AND e.createdAt >= :since AND e.paymentStatus = 'PAID'")
    Double sumPlatformCommissionSinceByDriverId(String driverId, LocalDateTime since);

    @Query("SELECT COALESCE(SUM(e.driverNetEarning), 0.0) FROM DriverEarnings e WHERE e.driverId = :driverId AND e.settlementStatus = 'PENDING' AND e.paymentStatus = 'PAID'")
    Double sumPendingSettlementByDriverId(String driverId);
}
