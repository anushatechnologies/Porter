package com.anushaporter.backend.repository;

import com.anushaporter.backend.model.Rating;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RatingRepository extends JpaRepository<Rating, Long> {
    Optional<Rating> findByBookingId(String bookingId);
    boolean existsByBookingId(String bookingId);
    List<Rating> findByDriverId(String driverId);
    List<Rating> findByDriverEmail(String driverEmail);
    List<Rating> findByCustomerEmailOrderByCreatedAtDesc(String customerEmail);
}
