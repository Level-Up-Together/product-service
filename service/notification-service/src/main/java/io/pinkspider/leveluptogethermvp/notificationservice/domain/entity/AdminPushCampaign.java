package io.pinkspider.leveluptogethermvp.notificationservice.domain.entity;

import io.pinkspider.global.domain.auditentity.LocalDateTimeBaseEntity;
import io.pinkspider.leveluptogethermvp.notificationservice.domain.enums.AdminPushCampaignStatus;
import io.pinkspider.leveluptogethermvp.notificationservice.domain.enums.AdminPushTargetType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;

/**
 * LUT-508: 관리자 푸시 발송 이력(캠페인).
 *
 * <p>어드민이 입력한 제목/본문/링크와 대상, 발송 결과 건수를 남긴다. 발송 자체는 유저별 {@code Notification}
 * 생성(기존 파이프라인)으로 이뤄지므로, 이 행은 "무엇을 누구에게 보냈고 몇 명에게 만들어졌는가"의 원장이다.
 * 상태는 이력 행 생성(PENDING) → 비동기 발송 시작(SENDING) → COMPLETED/FAILED 로 단조 진행한다.
 */
@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(name = "admin_push_campaign")
@Comment("관리자 푸시 발송 이력 (LUT-508)")
public class AdminPushCampaign extends LocalDateTimeBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @NotNull
    @Column(name = "title", nullable = false, length = 100)
    @Comment("푸시 제목 (Notification.title 과 동일 상한)")
    private String title;

    @NotNull
    @Column(name = "body", nullable = false, length = 500)
    @Comment("푸시 본문 (Notification.message 와 동일 상한)")
    private String body;

    @Column(name = "action_url", length = 500)
    @Comment("탭 시 이동 경로 (앱 내 경로, 없으면 null)")
    private String actionUrl;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 20)
    @Comment("대상 유형 (ALL|USERS)")
    private AdminPushTargetType targetType;

    @Column(name = "target_user_ids", columnDefinition = "TEXT")
    @Comment("USERS 대상 유저 ID JSON 배열 — ALL 은 null")
    private String targetUserIds;

    @NotNull
    @Column(name = "target_count", nullable = false)
    @Comment("발송 대상 유저 수")
    private Integer targetCount;

    @NotNull
    @Column(name = "sent_count", nullable = false)
    @Comment("알림 생성 성공 수 (푸시 파이프라인 진입)")
    private Integer sentCount;

    @NotNull
    @Column(name = "skipped_count", nullable = false)
    @Comment("스킵 수 (유저가 시스템 알림 카테고리를 껐거나 생성 대상이 아님)")
    private Integer skippedCount;

    @NotNull
    @Column(name = "failed_count", nullable = false)
    @Comment("생성 실패 수 (예외)")
    private Integer failedCount;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Comment("상태 (PENDING|SENDING|COMPLETED|FAILED)")
    private AdminPushCampaignStatus status;

    @NotNull
    @Column(name = "requested_by", nullable = false)
    @Comment("발송 요청 관리자 ID (admin_db admin.id)")
    private Long requestedBy;

    @Column(name = "started_at")
    @Comment("발송 시작 시각")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    @Comment("발송 종료 시각 (완료/실패)")
    private LocalDateTime completedAt;

    @Column(name = "error_message", length = 500)
    @Comment("실패 사유 (FAILED 일 때)")
    private String errorMessage;

    public static AdminPushCampaign create(
            String title,
            String body,
            String actionUrl,
            AdminPushTargetType targetType,
            String targetUserIds,
            int targetCount,
            Long requestedBy) {
        return AdminPushCampaign.builder()
                .title(title)
                .body(body)
                .actionUrl(actionUrl)
                .targetType(targetType)
                .targetUserIds(targetUserIds)
                .targetCount(targetCount)
                .sentCount(0)
                .skippedCount(0)
                .failedCount(0)
                .status(AdminPushCampaignStatus.PENDING)
                .requestedBy(requestedBy)
                .build();
    }

    public boolean isPending() {
        return status == AdminPushCampaignStatus.PENDING;
    }

    public void markSending(LocalDateTime now) {
        this.status = AdminPushCampaignStatus.SENDING;
        this.startedAt = now;
    }

    public void complete(int sent, int skipped, int failed, LocalDateTime now) {
        this.sentCount = sent;
        this.skippedCount = skipped;
        this.failedCount = failed;
        this.status = AdminPushCampaignStatus.COMPLETED;
        this.completedAt = now;
    }

    public void fail(int sent, int skipped, int failed, String message, LocalDateTime now) {
        this.sentCount = sent;
        this.skippedCount = skipped;
        this.failedCount = failed;
        this.status = AdminPushCampaignStatus.FAILED;
        this.errorMessage = message != null && message.length() > 500 ? message.substring(0, 500) : message;
        this.completedAt = now;
    }
}
