package io.pinkspider.leveluptogethermvp.notificationservice.infrastructure;

import io.pinkspider.leveluptogethermvp.notificationservice.domain.entity.DeviceToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeviceTokenRepository extends JpaRepository<DeviceToken, Long> {

    /**
     * FCM 토큰으로 조회
     */
    Optional<DeviceToken> findByFcmToken(String fcmToken);

    /**
     * 사용자의 활성화된 토큰 목록 조회
     */
    List<DeviceToken> findByUserIdAndIsActiveTrue(String userId);

    /**
     * 사용자의 모든 토큰 조회
     */
    List<DeviceToken> findByUserId(String userId);

    /**
     * 여러 사용자의 활성화된 토큰 목록 조회
     */
    @Query("SELECT dt FROM DeviceToken dt WHERE dt.userId IN :userIds AND dt.isActive = true")
    List<DeviceToken> findActiveTokensByUserIds(@Param("userIds") List<String> userIds);

    /**
     * 디바이스 ID로 조회
     */
    Optional<DeviceToken> findByDeviceId(String deviceId);

    /**
     * 사용자의 특정 디바이스 토큰 조회
     */
    Optional<DeviceToken> findByUserIdAndDeviceId(String userId, String deviceId);

    /**
     * 사용자의 모든 토큰 비활성화
     */
    @Modifying
    @Query("UPDATE DeviceToken dt SET dt.isActive = false WHERE dt.userId = :userId")
    void deactivateAllByUserId(@Param("userId") String userId);

    /**
     * 특정 토큰 비활성화
     */
    @Modifying
    @Query("UPDATE DeviceToken dt SET dt.isActive = false WHERE dt.fcmToken = :fcmToken")
    void deactivateByFcmToken(@Param("fcmToken") String fcmToken);

    /**
     * 사용자의 배지 카운트 증가
     */
    @Modifying
    @Query("UPDATE DeviceToken dt SET dt.badgeCount = dt.badgeCount + 1 WHERE dt.userId = :userId AND dt.isActive = true")
    void incrementBadgeCountByUserId(@Param("userId") String userId);

    /**
     * 사용자의 배지 카운트 초기화
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE DeviceToken dt SET dt.badgeCount = 0 WHERE dt.userId = :userId")
    void resetBadgeCountByUserId(@Param("userId") String userId);

    /**
     * FCM 토큰 삭제
     */
    void deleteByFcmToken(String fcmToken);

    /**
     * 사용자의 모든 토큰 삭제
     */
    void deleteByUserId(String userId);
}
