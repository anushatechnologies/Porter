package com.anushaporter.backend.service;

import com.anushaporter.backend.model.BookingStatus;
import com.anushaporter.backend.model.Driver;
import com.anushaporter.backend.model.Order;
import com.anushaporter.backend.repository.DriverOfferRepository;
import com.anushaporter.backend.repository.DriverRepository;
import com.anushaporter.backend.repository.NotificationRepository;
import com.anushaporter.backend.repository.OrderRepository;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

@Service
public class AutoAssignmentService {

    private static final Logger log = LoggerFactory.getLogger(AutoAssignmentService.class);

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private DriverRepository driverRepository;

    @Autowired
    private DriverOfferRepository driverOfferRepository;

    @Autowired
    private DriverEligibilityService driverEligibilityService;

    @Autowired
    private DriverRankingService driverRankingService;

    @Autowired
    private DriverOfferService driverOfferService;

    @Autowired(required = false)
    private PushNotificationService pushNotificationService;

    @Autowired(required = false)
    private NotificationRepository notificationRepository;

    @Autowired(required = false)
    private com.anushaporter.backend.config.handler.TelemetryWebSocketHandler telemetryWebSocketHandler;

    @Value("${assignment.offer.timeout-seconds:60}")
    private int offerTimeoutSeconds;

    @Value("${assignment.total-timeout-minutes:3}")
    private int totalTimeoutMinutes;

    private final ExecutorService executorService = Executors.newCachedThreadPool();

    public static final double[] DEFAULT_RADIUS_TIERS = {5.0, 10.0, 15.0, 20.0};

    @Value("${assignment.tier.duration-seconds:60}")
    private int tierDurationSeconds = 60;

    public int getTierDurationSeconds() {
        return tierDurationSeconds;
    }

    public void setTierDurationSeconds(int tierDurationSeconds) {
        this.tierDurationSeconds = tierDurationSeconds;
    }

    public CompletableFuture<Boolean> startAutoAssignment(String bookingId) {
        return CompletableFuture.supplyAsync(() -> executeAssignmentFlow(bookingId), executorService);
    }

    public boolean executeAssignmentFlow(String bookingId) {
        log.info("Starting Rapido/Swiggy Tiered Expanding Radius Auto-Assignment for Booking '{}' (Tier 1: 5km, Tier 2: 10km, etc.)", bookingId);

        Optional<Order> orderOpt = orderRepository.findByBookingId(bookingId);
        if (orderOpt.isEmpty()) {
            log.warn("Auto-assignment aborted: Booking '{}' not found.", bookingId);
            return false;
        }

        Order order = orderOpt.get();

        // 1. Initial State Transition to SEARCHING
        String currentStatus = order.getStatus() != null ? order.getStatus().toLowerCase() : "";
        if ("cancelled".equals(currentStatus) || "completed".equals(currentStatus) || "delivered".equals(currentStatus)) {
            log.info("Auto-assignment aborted: Booking '{}' is in terminal state '{}'", bookingId, currentStatus);
            return false;
        }

        order.setStatus(BookingStatus.SEARCHING.name());
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime deadline = now.plusMinutes(totalTimeoutMinutes > 0 ? totalTimeoutMinutes : 3);
        order.setAssignmentDeadline(deadline);
        orderRepository.save(order);

        if (pushNotificationService != null) {
            pushNotificationService.notifyOrderStatus(order, BookingStatus.SEARCHING.name());
        }

        // 2. Expanding Radius Tiers Broadcast (Tier 1: 5km, Tier 2: 10km, etc., each triggering for 60s)
        double pickupLat = order.getPickupLat() != null ? order.getPickupLat() : 17.4486;
        double pickupLng = order.getPickupLng() != null ? order.getPickupLng() : 78.3908;

        for (int tierIndex = 0; tierIndex < DEFAULT_RADIUS_TIERS.length; tierIndex++) {
            double currentMaxRadius = DEFAULT_RADIUS_TIERS[tierIndex];
            int currentTierNum = tierIndex + 1;

            if (LocalDateTime.now().isAfter(deadline)) break;

            Order currentOrder = orderRepository.findByBookingId(bookingId).orElse(null);
            if (currentOrder == null || isAssignedOrTerminal(currentOrder.getStatus())) {
                String finalStatus = currentOrder != null ? currentOrder.getStatus() : "null";
                log.info("Booking '{}' resolved (status: '{}'). Halting auto-assignment.", bookingId, finalStatus);
                return true;
            }

            Set<Long> alreadyOffered = new HashSet<>(driverOfferRepository.findAllDriverIdsOfferedForBooking(bookingId));
            List<Driver> allDrivers = driverRepository.findAll();
            List<Driver> eligibleDrivers = allDrivers.stream()
                    .filter(d -> driverEligibilityService.isEligible(d, currentOrder, alreadyOffered))
                    .toList();

            List<Driver> tierDrivers = driverRankingService.filterDriversWithinRadius(eligibleDrivers, pickupLat, pickupLng, currentMaxRadius);

            if (!tierDrivers.isEmpty()) {
                log.info("Triggering Tier {} (<= {} km) broadcast for Booking '{}' to {} active driver(s) for {}s...",
                        currentTierNum, currentMaxRadius, bookingId, tierDrivers.size(), tierDurationSeconds);

                driverOfferService.broadcastOffersToActiveDrivers(currentOrder, tierDrivers, currentMaxRadius, offerTimeoutSeconds > 0 ? offerTimeoutSeconds : 60);

                long tierEndMillis = System.currentTimeMillis() + (tierDurationSeconds * 1000L);
                while (System.currentTimeMillis() < tierEndMillis && LocalDateTime.now().isBefore(deadline)) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }

                    Order checked = orderRepository.findByBookingId(bookingId).orElse(null);
                    if (checked == null || isAssignedOrTerminal(checked.getStatus())) {
                        String finalStatus = checked != null ? checked.getStatus() : "null";
                        log.info("Booking '{}' resolved (status: '{}') during Tier {} (<= {} km). Halting auto-assignment.",
                                bookingId, finalStatus, currentTierNum, currentMaxRadius);
                        return true;
                    }

                    // Check if any new drivers came online within current unlocked radius
                    Set<Long> updatedOffered = new HashSet<>(driverOfferRepository.findAllDriverIdsOfferedForBooking(bookingId));
                    List<Driver> newlyEligible = driverRepository.findAll().stream()
                            .filter(d -> driverEligibilityService.isEligible(d, checked, updatedOffered))
                            .toList();
                    List<Driver> newlyInTier = driverRankingService.filterDriversWithinRadius(newlyEligible, pickupLat, pickupLng, currentMaxRadius);
                    if (!newlyInTier.isEmpty()) {
                        log.info("Found {} newly online driver(s) within <= {} km for Booking '{}'. Dispatching offers...",
                                newlyInTier.size(), currentMaxRadius, bookingId);
                        driverOfferService.broadcastOffersToActiveDrivers(checked, newlyInTier, currentMaxRadius, offerTimeoutSeconds > 0 ? offerTimeoutSeconds : 60);
                    }
                }
            } else {
                log.info("No active eligible drivers found in Tier {} (<= {} km) for Booking '{}'. Expanding immediately to next tier...",
                        currentTierNum, currentMaxRadius, bookingId);
                try { Thread.sleep(200); } catch (InterruptedException ignored) {}
            }
        }

        // 4. If deadline reached without assignment -> AUTO_ASSIGN_FAILED
        Order finalOrder = orderRepository.findByBookingId(bookingId).orElse(null);
        if (finalOrder != null && !isAssignedOrTerminal(finalOrder.getStatus())) {
            finalOrder.setStatus(BookingStatus.AUTO_ASSIGN_FAILED.name());
            orderRepository.save(finalOrder);

            driverOfferRepository.cancelAllPendingOffersForBooking(bookingId, LocalDateTime.now());

            if (notificationRepository != null) {
                try {
                    notificationRepository.dismissNotificationsForBooking(bookingId, "DRIVER_OFFER", null);
                } catch (Exception ignored) {}
            }

            if (telemetryWebSocketHandler != null) {
                try {
                    telemetryWebSocketHandler.broadcastOfferDismiss(bookingId, "TIMEOUT");
                } catch (Exception ignored) {}
            }

            log.warn("Auto-assignment FAILED for Booking '{}'. Deadline reached with no driver acceptance.", bookingId);

            if (pushNotificationService != null) {
                pushNotificationService.notifyOrderStatus(finalOrder, BookingStatus.AUTO_ASSIGN_FAILED.name());
            }
            return false;
        }

        return true;
    }

    private boolean isAssignedOrTerminal(String status) {
        if (status == null) return false;
        String s = status.trim().toUpperCase();
        return s.equals("ASSIGNED") || s.equals("ACCEPTED") || s.equals("DRIVER_ASSIGNED")
                || s.equals("DRIVER_EN_ROUTE") || s.equals("DRIVER_ARRIVED")
                || s.equals("PICKED_UP") || s.equals("IN_TRANSIT")
                || s.equals("DELIVERED") || s.equals("COMPLETED")
                || s.equals("CANCELLED") || s.equals("DRIVER_CANCELLED");
    }

    @PreDestroy
    public void shutdown() {
        executorService.shutdown();
    }
}
