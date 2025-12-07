package io.pinkspider.leveluptogethermvp.userservice.notification.domain.entity;

import io.pinkspider.global.domain.auditentity.LocalDateTimeBaseEntity;
import io.pinkspider.leveluptogethermvp.userservice.notification.domain.enums.NotificationType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
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
@Table(name = "notification",
    indexes = {
        @Index(name = "idx_notification_user", columnList = "user_id"),
        @Index(name = "idx_notification_user_read", columnList = "user_id, is_read"),
        @Index(name = "idx_notification_created", columnList = "created_at DESC")
    })
@Comment("알림")
public class Notification extends LocalDateTimeBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    @Comment("ID")
    private Long id;

    @NotNull
    @Column(name = "user_id", nullable = false)
    @Comment("사용자 ID")
    private String userId;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false, length = 30)
    @Comment("알림 타입")
    private NotificationType notificationType;

    @NotNull
    @Column(name = "title", nullable = false, length = 100)
    @Comment("알림 제목")
    private String title;

    @Column(name = "message", length = 500)
    @Comment("알림 메시지")
    private String message;

    @Column(name = "reference_type", length = 30)
    @Comment("참조 타입 (MISSION, ACHIEVEMENT, GUILD 등)")
    private String referenceType;

    @Column(name = "reference_id")
    @Comment("참조 ID")
    private Long referenceId;

    @Column(name = "action_url", length = 500)
    @Comment("클릭 시 이동 URL")
    private String actionUrl;

    @Column(name = "icon_url", length = 500)
    @Comment("아이콘 URL")
    private String iconUrl;

    @Column(name = "is_read")
    @Comment("읽음 여부")
    @Builder.Default
    private Boolean isRead = false;

    @Column(name = "read_at")
    @Comment("읽은 시간")
    private LocalDateTime readAt;

    @Column(name = "is_pushed")
    @Comment("푸시 발송 여부")
    @Builder.Default
    private Boolean isPushed = false;

    @Column(name = "pushed_at")
    @Comment("푸시 발송 시간")
    private LocalDateTime pushedAt;

    @Column(name = "expires_at")
    @Comment("만료 시간")
    private LocalDateTime expiresAt;

    public void markAsRead() {
        if (!this.isRead) {
            this.isRead = true;
            this.readAt = LocalDateTime.now();
        }
    }

    public void markAsPushed() {
        this.isPushed = true;
        this.pushedAt = LocalDateTime.now();
    }

    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }

    public String getCategory() {
        return notificationType.getCategory();
    }
}
