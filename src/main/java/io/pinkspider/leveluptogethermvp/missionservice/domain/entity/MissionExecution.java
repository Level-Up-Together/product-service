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
import java.time.Duration;
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
public class MissionExecution extends LocalDateTimeBaseEntity {

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

    @Version
    @Column(name = "version")
    @Comment("낙관적 락 버전")
    private Long version;

    // 최소 미션 수행 시간 (분) - 어뷰징 방지
    private static final long MINIMUM_EXECUTION_MINUTES = 1;

    /**
     * 미션 수행 시작
     */
    public void start() {
        if (this.status == ExecutionStatus.COMPLETED) {
            throw new IllegalStateException("이미 완료된 수행 기록입니다.");
        }
        if (this.status == ExecutionStatus.MISSED) {
            throw new IllegalStateException("미실행 처리된 수행 기록은 시작할 수 없습니다.");
        }
        if (this.startedAt != null) {
            throw new IllegalStateException("이미 시작된 수행 기록입니다.");
        }
        this.status = ExecutionStatus.IN_PROGRESS;
        this.startedAt = LocalDateTime.now();
    }

    /**
     * 미션 수행 완료 및 경험치 계산 (분당 1 EXP)
     * 최소 수행 시간 검증으로 어뷰징 방지
     */
    public void complete() {
        if (this.status == ExecutionStatus.COMPLETED) {
            throw new IllegalStateException("이미 완료된 수행 기록입니다.");
        }
        if (this.status == ExecutionStatus.MISSED) {
            throw new IllegalStateException("미실행 처리된 수행 기록은 완료할 수 없습니다.");
        }
        if (this.startedAt == null) {
            throw new IllegalStateException("미션을 먼저 시작해야 합니다.");
        }

        // 최소 수행 시간 검증 (어뷰징 방지)
        LocalDateTime now = LocalDateTime.now();
        long elapsedMinutes = Duration.between(this.startedAt, now).toMinutes();
        if (elapsedMinutes < MINIMUM_EXECUTION_MINUTES) {
            throw new IllegalStateException(
                String.format("최소 %d분 이상 수행해야 미션을 완료할 수 있습니다. (현재: %d분 경과)",
                    MINIMUM_EXECUTION_MINUTES, elapsedMinutes));
        }

        this.status = ExecutionStatus.COMPLETED;
        this.completedAt = now;
        // 경험치 계산: 시작~종료 시간을 분으로 계산하여 분당 1 EXP
        this.expEarned = calculateExpByDuration();
    }

    /**
     * 시작~종료 시간을 분으로 계산하여 경험치 반환 (분당 1 EXP)
     */
    public int calculateExpByDuration() {
        if (this.startedAt == null || this.completedAt == null) {
            return 0;
        }
        long durationMinutes = java.time.Duration.between(this.startedAt, this.completedAt).toMinutes();
        // 최소 1분, 최대 480분(8시간) 제한
        return (int) Math.max(1, Math.min(durationMinutes, 480));
    }

    public void markAsMissed() {
        if (this.status == ExecutionStatus.COMPLETED) {
            throw new IllegalStateException("완료된 수행 기록은 미실행 처리할 수 없습니다.");
        }
        this.status = ExecutionStatus.MISSED;
    }

    /**
     * 진행 중인 미션 취소 (PENDING 상태로 되돌림)
     */
    public void skip() {
        if (this.status == ExecutionStatus.COMPLETED) {
            throw new IllegalStateException("완료된 수행 기록은 취소할 수 없습니다.");
        }
        if (this.status == ExecutionStatus.MISSED) {
            throw new IllegalStateException("미실행 처리된 수행 기록은 취소할 수 없습니다.");
        }
        // PENDING 상태로 되돌리고 시작 시간 초기화
        this.status = ExecutionStatus.PENDING;
        this.startedAt = null;
    }

    public void setExpEarned(int exp) {
        this.expEarned = exp;
    }
}
