package com.anushaporter.backend.repository;

import com.anushaporter.backend.model.BookingStop;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BookingStopRepository extends JpaRepository<BookingStop, Long> {
    List<BookingStop> findByBookingIdOrderByStopOrderAsc(Long bookingId);
}
