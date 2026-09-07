package com.anushaporter.backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "pricing_audit_logs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PricingAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "pricing_version_id", length = 50)
    private String pricingVersionId;

    @Column(name = "entity_type", length = 50)
    private String entityType; // SERVICE, VEHICLE_CATEGORY, PRICING_RULE, SURGE, RENTAL_PACKAGE

    @Column(name = "entity_id")
    private Long entityId;

    @Column(name = "admin_name", length = 100)
    @Builder.Default
    private String adminName = "Admin";

    @Column(name = "admin_email", length = 100)
    private String adminEmail;

    @Column(name = "change_type", length = 50)
    private String changeType; // CREATE, UPDATE, DELETE, PUBLISH

    @Column(name = "field_name", length = 100)
    private String fieldName;

    @Column(name = "old_value", length = 500)
    private String oldValue;

    @Column(name = "new_value", length = 500)
    private String newValue;

    @Column(name = "reason", length = 500)
    private String reason;

    @CreationTimestamp
    @Column(name = "timestamp", updatable = false)
    private LocalDateTime timestamp;
}
