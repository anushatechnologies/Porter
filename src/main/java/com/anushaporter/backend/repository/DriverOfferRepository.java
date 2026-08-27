package com.anushaporter.backend.repository;

import com.anushaporter.backend.model.DriverOffer;
import com.anushaporter.backend.model.DriverOfferStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface DriverOfferRepository extends JpaRepository<DriverOffer, Long> {

    List<DriverOffer> findByBookingId(String bookingId);

    List<DriverOffer> findByBookingIdAndStatus(String bookingId, DriverOfferStatus status);

    List<DriverOffer> findByDriverIdAndStatusIn(Long driverId, List<DriverOfferStatus> statuses);

    Optional<DriverOffer> findFirstByBookingIdAndDriverIdOrderByIdDesc(String bookingId, Long driverId);

    @Query("SELECT DISTINCT o.driverId FROM DriverOffer o WHERE o.bookingId = :bookingId")
    List<Long> findAllDriverIdsOfferedForBooking(@Param("bookingId") String bookingId);

    @Query("SELECT o FROM DriverOffer o WHERE o.driverId = :driverId AND o.status = 'OFFERED' AND (o.expiresAt IS NULL OR o.expiresAt > :now)")
    List<DriverOffer> findActiveOffersForDriver(@Param("driverId") Long driverId, @Param("now") LocalDateTime now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("UPDATE DriverOffer o SET o.status = 'TOO_LATE', o.respondedAt = :now WHERE o.bookingId = :bookingId AND o.driverId != :winningDriverId AND o.status = 'OFFERED'")
    int markCompetingOffersTooLate(
            @Param("bookingId") String bookingId,
            @Param("winningDriverId") Long winningDriverId,
            @Param("now") LocalDateTime now
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("UPDATE DriverOffer o SET o.status = 'EXPIRED' WHERE o.status = 'OFFERED' AND o.expiresAt IS NOT NULL AND o.expiresAt <= :now")
    int expirePendingOffers(@Param("now") LocalDateTime now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("UPDATE DriverOffer o SET o.status = 'CANCELLED', o.respondedAt = :now WHERE o.bookingId = :bookingId AND o.status = 'OFFERED'")
    int cancelAllPendingOffersForBooking(@Param("bookingId") String bookingId, @Param("now") LocalDateTime now);
}
