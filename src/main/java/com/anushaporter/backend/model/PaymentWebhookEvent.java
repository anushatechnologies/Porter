package com.anushaporter.backend.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "payment_webhook_events", indexes = {
    @Index(name = "idx_webhook_event_id", columnList = "eventId", unique = true),
    @Index(name = "idx_webhook_processed", columnList = "processed")
})
public class PaymentWebhookEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 128)
    private String eventId;

    @Column(length = 32)
    private String gateway = "sandbox";

    @Column(length = 64)
    private String eventType;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String payloadRaw;

    @Column(length = 256)
    private String signature;

    private Boolean processed = false;

    @Column(length = 1000)
    private String processingError;

    private LocalDateTime receivedAt;
    private LocalDateTime processedAt;

    @PrePersist
    public void prePersist() {
        if (receivedAt == null) receivedAt = LocalDateTime.now();
        if (processed == null) processed = false;
    }

    // Getters and Setters

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }

    public String getGateway() { return gateway; }
    public void setGateway(String gateway) { this.gateway = gateway; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public String getPayloadRaw() { return payloadRaw; }
    public void setPayloadRaw(String payloadRaw) { this.payloadRaw = payloadRaw; }

    public String getSignature() { return signature; }
    public void setSignature(String signature) { this.signature = signature; }

    public Boolean getProcessed() { return processed; }
    public void setProcessed(Boolean processed) { this.processed = processed; }

    public String getProcessingError() { return processingError; }
    public void setProcessingError(String processingError) { this.processingError = processingError; }

    public LocalDateTime getReceivedAt() { return receivedAt; }
    public void setReceivedAt(LocalDateTime receivedAt) { this.receivedAt = receivedAt; }

    public LocalDateTime getProcessedAt() { return processedAt; }
    public void setProcessedAt(LocalDateTime processedAt) { this.processedAt = processedAt; }
}
