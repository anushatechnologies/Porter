package com.anushaporter.backend.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "payment_refunds", indexes = {
    @Index(name = "idx_refund_id", columnList = "refundId", unique = true),
    @Index(name = "idx_refund_payment", columnList = "paymentId"),
    @Index(name = "idx_refund_booking", columnList = "bookingId")
})
public class PaymentRefund {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 64)
    private String refundId;

    @Column(nullable = false, length = 64)
    private String paymentId;

    @Column(nullable = false, length = 64)
    private String bookingId;

    @Column(nullable = false)
    private Double amount;

    private String currency = "INR";

    @Column(length = 500)
    private String reason;

    @Column(length = 32)
    private String refundType = "FULL_REFUND"; // "FULL_REFUND", "PARTIAL_REFUND"

    @Column(length = 32)
    private String status = "SUCCESS"; // "PENDING", "SUCCESS", "FAILED"

    @Column(length = 128)
    private String gatewayRefundId;

    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (currency == null) currency = "INR";
        if (status == null) status = "SUCCESS";
    }

    // Getters and Setters

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getRefundId() { return refundId; }
    public void setRefundId(String refundId) { this.refundId = refundId; }

    public String getPaymentId() { return paymentId; }
    public void setPaymentId(String paymentId) { this.paymentId = paymentId; }

    public String getBookingId() { return bookingId; }
    public void setBookingId(String bookingId) { this.bookingId = bookingId; }

    public Double getAmount() { return amount; }
    public void setAmount(Double amount) { this.amount = amount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getRefundType() { return refundType; }
    public void setRefundType(String refundType) { this.refundType = refundType; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getGatewayRefundId() { return gatewayRefundId; }
    public void setGatewayRefundId(String gatewayRefundId) { this.gatewayRefundId = gatewayRefundId; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
