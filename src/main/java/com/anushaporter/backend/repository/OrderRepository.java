package com.anushaporter.backend.repository;

import com.anushaporter.backend.model.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findByUserEmailOrderByCreatedAtDesc(String userEmail);
    List<Order> findByUserEmailAndStatusOrderByCreatedAtDesc(String userEmail, String status);
    Optional<Order> findByBookingId(String bookingId);
    List<Order> findAllByDriverEmailOrderByCreatedAtDesc(String driverEmail);
    List<Order> findAllByDriverEmailAndStatusInOrderByCreatedAtDesc(String driverEmail, List<String> statusList);
}
