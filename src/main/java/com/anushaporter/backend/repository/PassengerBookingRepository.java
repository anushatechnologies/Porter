package com.anushaporter.backend.repository;

import com.anushaporter.backend.model.PassengerBooking;
import com.anushaporter.backend.model.PassengerBookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PassengerBookingRepository extends JpaRepository<PassengerBooking, Long> {

    Optional<PassengerBooking> findByBookingNumber(String bookingNumber);

    List<PassengerBooking> findByCustomerPhoneOrderByCreatedAtDesc(String customerPhone);

    List<PassengerBooking> findByCustomerIdOrderByCreatedAtDesc(Long customerId);

    List<PassengerBooking> findByCustomerEmailOrderByCreatedAtDesc(String customerEmail);

    @Query("SELECT b FROM PassengerBooking b WHERE " +
           "(:phone IS NOT NULL AND b.customerPhone = :phone) OR " +
           "(:email IS NOT NULL AND b.customerEmail = :email) OR " +
           "(:customerId IS NOT NULL AND b.customerId = :customerId) " +
           "ORDER BY b.createdAt DESC")
    List<PassengerBooking> findForCustomer(
            @Param("phone") String phone,
            @Param("email") String email,
            @Param("customerId") Long customerId
    );

    List<PassengerBooking> findByDriverIdOrderByCreatedAtDesc(Long driverId);

    List<PassengerBooking> findByStatusOrderByCreatedAtDesc(PassengerBookingStatus status);

    List<PassengerBooking> findAllByOrderByCreatedAtDesc();

    @Query("SELECT b FROM PassengerBooking b WHERE " +
           "(:status IS NULL OR b.status = :status) AND " +
           "(:serviceType IS NULL OR b.serviceType = :serviceType) AND " +
           "(:vehicleCategory IS NULL OR b.vehicleCategoryCode = :vehicleCategory) " +
           "ORDER BY b.createdAt DESC")
    List<PassengerBooking> filterBookings(
            @Param("status") PassengerBookingStatus status,
            @Param("serviceType") String serviceType,
            @Param("vehicleCategory") String vehicleCategory
    );

    long countByStatus(PassengerBookingStatus status);

    long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    @Query("SELECT COALESCE(SUM(b.fareBreakdown.totalFare), 0) FROM PassengerBooking b WHERE b.paymentStatus = 'PAID' OR b.status = 'TRIP_COMPLETED'")
    java.math.BigDecimal calculateTotalRevenue();

    @Query("SELECT COALESCE(SUM(b.fareBreakdown.driverEarnings), 0) FROM PassengerBooking b WHERE b.paymentStatus = 'PAID' OR b.status = 'TRIP_COMPLETED'")
    java.math.BigDecimal calculateTotalDriverEarnings();

    @Query("SELECT COALESCE(SUM(b.fareBreakdown.companyCommission), 0) FROM PassengerBooking b WHERE b.paymentStatus = 'PAID' OR b.status = 'TRIP_COMPLETED'")
    java.math.BigDecimal calculateTotalCompanyCommission();
}
