package com.anushaporter.backend.repository;

import com.anushaporter.backend.model.PaymentRefund;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRefundRepository extends JpaRepository<PaymentRefund, Long> {
    Optional<PaymentRefund> findByRefundId(String refundId);
    List<PaymentRefund> findByPaymentId(String paymentId);
    List<PaymentRefund> findByBookingId(String bookingId);
}
