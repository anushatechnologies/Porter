package com.anushaporter.backend.service;

import com.anushaporter.backend.model.BookingStatus;
import com.anushaporter.backend.model.Driver;
import com.anushaporter.backend.model.Order;
import com.anushaporter.backend.repository.DriverOfferRepository;
import com.anushaporter.backend.repository.DriverRepository;
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

    @Value("${assignment.radius.tiers:3,5,10,15}")
    private String radiusTiersStr;

    @Value("${assignment.offer.timeout-seconds:30}")
    private int offerTimeoutSeconds;

    @Value("${assignment.total-timeout-minutes:4}")
    private int totalTimeoutMinutes;

    @Value("${assignment.top-drivers-count:3}")
    private int topDriversCount;

    private final ExecutorService executorService = Executors.newCachedThreadPool();

    public CompletableFuture<Boolean> startAutoAssignment(String bookingId) {
        return CompletableFuture.supplyAsync(() -> executeAssignmentFlow(bookingId), executorService);
    }

    public boolean executeAssignmentFlow(String bookingId) {
        log.info("Starting Auto-Assignment for Booking '{}'", bookingId);

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
        LocalDateTime deadline = now.plusMinutes(totalTimeoutMinutes > 0 ? totalTimeoutMinutes : 4);
        order.setAssignmentDeadline(deadline);
        orderRepository.save(order);

        if (pushNotificationService != null) {
            pushNotificationService.notifyOrderStatus(order, BookingStatus.SEARCHING.name());
        }

        // Parse radius tiers
        List<Double> radiusTiers = parseRadiusTiers(radiusTiersStr);

        double pickupLat = (order.getPickupLat() != null) ? order.getPickupLat() : 17.4486;
        double pickupLng = (order.getPickupLng() != null) ? order.getPickupLng() : 78.3908;

        for (double radiusKm : radiusTiers) {
            // Check if overall deadline reached
            if (LocalDateTime.now().isAfter(deadline)) {
                log.warn("Overall assignment deadline ({} mins) reached for Booking '{}'", totalTimeoutMinutes, bookingId);
                break;
            }

            // Check if order was already assigned or cancelled by customer
            Order currentOrder = orderRepository.findByBookingId(bookingId).orElse(null);
            if (currentOrder == null || isAssignedOrTerminal(currentOrder.getStatus())) {
                log.info("Booking '{}' is no longer in SEARCHING state (current: '{}'). Halting expansion.",
                        bookingId, currentOrder != null ? currentOrder.getStatus() : "null");
                return true;
            }

            log.info("Searching eligible drivers for Booking '{}' within radius {} KM tier...", bookingId, radiusKm);

            // Fetch previously offered or excluded driver IDs
            Set<Long> excludedDriverIds = new HashSet<>(driverOfferRepository.findAllDriverIdsOfferedForBooking(bookingId));

            // Fetch all drivers
            List<Driver> allDrivers = driverRepository.findAll();

            // Filter eligible drivers
            List<Driver> eligibleDrivers = allDrivers.stream()
                    .filter(d -> driverEligibilityService.isEligible(d, currentOrder, excludedDriverIds))
                    .toList();

            // Rank eligible drivers and select Top N
            List<DriverRankingService.RankedDriver> rankedDrivers = driverRankingService.rankDrivers(
                    eligibleDrivers, pickupLat, pickupLng, radiusKm, topDriversCount
            );

            if (!rankedDrivers.isEmpty()) {
                log.info("Found {} candidate drivers in {} KM tier for Booking '{}'. Sending offers...",
                        rankedDrivers.size(), radiusKm, bookingId);

                // Dispatch parallel offers
                driverOfferService.createAndDispatchOffers(currentOrder, rankedDrivers, radiusKm, offerTimeoutSeconds);

                // Await response or timeout (poll every 2 seconds up to offerTimeoutSeconds)
                int waitIntervalMs = 2000;
                int totalWaitMs = (offerTimeoutSeconds > 0 ? offerTimeoutSeconds : 30) * 1000;
                int elapsedMs = 0;

                while (elapsedMs < totalWaitMs) {
                    try {
                        Thread.sleep(waitIntervalMs);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                    elapsedMs += waitIntervalMs;

                    Order checkedOrder = orderRepository.findByBookingId(bookingId).orElse(null);
                    if (checkedOrder != null && isAssignedOrTerminal(checkedOrder.getStatus())) {
                        log.info("Booking '{}' successfully assigned to driver ID #{} in {} KM tier!",
                                bookingId, checkedOrder.getDriverId(), radiusKm);
                        return true;
                    }
                }
            } else {
                log.info("No eligible drivers found in {} KM tier for Booking '{}'. Expanding search...", radiusKm, bookingId);
            }
        }

        // If loop completes without assignment -> AUTO_ASSIGN_FAILED
        Order finalOrder = orderRepository.findByBookingId(bookingId).orElse(null);
        if (finalOrder != null && !isAssignedOrTerminal(finalOrder.getStatus())) {
            finalOrder.setStatus(BookingStatus.AUTO_ASSIGN_FAILED.name());
            orderRepository.save(finalOrder);

            driverOfferRepository.cancelAllPendingOffersForBooking(bookingId, LocalDateTime.now());

            log.warn("Auto-assignment FAILED for Booking '{}'. All radius tiers exhausted.", bookingId);

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

    private List<Double> parseRadiusTiers(String tiersConfig) {
        List<Double> list = new ArrayList<>();
        if (tiersConfig != null && !tiersConfig.isBlank()) {
            for (String t : tiersConfig.split(",")) {
                try {
                    list.add(Double.parseDouble(t.trim()));
                } catch (NumberFormatException ignored) {}
            }
        }
        if (list.isEmpty()) {
            list = List.of(3.0, 5.0, 10.0, 15.0);
        }
        return list;
    }

    @PreDestroy
    public void shutdown() {
        executorService.shutdown();
    }
}
