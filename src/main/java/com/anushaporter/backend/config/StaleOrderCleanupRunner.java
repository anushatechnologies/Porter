package com.anushaporter.backend.config;

import com.anushaporter.backend.model.BookingStatus;
import com.anushaporter.backend.model.Order;
import com.anushaporter.backend.repository.DriverOfferRepository;
import com.anushaporter.backend.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Startup runner to clean up stale/dangling test orders from previous sessions.
 * Automatically marks unassigned orders in 'searching' or 'pending' state older than 10 minutes
 * as AUTO_ASSIGN_FAILED and expires lingering driver offers.
 */
@Component
@org.springframework.core.annotation.Order(15)
public class StaleOrderCleanupRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(StaleOrderCleanupRunner.class);

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private DriverOfferRepository driverOfferRepository;

    @Override
    public void run(String... args) {
        try {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime cutoff = now.minusMinutes(10);

            List<Order> allOrders = orderRepository.findAll();
            int cleanedOrdersCount = 0;

            for (Order order : allOrders) {
                String status = order.getStatus() != null ? order.getStatus().trim().toLowerCase() : "";
                boolean isUnassigned = (order.getDriverId() == null || order.getDriverId().isBlank());
                boolean isSearchingOrPending = status.equals("searching") || status.equals("pending") || status.equals("created");

                if (isUnassigned && isSearchingOrPending) {
                    boolean isOlderThanCutoff = (order.getCreatedAt() == null || order.getCreatedAt().isBefore(cutoff));
                    boolean deadlinePassed = (order.getAssignmentDeadline() != null && order.getAssignmentDeadline().isBefore(now));

                    if (isOlderThanCutoff || deadlinePassed) {
                        order.setStatus(BookingStatus.AUTO_ASSIGN_FAILED.name());
                        orderRepository.save(order);
                        cleanedOrdersCount++;

                        if (order.getBookingId() != null && !order.getBookingId().isBlank()) {
                            try {
                                driverOfferRepository.cancelAllPendingOffersForBooking(order.getBookingId(), now);
                            } catch (Exception ignored) {}
                        }
                    }
                }
            }

            try {
                int expiredOffers = driverOfferRepository.expirePendingOffers(now);
                if (expiredOffers > 0) {
                    log.info("[StaleOrderCleanupRunner] Expired {} lingering driver offers from previous sessions.", expiredOffers);
                }
            } catch (Exception e) {
                log.warn("[StaleOrderCleanupRunner] Note expiring pending offers: {}", e.getMessage());
            }

            if (cleanedOrdersCount > 0) {
                log.info("[StaleOrderCleanupRunner] Successfully cleaned up {} stale unassigned orders from database.", cleanedOrdersCount);
            }
        } catch (Exception e) {
            log.warn("[StaleOrderCleanupRunner] Notice during startup cleanup: {}", e.getMessage());
        }
    }
}
