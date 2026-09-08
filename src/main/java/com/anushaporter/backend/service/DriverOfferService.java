package com.anushaporter.backend.service;

import com.anushaporter.backend.dto.DriverOfferResponse;
import com.anushaporter.backend.model.*;
import com.anushaporter.backend.repository.DriverOfferRepository;
import com.anushaporter.backend.repository.DriverRepository;
import com.anushaporter.backend.repository.NotificationRepository;
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

    @Autowired(required = false)
    private NotificationRepository notificationRepository;

    @Autowired(required = false)
    private com.anushaporter.backend.config.handler.TelemetryWebSocketHandler telemetryWebSocketHandler;

    @Autowired(required = false)
    private DriverRankingService driverRankingService;

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
                    pushNotificationService.notifyDriverOffer(driver, order.getBookingId(), order.getPickupAddress(), order.getDropAddress(), order.getAmount());
                } catch (Exception e) {
                    log.warn("Failed to send push notification to driver {}: {}", driver.getId(), e.getMessage());
                }
            }
        }

        order.setOfferCount(order.getOfferCount() + createdOffers.size());
        orderRepository.save(order);

        return createdOffers;
    }

    public List<DriverOffer> broadcastOffersToActiveDrivers(Order order, List<Driver> activeDrivers, int timeoutSeconds) {
        return broadcastOffersToActiveDrivers(order, activeDrivers, 5.0, timeoutSeconds);
    }

    /**
     * Rapido / Swiggy Broadcast Dispatch: sends the ride request to active eligible drivers within the given radius tier.
     */
    public List<DriverOffer> broadcastOffersToActiveDrivers(Order order, List<Driver> activeDrivers, double radiusTierKm, int timeoutSeconds) {
        if (order == null || activeDrivers == null || activeDrivers.isEmpty()) {
            return List.of();
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plusSeconds(timeoutSeconds > 0 ? timeoutSeconds : 60);
        List<DriverOffer> createdOffers = new ArrayList<>();

        double pickupLat = order.getPickupLat() != null ? order.getPickupLat() : 17.4486;
        double pickupLng = order.getPickupLng() != null ? order.getPickupLng() : 78.3908;

        for (Driver driver : activeDrivers) {
            if (driver == null || driver.getId() == null) continue;

            double pickupDist = 1.0;
            if (driver.getLatitude() != null && driver.getLongitude() != null && driverRankingService != null) {
                pickupDist = driverRankingService.calculateHaversineDistanceKm(pickupLat, pickupLng, driver.getLatitude(), driver.getLongitude());
            }

            DriverOffer offer = new DriverOffer();
            offer.setBookingId(order.getBookingId());
            offer.setOrderId(order.getId());
            offer.setDriverId(driver.getId());
            offer.setStatus(DriverOfferStatus.OFFERED);
            offer.setRadiusTierKm(radiusTierKm > 0 ? radiusTierKm : pickupDist);
            offer.setDistanceKm(order.getDistanceKm() != null ? order.getDistanceKm() : 5.0);
            offer.setPickupDistanceKm(pickupDist);
            offer.setOfferedFare(order.getAmount() != null ? order.getAmount() : 250.0);
            offer.setOfferedAt(now);
            offer.setExpiresAt(expiresAt);

            DriverOffer saved = driverOfferRepository.save(offer);
            createdOffers.add(saved);

            log.info("Broadcast dispatched offer ID #{} to Driver ID #{} for Booking '{}' (Tier {} km, Pickup distance {} km)",
                    saved.getId(), driver.getId(), order.getBookingId(), radiusTierKm, pickupDist);

            // Dispatch Push Notification to driver
            if (pushNotificationService != null) {
                try {
                    pushNotificationService.notifyDriverOffer(driver, order.getBookingId(), order.getPickupAddress(), order.getDropAddress(), order.getAmount());
                } catch (Exception e) {
                    log.warn("Failed to send push notification to driver {}: {}", driver.getId(), e.getMessage());
                }
            }
        }

        order.setOfferCount(order.getOfferCount() + createdOffers.size());
        orderRepository.save(order);

        // Real-time WebSocket broadcast targeted to offered drivers
        if (telemetryWebSocketHandler != null && !createdOffers.isEmpty()) {
            try {
                List<Long> targetDriverIds = createdOffers.stream()
                        .map(DriverOffer::getDriverId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .collect(Collectors.toList());

                String payload = String.format("{\"bookingId\":\"%s\",\"pickupAddress\":\"%s\",\"dropAddress\":\"%s\",\"amount\":%.2f,\"distanceKm\":%.1f}",
                        order.getBookingId(),
                        order.getPickupAddress() != null ? order.getPickupAddress().replace("\"", "\\\"") : "",
                        order.getDropAddress() != null ? order.getDropAddress().replace("\"", "\\\"") : "",
                        order.getAmount() != null ? order.getAmount() : 0.0,
                        order.getDistanceKm() != null ? order.getDistanceKm() : 0.0);
                telemetryWebSocketHandler.broadcastOfferNew(order.getBookingId(), payload, targetDriverIds);
            } catch (Exception e) {
                log.warn("Failed to broadcast WebSocket offer: {}", e.getMessage());
            }
        }

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
            if (!accept) {
                // Synthesize rejected offer record so the driver is permanently marked as rejected for this booking
                DriverOffer rejectedOffer = new DriverOffer();
                rejectedOffer.setBookingId(bookingId);
                rejectedOffer.setDriverId(driverId);
                rejectedOffer.setStatus(DriverOfferStatus.REJECTED);
                rejectedOffer.setOfferedAt(now);
                rejectedOffer.setRespondedAt(now);
                driverOfferRepository.save(rejectedOffer);

                if (notificationRepository != null) {
                    try { notificationRepository.dismissDriverNotificationForBooking(bookingId, driverId); } catch (Exception ignored) {}
                }
                if (telemetryWebSocketHandler != null) {
                    try { telemetryWebSocketHandler.broadcastOfferDismissForDriver(bookingId, driverId, "REJECTED_BY_DRIVER"); } catch (Exception ignored) {}
                }
                if (pushNotificationService != null) {
                    try { driverRepository.findById(driverId).ifPresent(d -> pushNotificationService.notifyOfferDismissedForDriver(d, bookingId)); } catch (Exception ignored) {}
                }

                response.put("success", true);
                response.put("status", DriverOfferStatus.REJECTED.name());
                response.put("stopSound", true);
                response.put("action", "STOP_RINGTONE");
                response.put("message", "Offer rejected.");
                return response;
            }

            response.put("success", false);
            response.put("status", "NOT_FOUND");
            response.put("stopSound", true);
            response.put("action", "STOP_RINGTONE");
            response.put("message", "No offer found for this driver and booking.");
            return response;
        }

        DriverOffer offer = offerOpt.get();

        if (!accept) {
            offer.setStatus(DriverOfferStatus.REJECTED);
            offer.setRespondedAt(now);
            driverOfferRepository.save(offer);

            if (notificationRepository != null) {
                try {
                    notificationRepository.dismissDriverNotificationForBooking(bookingId, driverId);
                } catch (Exception ignored) {}
            }

            // Immediately send stop signal for this rejecting driver to stop audio playback
            if (telemetryWebSocketHandler != null) {
                try {
                    telemetryWebSocketHandler.broadcastOfferDismissForDriver(bookingId, driverId, "REJECTED_BY_DRIVER");
                } catch (Exception ignored) {}
            }

            if (pushNotificationService != null) {
                try {
                    driverRepository.findById(driverId).ifPresent(d -> pushNotificationService.notifyOfferDismissedForDriver(d, bookingId));
                } catch (Exception ignored) {}
            }

            response.put("success", true);
            response.put("status", DriverOfferStatus.REJECTED.name());
            response.put("stopSound", true);
            response.put("action", "STOP_RINGTONE");
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
            response.put("stopSound", true);
            response.put("action", "STOP_RINGTONE");
            response.put("message", "Offer has expired.");
            return response;
        }

        Driver driver = driverRepository.findById(driverId).orElse(null);
        if (driver == null) {
            response.put("success", false);
            response.put("status", "DRIVER_NOT_FOUND");
            response.put("stopSound", true);
            response.put("action", "STOP_RINGTONE");
            response.put("message", "Driver record not found.");
            return response;
        }

        if (driverWalletService != null) {
            double minRequired = driverWalletService.getMinRequiredBalance();
            Double walletBalance = driver.getWalletBalance();
            if (minRequired > 0.0 && (walletBalance == null || walletBalance < minRequired)) {
                response.put("success", false);
                response.put("status", "INSUFFICIENT_WALLET_BALANCE");
                response.put("error", "INSUFFICIENT_WALLET_BALANCE");
                response.put("stopSound", true);
                response.put("action", "STOP_RINGTONE");
                response.put("message", "Driver wallet balance must be at least ₹" + minRequired + " to accept rides. Please recharge your wallet.");
                return response;
            }
        }

        String driverIdStr = driver.getId().toString();
        String driverName = driver.getName() != null ? driver.getName() : "Driver";
        String driverEmail = driver.getEmail();
        String driverPhone = driver.getPhone();
        String driverVehicle = driver.getVehicleNumber();

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

            // Notify all competing drivers to stop notification & mark TOO_LATE
            onOrderAcceptedByDriver(bookingId, driverId);

            log.info("Driver ID #{} WON atomic assignment for Booking '{}'", driverId, bookingId);

            Order assignedOrder = orderRepository.findByBookingId(bookingId).orElse(null);
            if (pushNotificationService != null && assignedOrder != null) {
                pushNotificationService.notifyOrderStatus(assignedOrder, "ASSIGNED");
            }

            response.put("success", true);
            response.put("status", BookingStatus.ASSIGNED.name());
            response.put("bookingId", bookingId);
            response.put("driverId", driverId);
            response.put("stopSound", true);
            response.put("action", "STOP_RINGTONE");
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
            response.put("stopSound", true);
            response.put("action", "STOP_RINGTONE");
            response.put("message", "Another driver partner has already accepted this booking.");
            return response;
        }
    }

    /**
     * When any driver accepts the booking:
     * 1. Marks all competing offers as TOO_LATE.
     * 2. Dismisses all pending DRIVER_OFFER notifications for competing drivers in the DB.
     * 3. Dispatches push notification (STOP_DRIVER_OFFER) to competing drivers.
     * 4. Broadcasts WebSocket event to stop notification ringing across driver apps.
     */
    @Transactional
    public void onOrderAccepted(String bookingId, String winningDriverIdStr) {
        Long winningDriverId = null;
        if (winningDriverIdStr != null && !winningDriverIdStr.isBlank()) {
            try {
                winningDriverId = Long.parseLong(winningDriverIdStr);
            } catch (Exception ignored) {}
        }
        onOrderAcceptedByDriver(bookingId, winningDriverId);
    }

    @Transactional
    public void onOrderAcceptedByDriver(String bookingId, Long winningDriverId) {
        if (bookingId == null || bookingId.isBlank()) return;
        LocalDateTime now = LocalDateTime.now();

        // 1. Mark competing offers TOO_LATE
        driverOfferRepository.markCompetingOffersTooLate(bookingId, winningDriverId != null ? winningDriverId : -1L, now);

        // 2. Dismiss notifications in NotificationRepository (for both winner and competing drivers)
        if (notificationRepository != null) {
            try {
                notificationRepository.dismissNotificationsForBooking(bookingId, "DRIVER_OFFER", null);
            } catch (Exception e) {
                log.warn("Failed to dismiss notifications for booking {}: {}", bookingId, e.getMessage());
            }
        }

        // 3. Send silent push / stop notification to ALL offered drivers (including winner with confirmed status)
        if (pushNotificationService != null) {
            List<Long> competingDriverIds = driverOfferRepository.findAllDriverIdsOfferedForBooking(bookingId);
            for (Long cId : competingDriverIds) {
                if (winningDriverId != null && winningDriverId.equals(cId)) {
                    // Send stop push & confirmation to the winning driver
                    driverRepository.findById(cId).ifPresent(driver -> {
                        try {
                            pushNotificationService.notifyOfferAcceptedBySelf(driver, bookingId);
                        } catch (Exception ignored) {}
                    });
                } else {
                    // Send stop push to competing drivers
                    driverRepository.findById(cId).ifPresent(driver -> {
                        try {
                            pushNotificationService.notifyOfferTaken(driver, bookingId);
                        } catch (Exception ignored) {}
                    });
                }
            }
        }

        // 4. Broadcast WebSocket event to stop notification & ringtone across all driver apps
        if (telemetryWebSocketHandler != null) {
            try {
                telemetryWebSocketHandler.broadcastOfferDismiss(bookingId, "ACCEPTED", winningDriverId);
            } catch (Exception ignored) {}
        }
    }

    /**
     * When an order/booking is cancelled:
     * 1. Marks all pending offers as TOO_LATE/CANCELLED.
     * 2. Dismisses all pending DRIVER_OFFER notifications in the DB.
     * 3. Dispatches push notification (STOP_DRIVER_OFFER) to all offered drivers.
     * 4. Broadcasts WebSocket event to stop notification ringing across driver apps.
     */
    @Transactional
    public void onOrderCancelled(String bookingId) {
        if (bookingId == null || bookingId.isBlank()) return;
        LocalDateTime now = LocalDateTime.now();

        // 1. Mark all competing offers TOO_LATE
        driverOfferRepository.markCompetingOffersTooLate(bookingId, -1L, now);

        // 2. Dismiss all notifications in NotificationRepository
        if (notificationRepository != null) {
            try {
                notificationRepository.dismissNotificationsForBooking(bookingId, "DRIVER_OFFER", null);
            } catch (Exception e) {
                log.warn("Failed to dismiss notifications for cancelled booking {}: {}", bookingId, e.getMessage());
            }
        }

        // 3. Send silent push / stop notification to all offered drivers
        if (pushNotificationService != null) {
            List<Long> competingDriverIds = driverOfferRepository.findAllDriverIdsOfferedForBooking(bookingId);
            for (Long cId : competingDriverIds) {
                driverRepository.findById(cId).ifPresent(driver -> {
                    try {
                        pushNotificationService.notifyOfferTaken(driver, bookingId);
                    } catch (Exception ignored) {}
                });
            }
        }

        // 4. Broadcast WebSocket dismiss
        if (telemetryWebSocketHandler != null) {
            try {
                telemetryWebSocketHandler.broadcastOfferDismiss(bookingId, "CANCELLED");
            } catch (Exception ignored) {}
        }
    }
}
