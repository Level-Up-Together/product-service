package io.pinkspider.leveluptogethermvp.notificationservice.domain.entity;

import io.pinkspider.global.domain.auditentity.LocalDateTimeBaseEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.Comment;

/**
 * FCM 디바이스 토큰 엔티티
 * 사용자별 푸시 알림을 위한 디바이스 토큰 관리
 */
@Entity
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(name = "device_token",
    indexes = {
        @Index(name = "idx_device_token_user", columnList = "user_id"),
        @Index(name = "idx_device_token_token", columnList = "fcm_token", unique = true)
    })
@Comment("FCM 디바이스 토큰")
public class DeviceToken extends LocalDateTimeBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(name = "user_id", nullable = false)
    @Comment("사용자 ID")
    private String userId;

    @NotNull
    @Column(name = "fcm_token", nullable = false, unique = true, length = 512)
    @Comment("FCM 토큰")
    private String fcmToken;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "device_type", nullable = false, length = 20)
    @Comment("디바이스 타입 (IOS, ANDROID)")
    private DeviceType deviceType;

    @Column(name = "device_id", length = 255)
    @Comment("디바이스 고유 ID")
    private String deviceId;

    @Column(name = "device_name", length = 255)
    @Comment("디바이스 이름")
    private String deviceName;

    @Column(name = "app_version", length = 50)
    @Comment("앱 버전")
    private String appVersion;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    @Comment("활성화 여부")
    private Boolean isActive = true;

    @Builder.Default
    @Column(name = "badge_count", nullable = false)
    @Comment("배지 카운트")
    private Integer badgeCount = 0;

    /**
     * 디바이스 타입 Enum
     */
    public enum DeviceType {
        IOS,
        ANDROID
    }

    /**
     * 토큰 비활성화
     */
    public void deactivate() {
        this.isActive = false;
    }

    /**
     * 토큰 활성화
     */
    public void activate() {
        this.isActive = true;
    }

    /**
     * 배지 카운트 증가
     */
    public void incrementBadgeCount() {
        this.badgeCount++;
    }

    /**
     * 배지 카운트 초기화
     */
    public void resetBadgeCount() {
        this.badgeCount = 0;
    }

    /**
     * 토큰 업데이트
     */
    public void updateToken(String newToken) {
        this.fcmToken = newToken;
        this.isActive = true;
    }
}
