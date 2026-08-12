package com.anushaporter.backend.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Data
@Table(name = "tickets")
public class Ticket {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String ticketId;
    private String customer;
    private String driver;
    private String subject;
    private String status; // "open", "resolved", "in_progress"

    @Column(columnDefinition = "TEXT")
    private String customerChatJson; // JSON string of chat messages

    @Column(columnDefinition = "TEXT")
    private String driverChatJson;

    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (status == null) status = "open";
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTicketId() { return ticketId; }
    public void setTicketId(String ticketId) { this.ticketId = ticketId; }
    public String getCustomer() { return customer; }
    public void setCustomer(String customer) { this.customer = customer; }
    public String getDriver() { return driver; }
    public void setDriver(String driver) { this.driver = driver; }
    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getCustomerChatJson() { return customerChatJson; }
    public void setCustomerChatJson(String customerChatJson) { this.customerChatJson = customerChatJson; }
    public String getDriverChatJson() { return driverChatJson; }
    public void setDriverChatJson(String driverChatJson) { this.driverChatJson = driverChatJson; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
