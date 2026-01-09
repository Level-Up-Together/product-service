package io.pinkspider.leveluptogethermvp.notificationservice.infrastructure;

import io.pinkspider.leveluptogethermvp.notificationservice.domain.entity.Notification;
import io.pinkspider.leveluptogethermvp.notificationservice.domain.enums.NotificationType;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    @Query("SELECT n FROM Notification n WHERE n.userId = :userId " +
           "AND (n.expiresAt IS NULL OR n.expiresAt > CURRENT_TIMESTAMP) " +
           "ORDER BY n.createdAt DESC")
    Page<Notification> findByUserIdOrderByCreatedAtDesc(
        @Param("userId") String userId, Pageable pageable);

    @Query("SELECT n FROM Notification n WHERE n.userId = :userId AND n.isRead = false " +
           "AND (n.expiresAt IS NULL OR n.expiresAt > CURRENT_TIMESTAMP) " +
           "ORDER BY n.createdAt DESC")
    List<Notification> findUnreadByUserId(@Param("userId") String userId);

    @Query("SELECT COUNT(n) FROM Notification n WHERE n.userId = :userId AND n.isRead = false " +
           "AND (n.expiresAt IS NULL OR n.expiresAt > CURRENT_TIMESTAMP)")
    int countUnreadByUserId(@Param("userId") String userId);

    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true, n.readAt = CURRENT_TIMESTAMP " +
           "WHERE n.userId = :userId AND n.isRead = false")
    int markAllAsRead(@Param("userId") String userId);

    @Modifying
    @Query("DELETE FROM Notification n WHERE n.expiresAt IS NOT NULL AND n.expiresAt < :now")
    int deleteExpiredNotifications(@Param("now") LocalDateTime now);

    @Query("SELECT n FROM Notification n WHERE n.userId = :userId " +
           "AND n.notificationType = :type " +
           "ORDER BY n.createdAt DESC")
    Page<Notification> findByUserIdAndType(
        @Param("userId") String userId,
        @Param("type") NotificationType type,
        Pageable pageable);

    @Query("SELECT n FROM Notification n WHERE n.userId = :userId " +
           "AND n.notificationType IN :types " +
           "AND (n.expiresAt IS NULL OR n.expiresAt > CURRENT_TIMESTAMP) " +
           "ORDER BY n.createdAt DESC")
    Page<Notification> findByUserIdAndTypeIn(
        @Param("userId") String userId,
        @Param("types") List<NotificationType> types,
        Pageable pageable);

    @Modifying
    @Query("DELETE FROM Notification n WHERE n.userId = :userId " +
           "AND n.referenceType = :referenceType " +
           "AND n.referenceId = :referenceId")
    int deleteByUserIdAndReference(
        @Param("userId") String userId,
        @Param("referenceType") String referenceType,
        @Param("referenceId") Long referenceId);

    @Modifying
    @Query("DELETE FROM Notification n WHERE n.referenceType = :referenceType " +
           "AND n.referenceId = :referenceId")
    int deleteByReference(
        @Param("referenceType") String referenceType,
        @Param("referenceId") Long referenceId);
}
