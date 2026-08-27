package com.anushaporter.backend.repository;

import com.anushaporter.backend.model.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findByUserEmailOrderByCreatedAtDesc(String userEmail);
    List<Order> findByUserEmailAndStatusOrderByCreatedAtDesc(String userEmail, String status);
    Optional<Order> findByBookingId(String bookingId);
    List<Order> findAllByDriverEmailOrderByCreatedAtDesc(String driverEmail);
    List<Order> findAllByDriverEmailAndStatusInOrderByCreatedAtDesc(String driverEmail, List<String> statusList);
    List<Order> findAllByDriverIdAndStatusIn(String driverId, List<String> statusList);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("UPDATE Order o SET o.driverId = :driverId, o.driverName = :driverName, o.driverEmail = :driverEmail, o.driverPhone = :driverPhone, o.driverVehicleNumber = :driverVehicleNumber, o.status = :targetStatus, o.acceptedAt = :acceptedAt WHERE o.bookingId = :bookingId AND (UPPER(o.status) IN ('SEARCHING', 'PENDING', 'CREATED', 'BROADCASTED', 'UNASSIGNED', 'PLACED', 'AVAILABLE') OR o.status IS NULL)")
    int atomicAssignDriverToBooking(
            @Param("bookingId") String bookingId,
            @Param("driverId") String driverId,
            @Param("driverName") String driverName,
            @Param("driverEmail") String driverEmail,
            @Param("driverPhone") String driverPhone,
            @Param("driverVehicleNumber") String driverVehicleNumber,
            @Param("targetStatus") String targetStatus,
            @Param("acceptedAt") LocalDateTime acceptedAt
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("UPDATE Order o SET o.driverId = :driverId, o.driverName = :driverName, o.driverEmail = :driverEmail, o.driverPhone = :driverPhone, o.driverVehicleNumber = :driverVehicleNumber, o.status = 'accepted', o.acceptedAt = :acceptedAt WHERE o.id = :id AND (UPPER(o.status) IN ('SEARCHING', 'PENDING', 'CREATED', 'BROADCASTED', 'UNASSIGNED', 'PLACED', 'AVAILABLE') OR o.status IS NULL)")
    int claimOrderByIdAtomic(
            @Param("id") Long id,
            @Param("driverId") String driverId,
            @Param("driverName") String driverName,
            @Param("driverEmail") String driverEmail,
            @Param("driverPhone") String driverPhone,
            @Param("driverVehicleNumber") String driverVehicleNumber,
            @Param("acceptedAt") LocalDateTime acceptedAt
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("UPDATE Order o SET o.driverId = :driverId, o.driverName = :driverName, o.driverEmail = :driverEmail, o.driverPhone = :driverPhone, o.driverVehicleNumber = :driverVehicleNumber, o.status = 'accepted', o.acceptedAt = :acceptedAt WHERE o.bookingId = :bookingId AND (UPPER(o.status) IN ('SEARCHING', 'PENDING', 'CREATED', 'BROADCASTED', 'UNASSIGNED', 'PLACED', 'AVAILABLE') OR o.status IS NULL)")
    int claimOrderByBookingIdAtomic(
            @Param("bookingId") String bookingId,
            @Param("driverId") String driverId,
            @Param("driverName") String driverName,
            @Param("driverEmail") String driverEmail,
            @Param("driverPhone") String driverPhone,
            @Param("driverVehicleNumber") String driverVehicleNumber,
            @Param("acceptedAt") LocalDateTime acceptedAt
    );

    /**
     * Atomically transitions an order to OTP_VERIFIED status.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("UPDATE Order o SET o.status = 'OTP_VERIFIED' WHERE o.id = :id AND o.status NOT IN ('OTP_VERIFIED', 'PAYMENT_CONFIRMATION_PENDING', 'completed', 'delivered', 'cancelled')")
    int markOtpVerifiedById(@Param("id") Long id);

    /** Looks up an order by idempotency key to short-circuit duplicate completion requests. */
    Optional<Order> findByIdempotencyKey(String idempotencyKey);
}
