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

        // 7. Resolve Service Track for Order and Driver
        String orderTrack = resolveOrderTrack(order);
        String driverTrack = resolveDriverTrack(driver);

        if (!orderTrack.equals(driverTrack)) {
            log.debug("Driver '{}' track '{}' does not match order track '{}'", driver.getId(), driverTrack, orderTrack);
            return false;
        }

        // 8. Check vehicle compatibility (strict category matching within track)
        String orderVehicleRaw = order != null ? (order.getServiceName() != null ? order.getServiceName() : order.getVehicleType()) : "";
        String driverVehicleRaw = driver.getVehicleType() != null && !driver.getVehicleType().isBlank()
                ? driver.getVehicleType()
                : (driver.getVehicle() != null ? driver.getVehicle() : "");

        String requiredCategory = normalizeVehicleCategory(orderVehicleRaw, orderTrack);
        String driverCategory = normalizeVehicleCategory(driverVehicleRaw, driverTrack);

        if (!"UNKNOWN".equals(requiredCategory) && !"UNKNOWN".equals(driverCategory)) {
            if (!requiredCategory.equals(driverCategory)) {
                log.debug("Driver '{}' category '{}' does not match required order category '{}'",
                        driver.getId(), driverCategory, requiredCategory);
                return false;
            }
        }

        return true;
    }

    /**
     * Resolves whether an order belongs to PASSENGER or OUR_SERVICES.
     */
    public String resolveOrderTrack(Order order) {
        if (order == null) return "OUR_SERVICES";
        String sType = order.getServiceType() != null ? order.getServiceType().trim().toUpperCase() : "";
        if (sType.contains("PASSENGER") || sType.contains("CAB") || sType.contains("RIDE")) {
            return "PASSENGER";
        }
        String sName = (order.getServiceName() != null ? order.getServiceName() : "").toLowerCase().replaceAll("[^a-z0-9]", "");
        if (sName.contains("cab") || sName.contains("biketaxi") || sName.contains("autotaxi") || sName.startsWith("pass") || sName.equals("6")) {
            return "PASSENGER";
        }
        return "OUR_SERVICES";
    }

    /**
     * Resolves whether a driver belongs to PASSENGER or OUR_SERVICES.
     */
    public String resolveDriverTrack(Driver driver) {
        if (driver == null) return "OUR_SERVICES";
        String sType = driver.getServiceType() != null ? driver.getServiceType().trim().toUpperCase() : "";
        if (sType.contains("PASSENGER") || sType.contains("CAB") || sType.contains("RIDE")) {
            return "PASSENGER";
        }
        String vStr = (driver.getVehicleType() != null ? driver.getVehicleType() : (driver.getVehicle() != null ? driver.getVehicle() : "")).toLowerCase().replaceAll("[^a-z0-9]", "");
        if (vStr.contains("cab") || vStr.contains("biketaxi") || vStr.contains("autotaxi") || vStr.startsWith("pass") || vStr.equals("6") || vStr.equals("passbike") || vStr.equals("passauto")) {
            return "PASSENGER";
        }
        return "OUR_SERVICES";
    }

    /**
     * Normalizes diverse vehicle labels and IDs to canonical track-aware categories:
     * PASSENGER: PASSENGER_BIKE_TAXI, PASSENGER_AUTO_TAXI, CAB
     * OUR_SERVICES: GOODS_TWO_WHEELER, GOODS_THREE_WHEELER, TATA_ACE, PICKUP_8FT, TATA_407
     */
    public String normalizeVehicleCategory(String raw, String track) {
        if (raw == null || raw.trim().isEmpty()) {
            return "UNKNOWN";
        }
        String s = raw.toLowerCase().replaceAll("[^a-z0-9]", "");

        if ("PASSENGER".equalsIgnoreCase(track)) {
            if (s.contains("biketaxi") || s.contains("bike") || s.contains("scooter") || s.contains("motorcycle") || s.equals("passbike") || s.equals("7")) {
                return "PASSENGER_BIKE_TAXI";
            }
            if (s.contains("autotaxi") || s.contains("auto") || s.contains("rickshaw") || s.equals("passauto") || s.equals("8")) {
                return "PASSENGER_AUTO_TAXI";
            }
            if (s.contains("cab") || s.contains("car") || s.contains("taxi") || s.contains("sedan") || s.contains("hatchback") || s.contains("suv") || s.equals("6")) {
                return "CAB";
            }
            return "UNKNOWN";
        } else {
            // OUR_SERVICES (Goods & Delivery)
            if (s.contains("2wheel") || s.contains("twowheel") || s.contains("bike") || s.contains("scooter")
                    || s.contains("motorcycle") || s.contains("moto") || s.contains("courier") || s.contains("parcel")
                    || s.equals("1")) {
                return "TWO_WHEELER";
            }
            if (s.contains("3wheel") || s.contains("threewheel") || s.contains("auto") || s.contains("rickshaw")
                    || s.contains("cng") || s.equals("2")) {
                return "THREE_WHEELER";
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

    public String normalizeVehicleCategory(String raw) {
        return normalizeVehicleCategory(raw, "OUR_SERVICES");
    }
}
