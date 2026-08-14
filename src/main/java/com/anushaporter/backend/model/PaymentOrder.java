package com.anushaporter.backend.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "payment_orders", indexes = {
    @Index(name = "idx_pay_payment_id", columnList = "paymentId", unique = true),
    @Index(name = "idx_pay_booking_id", columnList = "bookingId"),
    @Index(name = "idx_pay_driver_id", columnList = "driverId"),
    @Index(name = "idx_pay_status", columnList = "status"),
    @Index(name = "idx_pay_idempotency", columnList = "idempotencyKey", unique = true)
})
public class PaymentOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 64)
    private String paymentId;

    @Column(nullable = false, length = 64)
    private String bookingId;

    @Column(unique = true, length = 64)
    private String invoiceId;

    @Column(length = 128)
    private String transactionId;

    private String customerId;
    private String customerName;
    private String customerPhone;
    private String customerEmail;

    private String driverId;
    private String driverName;
    private String driverPhone;

    @Column(nullable = false)
    private Double amount;

    private String currency = "INR";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PaymentStatus status = PaymentStatus.CREATED;

    @Column(length = 32)
    private String paymentMethod; // "UPI_QR", "UPI_INTENT", "CARD", "NET_BANKING", "WALLET", "CASH"

    @Column(length = 32)
    private String gateway = "sandbox"; // "sandbox", "razorpay", "cashfree"

    @Column(length = 128)
    private String gatewayOrderId;

    @Column(length = 128)
    private String gatewayPaymentId;

    @Column(length = 1000)
    private String qrCodeData; // dynamic UPI string payload e.g. upi://pay?pa=...

    @Column(length = 1000)
    private String qrImageUrl;

    private LocalDateTime qrExpiresAt;

    @Column(unique = true, length = 128)
    private String idempotencyKey;

    @Column(length = 2000)
    private String fareBreakdownJson;

    @Column(length = 1000)
    private String failureReason;

    private LocalDateTime paidAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (updatedAt == null) updatedAt = LocalDateTime.now();
        if (currency == null) currency = "INR";
        if (status == null) status = PaymentStatus.CREATED;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Getters and Setters

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getPaymentId() { return paymentId; }
    public void setPaymentId(String paymentId) { this.paymentId = paymentId; }

    public String getBookingId() { return bookingId; }
    public void setBookingId(String bookingId) { this.bookingId = bookingId; }

    public String getInvoiceId() { return invoiceId; }
    public void setInvoiceId(String invoiceId) { this.invoiceId = invoiceId; }

    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }

    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }

    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }

    public String getCustomerPhone() { return customerPhone; }
    public void setCustomerPhone(String customerPhone) { this.customerPhone = customerPhone; }

    public String getCustomerEmail() { return customerEmail; }
    public void setCustomerEmail(String customerEmail) { this.customerEmail = customerEmail; }

    public String getDriverId() { return driverId; }
    public void setDriverId(String driverId) { this.driverId = driverId; }

    public String getDriverName() { return driverName; }
    public void setDriverName(String driverName) { this.driverName = driverName; }

    public String getDriverPhone() { return driverPhone; }
    public void setDriverPhone(String driverPhone) { this.driverPhone = driverPhone; }

    public Double getAmount() { return amount; }
    public void setAmount(Double amount) { this.amount = amount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public PaymentStatus getStatus() { return status; }
    public void setStatus(PaymentStatus status) { this.status = status; }

    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }

    public String getGateway() { return gateway; }
    public void setGateway(String gateway) { this.gateway = gateway; }

    public String getGatewayOrderId() { return gatewayOrderId; }
    public void setGatewayOrderId(String gatewayOrderId) { this.gatewayOrderId = gatewayOrderId; }

    public String getGatewayPaymentId() { return gatewayPaymentId; }
    public void setGatewayPaymentId(String gatewayPaymentId) { this.gatewayPaymentId = gatewayPaymentId; }

    public String getQrCodeData() { return qrCodeData; }
    public void setQrCodeData(String qrCodeData) { this.qrCodeData = qrCodeData; }

    public String getQrImageUrl() { return qrImageUrl; }
    public void setQrImageUrl(String qrImageUrl) { this.qrImageUrl = qrImageUrl; }

    public LocalDateTime getQrExpiresAt() { return qrExpiresAt; }
    public void setQrExpiresAt(LocalDateTime qrExpiresAt) { this.qrExpiresAt = qrExpiresAt; }

    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }

    public String getFareBreakdownJson() { return fareBreakdownJson; }
    public void setFareBreakdownJson(String fareBreakdownJson) { this.fareBreakdownJson = fareBreakdownJson; }

    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }

    public LocalDateTime getPaidAt() { return paidAt; }
    public void setPaidAt(LocalDateTime paidAt) { this.paidAt = paidAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
