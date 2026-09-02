package com.anushaporter.backend.service;

import com.anushaporter.backend.dto.DriverOfferResponse;
import com.anushaporter.backend.model.*;
import com.anushaporter.backend.repository.DriverOfferRepository;
import com.anushaporter.backend.repository.DriverRepository;
import com.anushaporter.backend.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DriverOfferService {

    private static final Logger log = LoggerFactory.getLogger(DriverOfferService.class);

    @Autowired
    private DriverOfferRepository driverOfferRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private DriverRepository driverRepository;

    @Autowired(required = false)
    private PushNotificationService pushNotificationService;

    @Autowired(required = false)
    private DriverWalletService driverWalletService;

    public List<DriverOffer> createAndDispatchOffers(Order order, List<DriverRankingService.RankedDriver> rankedDrivers, double radiusTierKm, int timeoutSeconds) {
        if (order == null || rankedDrivers == null || rankedDrivers.isEmpty()) {
            return List.of();
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plusSeconds(timeoutSeconds > 0 ? timeoutSeconds : 30);
        List<DriverOffer> createdOffers = new ArrayList<>();

        for (DriverRankingService.RankedDriver rd : rankedDrivers) {
            Driver driver = rd.getDriver();
            if (driver == null || driver.getId() == null) continue;

            DriverOffer offer = new DriverOffer();
            offer.setBookingId(order.getBookingId());
            offer.setOrderId(order.getId());
            offer.setDriverId(driver.getId());
            offer.setStatus(DriverOfferStatus.OFFERED);
            offer.setRadiusTierKm(radiusTierKm);
            offer.setDistanceKm(order.getDistanceKm() != null ? order.getDistanceKm() : 5.0);
            offer.setPickupDistanceKm(rd.getDistanceKm());
            offer.setOfferedFare(order.getAmount() != null ? order.getAmount() : 250.0);
            offer.setOfferedAt(now);
            offer.setExpiresAt(expiresAt);

            DriverOffer saved = driverOfferRepository.save(offer);
            createdOffers.add(saved);

            log.info("Dispatched offer ID #{} to Driver ID #{} for Booking '{}' (Radius {} km, Pickup distance {} km)",
                    saved.getId(), driver.getId(), order.getBookingId(), radiusTierKm, rd.getDistanceKm());

            // Dispatch Push Notification to driver
            if (pushNotificationService != null) {
                try {
                    pushNotificationService.notifyDriverAssignment(driver.getId().toString(), order.getBookingId(), order.getPickupAddress(), order.getDropAddress());
                } catch (Exception e) {
                    log.warn("Failed to send push notification to driver {}: {}", driver.getId(), e.getMessage());
                }
            }
        }

        order.setOfferCount(order.getOfferCount() + createdOffers.size());
        orderRepository.save(order);

        return createdOffers;
    }

    public List<DriverOfferResponse> getActiveOffersForDriver(Long driverId) {
        if (driverId == null) return List.of();
        LocalDateTime now = LocalDateTime.now();
        List<DriverOffer> activeOffers = driverOfferRepository.findActiveOffersForDriver(driverId, now);

        return activeOffers.stream().map(offer -> {
            DriverOfferResponse dto = new DriverOfferResponse();
            dto.setOfferId(offer.getId());
            dto.setBookingId(offer.getBookingId());
            dto.setOrderId(offer.getOrderId());
            dto.setDriverId(offer.getDriverId());
            dto.setStatus(offer.getStatus());
            dto.setRadiusTierKm(offer.getRadiusTierKm());
            dto.setDistanceKm(offer.getDistanceKm());
            dto.setPickupDistanceKm(offer.getPickupDistanceKm());
            dto.setOfferedFare(offer.getOfferedFare());
            dto.setOfferedAt(offer.getOfferedAt());
            dto.setExpiresAt(offer.getExpiresAt());

            if (offer.getExpiresAt() != null) {
                long remaining = Duration.between(now, offer.getExpiresAt()).getSeconds();
                dto.setRemainingSeconds(Math.max(0, remaining));
            }

            // Populate order details
            if (offer.getBookingId() != null) {
                orderRepository.findByBookingId(offer.getBookingId()).ifPresent(o -> {
                    dto.setPickupAddress(o.getPickupAddress());
                    dto.setDropAddress(o.getDropAddress());
                    dto.setPickupLat(o.getPickupLat());
                    dto.setPickupLng(o.getPickupLng());
                    dto.setDropLat(o.getDropLat());
                    dto.setDropLng(o.getDropLng());
                    dto.setServiceName(o.getServiceName());
                    dto.setGoodsCategory(o.getGoodsCategory());
                    dto.setHelpersCount(o.getHelpersCount());
                });
            }

            return dto;
        }).collect(Collectors.toList());
    }

    @Transactional
    public Map<String, Object> respondToOffer(String bookingId, Long driverId, boolean accept) {
        Map<String, Object> response = new LinkedHashMap<>();
        LocalDateTime now = LocalDateTime.now();

        Optional<DriverOffer> offerOpt = driverOfferRepository.findFirstByBookingIdAndDriverIdOrderByIdDesc(bookingId, driverId);
        if (offerOpt.isEmpty()) {
            response.put("success", false);
            response.put("status", "NOT_FOUND");
            response.put("message", "No offer found for this driver and booking.");
            return response;
        }

        DriverOffer offer = offerOpt.get();

        if (!accept) {
            offer.setStatus(DriverOfferStatus.REJECTED);
            offer.setRespondedAt(now);
            driverOfferRepository.save(offer);

            response.put("success", true);
            response.put("status", DriverOfferStatus.REJECTED.name());
            response.put("message", "Offer rejected.");
            return response;
        }

        // Driver attempted to ACCEPT
        if (offer.getExpiresAt() != null && offer.getExpiresAt().isBefore(now)) {
            offer.setStatus(DriverOfferStatus.EXPIRED);
            offer.setRespondedAt(now);
            driverOfferRepository.save(offer);

            response.put("success", false);
            response.put("status", DriverOfferStatus.EXPIRED.name());
            response.put("message", "Offer has expired.");
            return response;
        }

        Driver driver = driverRepository.findById(driverId).orElse(null);
        if (driver == null) {
            response.put("success", false);
            response.put("status", "DRIVER_NOT_FOUND");
            response.put("message", "Driver record not found.");
            return response;
        }

        String driverIdStr = driver.getId().toString();
        String driverName = driver.getName() != null ? driver.getName() : "Driver";
        String driverEmail = driver.getEmail();
        String driverPhone = driver.getPhone();
        String driverVehicle = driver.getVehicleNumber();

        // Check if driver has sufficient wallet balance (at least 5% commission of ride fare and balance > 0)
        Order order = orderRepository.findByBookingId(bookingId).orElse(null);
        if (order != null && driverWalletService != null) {
            Map<String, Object> eligibility = driverWalletService.checkRideAcceptanceEligibility(driver, order);
            if (!Boolean.TRUE.equals(eligibility.get("canAccept"))) {
                response.put("success", false);
                response.put("status", "INSUFFICIENT_WALLET_BALANCE");
                response.put("message", eligibility.get("message"));
                response.put("requiredCommission", eligibility.get("requiredCommission"));
                response.put("currentWalletBalance", eligibility.get("currentWalletBalance"));
                response.put("remainingAmount", eligibility.get("remainingAmount"));
                response.put("rechargeRequired", true);
                return response;
            }
        }

        // Perform ATOMIC assignment check
        int rowsUpdated = orderRepository.atomicAssignDriverToBooking(
                bookingId,
                driverIdStr,
                driverName,
                driverEmail,
                driverPhone,
                driverVehicle,
                "ASSIGNED",
                now
        );

        if (rowsUpdated == 1) {
            // WINNER!
            offer.setStatus(DriverOfferStatus.ACCEPTED);
            offer.setRespondedAt(now);
            driverOfferRepository.save(offer);

            // Mark all competing offers as TOO_LATE
            driverOfferRepository.markCompetingOffersTooLate(bookingId, driverId, now);

            log.info("Driver ID #{} WON atomic assignment for Booking '{}'", driverId, bookingId);

            Order assignedOrder = orderRepository.findByBookingId(bookingId).orElse(null);
            if (pushNotificationService != null && assignedOrder != null) {
                pushNotificationService.notifyOrderStatus(assignedOrder, "ASSIGNED");
            }

            // Deduct 5% platform commission immediately upon ride acceptance
            double fare = assignedOrder != null && assignedOrder.getAmount() != null && assignedOrder.getAmount() > 0
                    ? assignedOrder.getAmount() : (order != null && order.getAmount() != null ? order.getAmount() : 0.0);
            if (driverWalletService != null && fare > 0) {
                try {
                    driverWalletService.deductCommissionOnRideAcceptance(driverIdStr, bookingId, fare);
                } catch (Exception e) {
                    log.warn("Commission deduction on offer win notice: {}", e.getMessage());
                }
            }

            response.put("success", true);
            response.put("status", BookingStatus.ASSIGNED.name());
            response.put("bookingId", bookingId);
            response.put("driverId", driverId);
            response.put("order", assignedOrder);
            response.put("message", "Booking assigned successfully!");
            return response;
        } else {
            // LOSER (Another driver accepted first or booking was cancelled)
            offer.setStatus(DriverOfferStatus.TOO_LATE);
            offer.setRespondedAt(now);
            driverOfferRepository.save(offer);

            log.info("Driver ID #{} was TOO_LATE for Booking '{}'", driverId, bookingId);

            response.put("success", false);
            response.put("status", DriverOfferStatus.TOO_LATE.name());
            response.put("bookingId", bookingId);
            response.put("message", "Another driver partner has already accepted this booking.");
            return response;
        }
    }
}
