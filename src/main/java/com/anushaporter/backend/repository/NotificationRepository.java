package com.anushaporter.backend.repository;

import com.anushaporter.backend.model.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByUserIdOrderByCreatedAtDesc(Long userId);
    Optional<Notification> findByIdAndUserId(Long id, Long userId);
    List<Notification> findByBookingIdAndNotificationType(String bookingId, String notificationType);
    List<Notification> findByUserIdAndBookingIdAndNotificationType(Long userId, String bookingId, String notificationType);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("UPDATE Notification n SET n.readStatus = true WHERE n.bookingId = :bookingId AND n.notificationType = :type AND (:excludeUserId IS NULL OR n.userId != :excludeUserId)")
    int dismissNotificationsForBooking(@Param("bookingId") String bookingId, @Param("type") String type, @Param("excludeUserId") Long excludeUserId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("UPDATE Notification n SET n.readStatus = true WHERE n.bookingId = :bookingId AND n.userId = :userId")
    int dismissDriverNotificationForBooking(@Param("bookingId") String bookingId, @Param("userId") Long userId);
}
