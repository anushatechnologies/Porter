package com.anushaporter.backend.model;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Data
@Table(name = "driver_wallets")
public class DriverWallet {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "driver_id", length = 64, unique = true, nullable = false)
    private String driverId;

    @Column(name = "available_balance", nullable = false)
    private Double availableBalance = 0.0;

    @Column(name = "pending_balance", nullable = false)
    private Double pendingBalance = 0.0;

    @Column(name = "total_earned", nullable = false)
    private Double totalEarned = 0.0;

    @Column(name = "total_withdrawn", nullable = false)
    private Double totalWithdrawn = 0.0;

    @Column(name = "platform_commission", nullable = false)
    private Double platformCommission = 0.0;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @PrePersist
    public void generateId() {
        if (this.id == null) {
            this.id = UUID.randomUUID().toString();
        }
    }
}
