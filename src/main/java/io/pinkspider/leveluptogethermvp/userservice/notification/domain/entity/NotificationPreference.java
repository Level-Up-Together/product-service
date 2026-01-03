package io.pinkspider.leveluptogethermvp.userservice.notification.domain.entity;

import io.pinkspider.global.domain.auditentity.LocalDateTimeBaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.Comment;

@Entity
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(name = "notification_preference",
    indexes = @Index(name = "idx_notification_pref_user", columnList = "user_id", unique = true))
@Comment("알림 설정")
public class NotificationPreference extends LocalDateTimeBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    @Comment("ID")
    private Long id;

    @NotNull
    @Column(name = "user_id", nullable = false, unique = true)
    @Comment("사용자 ID")
    private String userId;

    @Column(name = "push_enabled")
    @Comment("푸시 알림 활성화")
    @Builder.Default
    private Boolean pushEnabled = true;

    @Column(name = "friend_notifications")
    @Comment("친구 알림")
    @Builder.Default
    private Boolean friendNotifications = true;

    @Column(name = "guild_notifications")
    @Comment("길드 알림")
    @Builder.Default
    private Boolean guildNotifications = true;

    @Column(name = "social_notifications")
    @Comment("소셜 알림 (댓글)")
    @Builder.Default
    private Boolean socialNotifications = true;

    @Column(name = "system_notifications")
    @Comment("시스템 알림")
    @Builder.Default
    private Boolean systemNotifications = true;

    @Column(name = "quiet_hours_enabled")
    @Comment("방해금지 시간 활성화")
    @Builder.Default
    private Boolean quietHoursEnabled = false;

    @Column(name = "quiet_hours_start", length = 5)
    @Comment("방해금지 시작 시간 (HH:mm)")
    private String quietHoursStart;

    @Column(name = "quiet_hours_end", length = 5)
    @Comment("방해금지 종료 시간 (HH:mm)")
    private String quietHoursEnd;

    public static NotificationPreference createDefault(String userId) {
        return NotificationPreference.builder()
            .userId(userId)
            .build();
    }

    public boolean isCategoryEnabled(String category) {
        return switch (category) {
            case "FRIEND" -> friendNotifications;
            case "GUILD" -> guildNotifications;
            case "SOCIAL" -> socialNotifications;
            case "SYSTEM" -> systemNotifications;
            default -> true;
        };
    }
}
