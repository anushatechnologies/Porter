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

    @Autowired(required = false)
    private DriverWalletService driverWalletService;

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

        // 4. Check wallet balance (must satisfy minRequiredBalance, 0 allowed if min is 0, negative blocked)
        if (driverWalletService != null) {
            if (!driverWalletService.canDriverAcceptRide(driver)) {
                return false;
            }
        } else {
            Double wallet = driver.getWalletBalance() != null ? driver.getWalletBalance() : 0.0;
            if (wallet < 0.0) {
                return false;
            }
        }

        // 5. GPS coordinates (fallback if null so active driver is not excluded)
        if (driver.getLatitude() == null || driver.getLongitude() == null) {
            driver.setLatitude(order != null && order.getPickupLat() != null ? order.getPickupLat() : 17.4486);
            driver.setLongitude(order != null && order.getPickupLng() != null ? order.getPickupLng() : 78.3908);
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

        // 7. Check vehicle compatibility (strict category matching)
        String requiredCategory = "UNKNOWN";
        String driverCategory = "UNKNOWN";

        String driverRaw = driver.getVehicleType() != null && !driver.getVehicleType().isBlank()
                ? driver.getVehicleType()
                : (driver.getVehicle() != null ? driver.getVehicle() : "");
        driverCategory = normalizeVehicleCategory(driverRaw);

        if (order != null && order.getServiceName() != null && !order.getServiceName().isBlank()) {
            String requiredRaw = order.getServiceName();
            requiredCategory = normalizeVehicleCategory(requiredRaw);

            // If the order specifies a recognized vehicle category, driver must match that exact category
            if (!"UNKNOWN".equals(requiredCategory)) {
                if (!requiredCategory.equals(driverCategory)) {
                    log.debug("Driver '{}' vehicle category '{}' does not match required order vehicle category '{}'",
                            driver.getId(), driverCategory, requiredCategory);
                    return false;
                }
            }
        }

        // 8. Check service capability compatibility (Our Services/Goods vs. Passenger)
        String orderType = order != null && order.getServiceType() != null ? order.getServiceType().toUpperCase() : "GOODS";
        if ("CAB".equals(requiredCategory) || "BIKE_TAXI".equals(requiredCategory) || "AUTO_TAXI".equals(requiredCategory)) {
            orderType = "PASSENGER";
        }
        if ("OUR_SERVICES".equals(orderType) || "OURSERVICES".equals(orderType)) {
            orderType = "GOODS";
        }

        String driverService = driver.getServiceType() != null ? driver.getServiceType().toUpperCase() : "GOODS";
        if ("OUR_SERVICES".equals(driverService) || "OURSERVICES".equals(driverService)) {
            driverService = "GOODS";
        } else if ("CAB".equals(driverCategory)) {
            driverService = "PASSENGER";
        } else if ("TATA_ACE".equals(driverCategory) || "PICKUP_8FT".equals(driverCategory) || "TATA_407".equals(driverCategory)) {
            driverService = "GOODS";
        }

        if ("PASSENGER".equals(orderType) && "GOODS".equals(driverService)) {
            log.debug("Driver '{}' is goods/our-services only, rejecting for passenger order", driver.getId());
            return false;
        }
        if ("GOODS".equals(orderType) && "PASSENGER".equals(driverService)) {
            log.debug("Driver '{}' is passenger-only, rejecting for goods/our-services order", driver.getId());
            return false;
        }

        return true;
    }

    /**
     * Normalizes diverse vehicle labels and IDs to canonical categories:
     * TWO_WHEELER, THREE_WHEELER, CAB, TATA_ACE, PICKUP_8FT, TATA_407
     */
    public String normalizeVehicleCategory(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return "UNKNOWN";
        }
        String s = raw.toLowerCase().replaceAll("[^a-z0-9]", "");
        if (s.contains("2wheel") || s.contains("twowheel") || s.contains("bike") || s.contains("scooter")
                || s.contains("motorcycle") || s.contains("moto") || s.contains("courier") || s.contains("parcel")
                || s.contains("biketaxi")
                || s.contains("activa") || s.contains("jupiter") || s.contains("platina") || s.contains("splendor")
                || s.contains("pulsar") || s.contains("apache") || s.contains("dio") || s.contains("shine")
                || s.equals("1")) {
            return "TWO_WHEELER";
        }
        if (s.contains("3wheel") || s.contains("threewheel") || s.contains("auto") || s.contains("rickshaw")
                || s.contains("cng") || s.contains("autotaxi") || s.equals("2")) {
            return "THREE_WHEELER";
        }
        if (s.contains("cab") || s.contains("car") || s.contains("taxi") || s.contains("sedan")
                || s.contains("hatchback") || s.contains("suv") || s.contains("dzire") || s.contains("etios")
                || s.contains("wagonr") || s.contains("innova") || s.contains("ertiga")
                || s.equals("6")) {
            return "CAB";
        }
        if (s.contains("tataace") || s.contains("ace") || s.contains("chotahathi") || s.equals("3")) {
            return "TATA_ACE";
        }
        if (s.contains("8ft") || s.contains("pickup") || s.contains("bolero") || s.contains("dost") || s.equals("4")) {
            return "PICKUP_8FT";
        }
        if (s.contains("407") || s.contains("tata407") || s.contains("14ft") || s.contains("truck") || s.contains("eicher") || s.contains("large") || s.equals("5")) {
            return "TATA_407";
        }
        return "UNKNOWN";
    }
}
