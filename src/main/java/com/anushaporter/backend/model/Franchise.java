package com.anushaporter.backend.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Data
@Table(name = "franchises")
public class Franchise {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String city;
    private String head;
    @Column(length = 500)
    private String address;
    private Integer driversCount;
    private Integer dailyOrders;
    private String revenue;
    private String status; // "Active", "Inactive"

    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (status == null) status = "Active";
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getHead() { return head; }
    public void setHead(String head) { this.head = head; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public Integer getDriversCount() { return driversCount; }
    public void setDriversCount(Integer driversCount) { this.driversCount = driversCount; }
    public Integer getDailyOrders() { return dailyOrders; }
    public void setDailyOrders(Integer dailyOrders) { this.dailyOrders = dailyOrders; }
    public String getRevenue() { return revenue; }
    public void setRevenue(String revenue) { this.revenue = revenue; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
