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
import jakarta.persistence.Index;
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

/**
 * 고정 미션(pinned mission)의 일일 인스턴스
 *
 * 고정 미션은 매일 새로운 인스턴스가 생성되어 독립적으로 관리됩니다.
 * 미션 정보는 스냅샷으로 저장되어 원본 미션 변경에 영향받지 않습니다.
 */
@Entity
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(name = "daily_mission_instance",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_instance_participant_date_seq",
        columnNames = {"participant_id", "instance_date", "sequence_number"}
    ),
    indexes = {
        @Index(name = "idx_instance_user_date", columnList = "participant_id, instance_date"),
        @Index(name = "idx_instance_status", columnList = "status")
    }
)
@Comment("고정 미션 일일 인스턴스")
public class DailyMissionInstance extends LocalDateTimeBaseEntity implements MissionExecutionLifecycle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    @Comment("인스턴스 ID")
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "participant_id", nullable = false)
    @Comment("미션 참여자")
    private MissionParticipant participant;

    @NotNull
    @Column(name = "instance_date", nullable = false)
    @Comment("인스턴스 날짜 (해당 일자의 미션)")
    private LocalDate instanceDate;

    @NotNull
    @Column(name = "sequence_number", nullable = false)
    @Comment("당일 수행 순번 (1부터 시작)")
    @Builder.Default
    private Integer sequenceNumber = 1;

    // ============ 미션 정보 스냅샷 (원본 미션 변경에 영향받지 않음) ============

    @NotNull
    @Column(name = "mission_title", nullable = false, length = 200)
    @Comment("미션 제목 (스냅샷)")
    private String missionTitle;

    @Column(name = "mission_description", length = 1000)
    @Comment("미션 설명 (스냅샷)")
    private String missionDescription;

    @Column(name = "category_name", length = 100)
    @Comment("카테고리 이름 (스냅샷)")
    private String categoryName;

    @Column(name = "category_id")
    @Comment("카테고리 ID (통계/필터용)")
    private Long categoryId;

    @Column(name = "exp_per_completion")
    @Comment("완료 시 기본 경험치 (스냅샷)")
    @Builder.Default
    private Integer expPerCompletion = 0;

    @Column(name = "target_duration_minutes")
    @Comment("목표 수행 시간 (분) - 스냅샷")
    private Integer targetDurationMinutes;

    // ============ 수행 상태 ============

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
    @Comment("획득한 경험치 (현재 회차, 시간 기반 계산)")
    @Builder.Default
    private Integer expEarned = 0;

    @Column(name = "completion_count")
    @Comment("오늘 완료 횟수 (고정 미션용)")
    @Builder.Default
    private Integer completionCount = 0;

    @Column(name = "total_exp_earned")
    @Comment("오늘 획득한 총 경험치 (고정 미션용)")
    @Builder.Default
    private Integer totalExpEarned = 0;

    // ============ 기록 내용 ============

    @Column(name = "note", length = 500)
    @Comment("메모")
    private String note;

    @Column(name = "image_url", length = 500)
    @Comment("첨부 이미지 URL")
    private String imageUrl;

    // ============ 피드 연동 ============

    @Column(name = "is_shared_to_feed", nullable = false)
    @Comment("피드 공유 여부")
    @Builder.Default
    private Boolean isSharedToFeed = false;

    @Column(name = "is_auto_completed", nullable = false)
    @Comment("자동 종료 여부 (2시간 초과)")
    @Builder.Default
    private Boolean isAutoCompleted = false;

    // ============ 낙관적 락 ============

    @Version
    @Column(name = "version")
    @Comment("낙관적 락 버전")
    private Long version;

    // === MissionExecutionLifecycle 훅 구현 ===

    /**
     * 완료 후처리: completionCount, totalExpEarned 갱신
     */
    @Override
    public void onComplete() {
        this.completionCount = (this.completionCount == null ? 0 : this.completionCount) + 1;
        this.totalExpEarned = (this.totalExpEarned == null ? 0 : this.totalExpEarned) + this.expEarned;
    }

    /**
     * 자동 완료 후처리: completionCount, totalExpEarned 갱신
     */
    @Override
    public void onAutoComplete() {
        this.completionCount = (this.completionCount == null ? 0 : this.completionCount) + 1;
        this.totalExpEarned = (this.totalExpEarned == null ? 0 : this.totalExpEarned) + this.expEarned;
    }

    /**
     * 자동 완료 가능 여부: 목표시간 설정 미션은 스케줄러가 Saga를 통해 별도 처리
     */
    @Override
    public io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionExecutionMode getExecutionMode() {
        if (this.participant != null && this.participant.getMission() != null) {
            var mode = this.participant.getMission().getExecutionMode();
            return mode != null ? mode : io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionExecutionMode.TIMED;
        }
        return io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionExecutionMode.TIMED;
    }

    @Override
    public boolean shouldAutoComplete() {
        if (getExecutionMode() == io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionExecutionMode.SIMPLE) {
            return false;
        }
        return this.targetDurationMinutes == null || this.targetDurationMinutes <= 0;
    }

    /**
     * 경험치 계산: 목표시간 기반 보너스 포함
     *
     * 목표시간(targetDurationMinutes) 설정 시:
     * - 목표시간 달성: targetDurationMinutes + expPerCompletion (보너스)
     * - 목표시간 미달: 실제 수행 분 (1분=1XP)
     *
     * 목표시간 미설정 시: 분당 1 EXP, 최대 480
     */
    @Override
    public int calculateExpByDuration() {
        if (this.startedAt == null || this.completedAt == null) {
            return 0;
        }
        long elapsed = Duration.between(this.startedAt, this.completedAt).toMinutes();

        if (this.targetDurationMinutes != null && this.targetDurationMinutes > 0) {
            if (elapsed >= this.targetDurationMinutes) {
                int bonus = this.expPerCompletion != null ? this.expPerCompletion : 0;
                return this.targetDurationMinutes + bonus;
            }
            return (int) Math.max(1, elapsed);
        }
        return (int) Math.max(1, Math.min(elapsed, 480));
    }

    // start(), complete(), skip(), markAsMissed(), shareToFeed(), unshareFromFeed(),
    // isExpired(), autoCompleteIfExpired(), autoCompleteForDateChange()
    // → MissionExecutionLifecycle 인터페이스의 default 메서드 사용

    // === 고정 미션 전용 메서드 ===

    /**
     * 완료된 인스턴스를 PENDING 상태로 리셋 (고정 미션의 재시작용)
     * completionCount, totalExpEarned는 유지하여 오늘 수행 내역 기록
     *
     * NOTE: 이 메서드는 Saga Step에서만 호출되며,
     * MissionExecutionLifecycle의 상태 전이 규칙을 우회합니다.
     */
    public void resetToPending() {
        if (this.status != ExecutionStatus.COMPLETED) {
            throw new IllegalStateException("완료 상태의 인스턴스만 리셋할 수 있습니다.");
        }
        this.status = ExecutionStatus.PENDING;
        this.startedAt = null;
        this.completedAt = null;
        this.expEarned = 0;
        this.note = null;
        this.imageUrl = null;
    }

    /**
     * 수행 시간 (분) 계산
     */
    public Integer getDurationMinutes() {
        if (this.startedAt == null || this.completedAt == null) {
            return null;
        }
        return (int) Duration.between(this.startedAt, this.completedAt).toMinutes();
    }

    // ============ 팩토리 메서드 ============

    public static DailyMissionInstance createFrom(MissionParticipant participant, LocalDate date) {
        return createFrom(participant, date, 1);
    }

    public static DailyMissionInstance createFrom(MissionParticipant participant, LocalDate date, int sequenceNumber) {
        Mission mission = participant.getMission();
        String categoryName = mission.getCategoryName();
        Long categoryId = mission.getCategoryId();

        return DailyMissionInstance.builder()
            .participant(participant)
            .instanceDate(date)
            .sequenceNumber(sequenceNumber)
            .missionTitle(mission.getTitle())
            .missionDescription(mission.getDescription())
            .categoryName(categoryName)
            .categoryId(categoryId)
            .expPerCompletion(mission.getExpPerCompletion())
            .targetDurationMinutes(mission.getTargetDurationMinutes())
            .status(ExecutionStatus.PENDING)
            .expEarned(0)
            .completionCount(0)
            .totalExpEarned(0)
            .isSharedToFeed(false)
            .build();
    }
}
