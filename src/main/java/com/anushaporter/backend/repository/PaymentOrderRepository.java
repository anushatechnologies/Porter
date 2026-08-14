package com.anushaporter.backend.repository;

import com.anushaporter.backend.model.PaymentOrder;
import com.anushaporter.backend.model.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentOrderRepository extends JpaRepository<PaymentOrder, Long> {
    Optional<PaymentOrder> findByPaymentId(String paymentId);
    Optional<PaymentOrder> findByBookingId(String bookingId);
    Optional<PaymentOrder> findByInvoiceId(String invoiceId);
    Optional<PaymentOrder> findByIdempotencyKey(String idempotencyKey);
    Optional<PaymentOrder> findByGatewayOrderId(String gatewayOrderId);
    Optional<PaymentOrder> findByGatewayPaymentId(String gatewayPaymentId);

    List<PaymentOrder> findByDriverIdOrderByCreatedAtDesc(String driverId);
    List<PaymentOrder> findByCustomerIdOrderByCreatedAtDesc(String customerId);
    List<PaymentOrder> findByStatusOrderByCreatedAtDesc(PaymentStatus status);
    List<PaymentOrder> findByCreatedAtBetweenOrderByCreatedAtDesc(LocalDateTime start, LocalDateTime end);
    List<PaymentOrder> findAllByOrderByCreatedAtDesc();
}
