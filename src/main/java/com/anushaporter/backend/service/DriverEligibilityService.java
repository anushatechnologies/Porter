package com.anushaporter.backend.service;

import com.anushaporter.backend.model.Driver;
import com.anushaporter.backend.model.Order;
import com.anushaporter.backend.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

@Service
public class DriverEligibilityService {

    private static final Logger log = LoggerFactory.getLogger(DriverEligibilityService.class);

    @Autowired
    private OrderRepository orderRepository;

    private static final List<String> ACTIVE_ORDER_STATUSES = List.of(
            "ASSIGNED", "assigned",
            "ACCEPTED", "accepted",
            "DRIVER_EN_ROUTE", "driver_en_route", "arriving_at_pickup",
            "DRIVER_ARRIVED", "driver_arrived", "driver_reached",
            "PICKED_UP", "picked_up", "pickup_started",
            "IN_TRANSIT", "in_transit", "transit"
    );

    public boolean isEligible(Driver driver, Order order, Set<Long> excludedDriverIds) {
        if (driver == null || driver.getId() == null) {
            return false;
        }

        // 1. Exclude already offered or cancelled drivers
        if (excludedDriverIds != null && excludedDriverIds.contains(driver.getId())) {
            return false;
        }

        // 2. Check online status
        String status = driver.getStatus() != null ? driver.getStatus().trim().toLowerCase() : "offline";
        boolean isOnline = status.equals("online") || status.equals("active") || status.equals("available");
        if (!isOnline) {
            return false;
        }

        // 3. Check KYC verification status
        String kyc = driver.getKyc() != null ? driver.getKyc().trim().toLowerCase() : "approved";
        String verification = driver.getVerificationStatus() != null ? driver.getVerificationStatus().trim().toLowerCase() : "approved";
        if (kyc.equals("rejected") || verification.equals("rejected")) {
            return false;
        }

        // 4. Check wallet balance (must not be zero/negative)
        Double wallet = driver.getWalletBalance();
        if (wallet != null && wallet <= 0.0) {
            return false;
        }

        // 5. Check if driver has valid GPS coordinates
        if (driver.getLatitude() == null || driver.getLongitude() == null) {
            return false;
        }

        // 6. Check active ongoing orders
        String driverIdStr = driver.getId().toString();
        List<Order> activeOrders = orderRepository.findAllByDriverIdAndStatusIn(driverIdStr, ACTIVE_ORDER_STATUSES);
        if (activeOrders != null && !activeOrders.isEmpty()) {
            return false;
        }
        if (driver.getEmail() != null && !driver.getEmail().isBlank()) {
            List<Order> activeByEmail = orderRepository.findAllByDriverEmailAndStatusInOrderByCreatedAtDesc(driver.getEmail(), ACTIVE_ORDER_STATUSES);
            if (activeByEmail != null && !activeByEmail.isEmpty()) {
                return false;
            }
        }

        // 7. Check vehicle compatibility
        if (order != null && order.getServiceName() != null && !order.getServiceName().isBlank()) {
            String requiredService = order.getServiceName().toLowerCase().replaceAll("[^a-z0-9]", "");
            String driverVehicle = (driver.getVehicleType() != null ? driver.getVehicleType() : (driver.getVehicle() != null ? driver.getVehicle() : "")).toLowerCase().replaceAll("[^a-z0-9]", "");

            if (!driverVehicle.isEmpty() && !requiredService.isEmpty()) {
                // If specific truck is required, verify driver vehicle
                if (requiredService.contains("truck") || requiredService.contains("14ft") || requiredService.contains("8ft")) {
                    if (!driverVehicle.contains("truck") && !driverVehicle.contains("pickup") && !driverVehicle.contains("ace") && !driverVehicle.contains("8ft") && !driverVehicle.contains("14ft")) {
                        return false;
                    }
                }
            }
        }

        return true;
    }
}
