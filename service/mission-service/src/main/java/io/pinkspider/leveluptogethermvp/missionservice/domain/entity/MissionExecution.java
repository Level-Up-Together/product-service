package io.pinkspider.leveluptogethermvp.missionservice.domain.entity;

import io.pinkspider.global.domain.auditentity.LocalDateTimeBaseEntity;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.ExecutionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
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
@Table(name = "mission_execution",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_execution_participant_date",
        columnNames = {"participant_id", "execution_date"}
    )
)
@Comment("미션 수행 기록")
public class MissionExecution extends LocalDateTimeBaseEntity implements MissionExecutionLifecycle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    @Comment("수행 기록 ID")
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "participant_id", nullable = false)
    @Comment("참여자")
    private MissionParticipant participant;

    @NotNull
    @Column(name = "execution_date", nullable = false)
    @Comment("수행 예정 일자")
    private LocalDate executionDate;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Comment("수행 상태")
    @Builder.Default
    private ExecutionStatus status = ExecutionStatus.PENDING;

    @Column(name = "started_at")
    @Comment("시작 일시")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    @Comment("완료 일시")
    private LocalDateTime completedAt;

    @Column(name = "exp_earned")
    @Comment("획득한 경험치")
    @Builder.Default
    private Integer expEarned = 0;

    @Column(name = "note", length = 500)
    @Comment("메모")
    private String note;

    @Column(name = "image_url", length = 500)
    @Comment("첨부 이미지 URL")
    private String imageUrl;

    @Column(name = "is_shared_to_feed", nullable = false)
    @Comment("피드 공유 여부")
    @Builder.Default
    private Boolean isSharedToFeed = false;

    @Column(name = "is_auto_completed", nullable = false)
    @Comment("자동 종료 여부 (2시간 초과)")
    @Builder.Default
    private Boolean isAutoCompleted = false;

    @Version
    @Column(name = "version")
    @Comment("낙관적 락 버전")
    private Long version;

    // === MissionExecutionLifecycle 구현 ===

    @Override
    public io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionExecutionMode getExecutionMode() {
        if (this.participant != null && this.participant.getMission() != null) {
            var mode = this.participant.getMission().getExecutionMode();
            return mode != null ? mode : io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionExecutionMode.TIMED;
        }
        return io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionExecutionMode.TIMED;
    }

    /**
     * 경험치 계산: 분당 1 EXP, 최소 1분, 최대 480분(8시간)
     */
    @Override
    public int calculateExpByDuration() {
        if (this.startedAt == null || this.completedAt == null) {
            return 0;
        }
        long durationMinutes = java.time.Duration.between(this.startedAt, this.completedAt).toMinutes();
        return (int) Math.max(1, Math.min(durationMinutes, 480));
    }

    // start(), complete(), skip(), markAsMissed(), shareToFeed(), unshareFromFeed(),
    // isExpired(), autoCompleteIfExpired(), autoCompleteForDateChange()
    // → MissionExecutionLifecycle 인터페이스의 default 메서드 사용
}
