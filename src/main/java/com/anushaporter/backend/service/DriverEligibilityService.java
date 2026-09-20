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

        // 4. Wallet balance policy: Any wallet balance (0, negative, or positive) is allowed to accept rides
        // Wallet code is preserved, but does not block ride eligibility.

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

        // 7. Check vehicle compatibility across service tracks
        if (!isVehicleCompatible(driver, order)) {
            log.debug("Driver '{}' vehicle is not compatible with order '{}' (serviceName: '{}', serviceType: '{}')",
                    driver.getId(), order != null ? order.getId() : null,
                    order != null ? order.getServiceName() : null,
                    order != null ? order.getServiceType() : null);
            return false;
        }

        return true;
    }

    /**
     * Determines whether a driver's vehicle is compatible with an incoming order.
     * Supports dual-fleet capability for Auto and 2-Wheeler drivers (goods + passenger rides)
     * while strictly ensuring freight trucks never receive passenger orders.
     */
    public boolean isVehicleCompatible(Driver driver, Order order) {
        if (driver == null || order == null) return false;

        String orderTrack = resolveOrderTrack(order);
        String orderVehicleRaw = order.getServiceName() != null ? order.getServiceName() : order.getVehicleType();
        String driverVehicleRaw = driver.getVehicleType() != null && !driver.getVehicleType().isBlank()
                ? driver.getVehicleType()
                : (driver.getVehicle() != null ? driver.getVehicle() : "");

        String dClean = driverVehicleRaw.toLowerCase().replaceAll("[^a-z0-9]", "");

        if ("PASSENGER".equalsIgnoreCase(orderTrack)) {
            // Freight trucks can NEVER take passenger orders
            if (dClean.contains("tataace") || dClean.contains("ace") || dClean.contains("pickup") || dClean.contains("8ft")
                    || dClean.contains("407") || dClean.contains("tata407") || dClean.contains("truck") || dClean.contains("1109") || dClean.contains("lpt")) {
                return false;
            }

            String reqCategory = normalizeVehicleCategory(orderVehicleRaw, "PASSENGER");
            if ("PASSENGER_AUTO_TAXI".equals(reqCategory)) {
                return dClean.contains("auto") || dClean.contains("rickshaw") || dClean.contains("3wheel") || dClean.contains("threewheel")
                        || dClean.equals("2") || dClean.equals("passauto") || dClean.equals("8");
            }
            if ("PASSENGER_BIKE_TAXI".equals(reqCategory)) {
                return dClean.contains("bike") || dClean.contains("scooter") || dClean.contains("motorcycle") || dClean.contains("moto")
                        || dClean.contains("2wheel") || dClean.contains("twowheel") || dClean.equals("1") || dClean.equals("passbike") || dClean.equals("7");
            }
            if ("CAB".equals(reqCategory)) {
                return dClean.contains("cab") || dClean.contains("car") || dClean.contains("taxi") || dClean.contains("sedan")
                        || dClean.contains("hatchback") || dClean.contains("suv") || dClean.equals("6");
            }
            return false;
        } else {
            // OUR_SERVICES (Goods & Delivery)
            // Dedicated passenger cabs cannot take goods delivery
            if (dClean.contains("cab") || dClean.contains("car") || dClean.contains("taxi") || dClean.contains("sedan")
                    || dClean.contains("hatchback") || dClean.contains("suv") || dClean.equals("6")) {
                return false;
            }

            String reqCategory = normalizeVehicleCategory(orderVehicleRaw, "OUR_SERVICES");
            String driverCategory = normalizeVehicleCategory(driverVehicleRaw, "OUR_SERVICES");

            if ("UNKNOWN".equals(reqCategory) || "UNKNOWN".equals(driverCategory)) {
                return false;
            }
            return reqCategory.equals(driverCategory);
        }
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
        if (sType.contains("OUR_SERVICES") || sType.contains("GOODS") || sType.contains("DELIVERY")) {
            return "OUR_SERVICES";
        }
        String sName = (order.getServiceName() != null ? order.getServiceName() : "").toLowerCase().replaceAll("[^a-z0-9]", "");
        if (sName.contains("cab") || sName.contains("biketaxi") || sName.contains("autotaxi") || sName.startsWith("pass") || sName.equals("6") || sName.equals("passbike") || sName.equals("passauto")) {
            return "PASSENGER";
        }
        return "OUR_SERVICES";
    }

    /**
     * Resolves whether a driver belongs to PASSENGER, OUR_SERVICES, or BOTH.
     */
    public String resolveDriverTrack(Driver driver) {
        if (driver == null) return "OUR_SERVICES";
        String sType = driver.getServiceType() != null ? driver.getServiceType().trim().toUpperCase() : "";
        if ("BOTH".equals(sType) || "ALL".equals(sType)) {
            return "BOTH";
        }
        if (sType.contains("PASSENGER") || sType.contains("CAB") || sType.contains("RIDE")) {
            return "PASSENGER";
        }
        if (sType.contains("OUR_SERVICES") || sType.contains("GOODS") || sType.contains("DELIVERY")) {
            return "OUR_SERVICES";
        }
        String vStr = (driver.getVehicleType() != null ? driver.getVehicleType() : (driver.getVehicle() != null ? driver.getVehicle() : "")).toLowerCase().replaceAll("[^a-z0-9]", "");
        if (vStr.contains("cab") || vStr.contains("sedan") || vStr.contains("hatchback") || vStr.contains("suv") || vStr.equals("6")) {
            return "PASSENGER";
        }
        if (vStr.contains("auto") || vStr.contains("rickshaw") || vStr.contains("3wheel") || vStr.contains("bike") || vStr.contains("scooter") || vStr.contains("2wheel")) {
            return "BOTH";
        }
        return "OUR_SERVICES";
    }

    /**
     * Normalizes diverse vehicle labels and IDs to canonical track-aware categories:
     * PASSENGER: PASSENGER_BIKE_TAXI, PASSENGER_AUTO_TAXI, CAB
     * OUR_SERVICES: TWO_WHEELER, THREE_WHEELER, TATA_ACE, PICKUP_8FT, TATA_407
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
                    || s.contains("cng") || s.contains("loader") || s.equals("2")) {
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
