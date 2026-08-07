package com.anushaporter.backend.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Data
@Table(name = "orders")
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String bookingId;

    private String userEmail;
    private String serviceName;

    @Column(length = 500)
    private String pickupAddress;

    @Column(length = 500)
    private String dropAddress;

    private Double pickupLat;
    private Double pickupLng;
    private Double dropLat;
    private Double dropLng;

    private Double amount;
    private String currency;
    private String status;
    private String paymentMethod;

    private String scheduledDate;
    private String scheduledSlot;

    private String receiverName;
    private String receiverPhone;

    private String driverName;
    private String driverPhone;
    private String driverEmail;
    private String driverId;
    private String driverVehicleNumber;

    private Double distanceKm;

    private String houseSize;
    private String heavyItems;
    private String loadAssist;

    @Column(length = 10)
    private String deliveryOtp;
    private LocalDateTime otpExpiresAt;
    @Column(length = 500)
    private String cancellationReason;

    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (currency == null) {
            currency = "INR";
        }
    }
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getBookingId() { return bookingId; }
    public void setBookingId(String bookingId) { this.bookingId = bookingId; }
    public String getUserEmail() { return userEmail; }
    public void setUserEmail(String userEmail) { this.userEmail = userEmail; }
    public String getServiceName() { return serviceName; }
    public void setServiceName(String serviceName) { this.serviceName = serviceName; }
    public String getPickupAddress() { return pickupAddress; }
    public void setPickupAddress(String pickupAddress) { this.pickupAddress = pickupAddress; }
    public String getDropAddress() { return dropAddress; }
    public void setDropAddress(String dropAddress) { this.dropAddress = dropAddress; }
    public Double getPickupLat() { return pickupLat; }
    public void setPickupLat(Double pickupLat) { this.pickupLat = pickupLat; }
    public Double getPickupLng() { return pickupLng; }
    public void setPickupLng(Double pickupLng) { this.pickupLng = pickupLng; }
    public Double getDropLat() { return dropLat; }
    public void setDropLat(Double dropLat) { this.dropLat = dropLat; }
    public Double getDropLng() { return dropLng; }
    public void setDropLng(Double dropLng) { this.dropLng = dropLng; }
    public Double getAmount() { return amount; }
    public void setAmount(Double amount) { this.amount = amount; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }
    public String getScheduledDate() { return scheduledDate; }
    public void setScheduledDate(String scheduledDate) { this.scheduledDate = scheduledDate; }
    public String getScheduledSlot() { return scheduledSlot; }
    public void setScheduledSlot(String scheduledSlot) { this.scheduledSlot = scheduledSlot; }
    public String getReceiverName() { return receiverName; }
    public void setReceiverName(String receiverName) { this.receiverName = receiverName; }
    public String getReceiverPhone() { return receiverPhone; }
    public void setReceiverPhone(String receiverPhone) { this.receiverPhone = receiverPhone; }
    public String getDriverName() { return driverName; }
    public void setDriverName(String driverName) { this.driverName = driverName; }
    public String getDriverPhone() { return driverPhone; }
    public void setDriverPhone(String driverPhone) { this.driverPhone = driverPhone; }
    public String getDriverEmail() { return driverEmail; }
    public void setDriverEmail(String driverEmail) { this.driverEmail = driverEmail; }
    public String getDriverId() { return driverId; }
    public void setDriverId(String driverId) { this.driverId = driverId; }
    public String getDriverVehicleNumber() { return driverVehicleNumber; }
    public void setDriverVehicleNumber(String driverVehicleNumber) { this.driverVehicleNumber = driverVehicleNumber; }
    public Double getDistanceKm() { return distanceKm; }
    public void setDistanceKm(Double distanceKm) { this.distanceKm = distanceKm; }
    public String getHouseSize() { return houseSize; }
    public void setHouseSize(String houseSize) { this.houseSize = houseSize; }
    public String getHeavyItems() { return heavyItems; }
    public void setHeavyItems(String heavyItems) { this.heavyItems = heavyItems; }
    public String getLoadAssist() { return loadAssist; }
    public void setLoadAssist(String loadAssist) { this.loadAssist = loadAssist; }
    public String getDeliveryOtp() { return deliveryOtp; }
    public void setDeliveryOtp(String deliveryOtp) { this.deliveryOtp = deliveryOtp; }
    public LocalDateTime getOtpExpiresAt() { return otpExpiresAt; }
    public void setOtpExpiresAt(LocalDateTime otpExpiresAt) { this.otpExpiresAt = otpExpiresAt; }
    public String getCancellationReason() { return cancellationReason; }
    public void setCancellationReason(String cancellationReason) { this.cancellationReason = cancellationReason; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
