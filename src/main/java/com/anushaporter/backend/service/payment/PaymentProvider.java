package com.anushaporter.backend.service.payment;

import com.anushaporter.backend.model.DriverPayoutAccount;
import com.anushaporter.backend.model.DriverPayoutRecord;
import com.anushaporter.backend.model.PaymentOrder;
import com.anushaporter.backend.model.PaymentRefund;

import java.util.Map;

public interface PaymentProvider {
    /**
     * Creates an order with the payment gateway / generates dynamic UPI QR payload
     */
    Map<String, Object> createPaymentOrder(PaymentOrder order);

    /**
     * Generates a dynamic UPI URI and QR data string
     */
    String generateDynamicUpiQrData(PaymentOrder order);

    /**
     * Verifies gateway webhook signature
     */
    boolean verifyWebhookSignature(String payload, String signature);

    /**
     * Queries gateway for actual server-side payment status
     */
    Map<String, Object> fetchPaymentStatus(String gatewayPaymentId);

    /**
     * Executes a refund with the gateway
     */
    Map<String, Object> processRefund(PaymentRefund refund, PaymentOrder order);

    /**
     * Initiates a driver payout transfer to bank or UPI
     */
    Map<String, Object> initiatePayout(DriverPayoutRecord payout, DriverPayoutAccount account);

    /**
     * Fetches live status and UTR of a payout transfer
     */
    Map<String, Object> fetchPayoutStatus(String gatewayPayoutId);

    /**
     * Verifies payment signature (e.g. Razorpay HMAC SHA256 of orderId + "|" + paymentId)
     */
    boolean verifyPaymentSignature(String orderId, String paymentId, String signature);

    /**
     * Gets the configured Gateway Key ID (e.g. Razorpay Key ID)
     */
    String getKeyId();

    /**
     * Gets the configured Gateway Key Secret
     */
    String getKeySecret();
}
