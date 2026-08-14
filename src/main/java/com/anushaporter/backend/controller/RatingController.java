package com.anushaporter.backend.controller;

import com.anushaporter.backend.model.AppUser;
import com.anushaporter.backend.model.Driver;
import com.anushaporter.backend.model.Order;
import com.anushaporter.backend.model.Rating;
import com.anushaporter.backend.repository.AppUserRepository;
import com.anushaporter.backend.repository.DriverRepository;
import com.anushaporter.backend.repository.OrderRepository;
import com.anushaporter.backend.repository.RatingRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@RestController
@RequestMapping("/api")
public class RatingController {

    @Autowired
    private RatingRepository ratingRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private DriverRepository driverRepository;

    @Autowired
    private AppUserRepository appUserRepository;

    /**
     * Submit Driver Rating & Review
     * Supports:
     * - POST /api/bookings/{id}/rate
     * - POST /api/bookings/{id}/rating
     * - POST /api/driver/rate
     * - POST /api/ratings
     */
    @PostMapping({
            "/bookings/{id}/rate",
            "/bookings/{id}/rating",
            "/driver/rate",
            "/ratings"
    })
    public ResponseEntity<Map<String, Object>> submitRating(
            @PathVariable(required = false) String id,
            @RequestBody(required = false) Map<String, Object> payload,
            HttpServletRequest request) {

        if (payload == null) {
            payload = new HashMap<>();
        }

        // 1. Resolve booking ID from path or request body
        String bookingId = id;
        if (bookingId == null || bookingId.isBlank()) {
            if (payload.get("bookingId") != null) {
                bookingId = String.valueOf(payload.get("bookingId"));
            } else if (payload.get("orderId") != null) {
                bookingId = String.valueOf(payload.get("orderId"));
            } else if (payload.get("id") != null) {
                bookingId = String.valueOf(payload.get("id"));
            }
        }

        if (bookingId == null || bookingId.isBlank()) {
            return ResponseEntity.status(400).body(Map.of(
                    "success", false,
                    "message", "Booking ID is required"
            ));
        }

        // 2. Validate Rating value (Integer between 1 and 5)
        Object ratingObj = payload.get("rating");
        if (ratingObj == null) {
            return ResponseEntity.status(400).body(Map.of(
                    "success", false,
                    "message", "Rating must be an integer between 1 and 5"
            ));
        }

        int ratingValue;
        try {
            if (ratingObj instanceof Number) {
                ratingValue = ((Number) ratingObj).intValue();
            } else {
                ratingValue = Integer.parseInt(ratingObj.toString().trim());
            }
        } catch (Exception e) {
            return ResponseEntity.status(400).body(Map.of(
                    "success", false,
                    "message", "Rating must be an integer between 1 and 5"
            ));
        }

        if (ratingValue < 1 || ratingValue > 5) {
            return ResponseEntity.status(400).body(Map.of(
                    "success", false,
                    "message", "Rating must be an integer between 1 and 5"
            ));
        }

        // 3. Find Booking / Order
        Optional<Order> orderOpt = orderRepository.findByBookingId(bookingId);
        if (orderOpt.isEmpty()) {
            try {
                orderOpt = orderRepository.findById(Long.valueOf(bookingId));
            } catch (NumberFormatException ignored) {}
        }

        if (orderOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of(
                    "success", false,
                    "message", "Booking not found with the provided ID"
            ));
        }

        Order order = orderOpt.get();
        String finalBookingId = order.getBookingId() != null ? order.getBookingId() : bookingId;

        // 4. Verify Order is delivered/completed
        String status = order.getStatus() != null ? order.getStatus().toLowerCase() : "";
        if (!"delivered".equals(status) && !"completed".equals(status) && !"done".equals(status)) {
            return ResponseEntity.status(400).body(Map.of(
                    "success", false,
                    "message", "You can only rate an order after it has been delivered"
            ));
        }

        // 5. Prevent Duplicate Ratings for the same booking
        if (ratingRepository.existsByBookingId(finalBookingId)) {
            return ResponseEntity.status(409).body(Map.of(
                    "success", false,
                    "message", "This order has already been rated"
            ));
        }

        // 6. Extract Customer Details
        String customerEmail = order.getUserEmail();
        String customerName = order.getReceiverName();
        String customerId = null;

        String authUser = (String) request.getAttribute("userId");
        if (authUser != null) {
            Optional<AppUser> userOpt = appUserRepository.findFirstByEmailOrderByIdDesc(authUser);
            if (userOpt.isPresent()) {
                AppUser u = userOpt.get();
                if (customerEmail == null || customerEmail.isBlank()) customerEmail = u.getEmail();
                if (customerName == null || customerName.isBlank()) customerName = u.getName();
                customerId = String.valueOf(u.getId());
            }
        }

        // 7. Extract Driver Details
        String driverId = order.getDriverId();
        if ((driverId == null || driverId.isBlank()) && payload.get("driverId") != null) {
            driverId = String.valueOf(payload.get("driverId"));
        }
        String driverEmail = order.getDriverEmail();
        String driverName = order.getDriverName();

        // 8. Extract Review & Feedback Tags
        String review = payload.get("review") != null ? String.valueOf(payload.get("review")) : null;

        List<String> feedbackTags = new ArrayList<>();
        Object feedbackObj = payload.get("feedback");
        if (feedbackObj instanceof List<?>) {
            for (Object item : (List<?>) feedbackObj) {
                if (item != null) {
                    feedbackTags.add(String.valueOf(item));
                }
            }
        }

        // 9. Save Rating Entity
        Rating rating = new Rating();
        rating.setBookingId(finalBookingId);
        rating.setRating(ratingValue);
        rating.setReview(review);
        rating.setFeedback(feedbackTags);
        rating.setCustomerId(customerId);
        rating.setCustomerEmail(customerEmail);
        rating.setCustomerName(customerName);
        rating.setDriverId(driverId);
        rating.setDriverEmail(driverEmail);
        rating.setDriverName(driverName);
        rating.setCreatedAt(LocalDateTime.now());

        Rating savedRating = ratingRepository.save(rating);

        // 10. Recalculate Driver Average Rating
        Double newAverage = (double) ratingValue;
        Driver driver = null;
        if (driverId != null && !driverId.isBlank()) {
            try {
                driver = driverRepository.findById(Long.valueOf(driverId)).orElse(null);
            } catch (NumberFormatException ignored) {}
        }
        if (driver == null && driverEmail != null && !driverEmail.isBlank()) {
            driver = driverRepository.findByEmailIgnoreCase(driverEmail).orElse(null);
        }
        if (driver == null && order.getDriverPhone() != null) {
            driver = driverRepository.findByPhone(order.getDriverPhone()).orElse(null);
        }

        if (driver != null) {
            List<Rating> driverRatings = ratingRepository.findByDriverId(driver.getId().toString());
            if (driverRatings.isEmpty() && driver.getEmail() != null) {
                driverRatings = ratingRepository.findByDriverEmail(driver.getEmail());
            }

            if (!driverRatings.isEmpty()) {
                double sum = 0.0;
                for (Rating r : driverRatings) {
                    sum += r.getRating();
                }
                newAverage = Math.round((sum / driverRatings.size()) * 10.0) / 10.0;
            }
            driver.setRating(String.format(Locale.US, "%.1f", newAverage));
            driverRepository.save(driver);
        }

        // 11. Format ISO Creation Timestamp
        String createdAtIso = savedRating.getCreatedAt() != null
                ? savedRating.getCreatedAt().format(DateTimeFormatter.ISO_DATE_TIME) + "Z"
                : LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME) + "Z";

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("bookingId", finalBookingId);
        data.put("driverId", driverId != null ? driverId : (driver != null ? driver.getId().toString() : ""));
        data.put("rating", savedRating.getRating());
        data.put("review", savedRating.getReview() != null ? savedRating.getReview() : "");
        data.put("feedback", savedRating.getFeedback());
        data.put("newDriverAverageRating", newAverage);
        data.put("createdAt", createdAtIso);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("message", "Driver rating submitted successfully");
        response.put("data", data);

        return ResponseEntity.ok(response);
    }

    /**
     * Get Rating for a specific Booking
     * GET /api/bookings/{id}/rate or GET /api/ratings/booking/{bookingId}
     */
    @GetMapping({"/bookings/{id}/rate", "/ratings/booking/{id}"})
    public ResponseEntity<Map<String, Object>> getBookingRating(@PathVariable String id) {
        Optional<Rating> ratingOpt = ratingRepository.findByBookingId(id);
        if (ratingOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of(
                    "success", false,
                    "message", "No rating found for this booking"
            ));
        }

        Rating rating = ratingOpt.get();
        return ResponseEntity.ok(Map.of(
                "success", true,
                "rating", rating
        ));
    }

    /**
     * Get all ratings for a driver
     * GET /api/ratings/driver/{driverId}
     */
    @GetMapping("/ratings/driver/{driverId}")
    public ResponseEntity<Map<String, Object>> getDriverRatings(@PathVariable String driverId) {
        List<Rating> ratings = ratingRepository.findByDriverId(driverId);
        if (ratings.isEmpty()) {
            Optional<Driver> driverOpt = driverRepository.findByEmailIgnoreCase(driverId);
            if (driverOpt.isPresent()) {
                ratings = ratingRepository.findByDriverEmail(driverOpt.get().getEmail());
            }
        }

        return ResponseEntity.ok(Map.of(
                "success", true,
                "driverId", driverId,
                "ratings", ratings,
                "totalReviews", ratings.size()
        ));
    }
}
