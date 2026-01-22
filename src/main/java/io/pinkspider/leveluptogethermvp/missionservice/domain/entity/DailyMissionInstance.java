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
        name = "uk_instance_participant_date",
        columnNames = {"participant_id", "instance_date"}
    ),
    indexes = {
        @Index(name = "idx_instance_user_date", columnList = "participant_id, instance_date"),
        @Index(name = "idx_instance_status", columnList = "status"),
        @Index(name = "idx_instance_feed", columnList = "feed_id")
    }
)
@Comment("고정 미션 일일 인스턴스")
public class DailyMissionInstance extends LocalDateTimeBaseEntity {

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
    @Comment("획득한 경험치 (시간 기반 계산)")
    @Builder.Default
    private Integer expEarned = 0;

    // ============ 기록 내용 ============

    @Column(name = "note", length = 500)
    @Comment("메모")
    private String note;

    @Column(name = "image_url", length = 500)
    @Comment("첨부 이미지 URL")
    private String imageUrl;

    // ============ 피드 연동 ============

    @Column(name = "feed_id")
    @Comment("연결된 피드 ID (1:1 관계)")
    private Long feedId;

    @Column(name = "is_shared_to_feed", nullable = false)
    @Comment("피드 공유 여부")
    @Builder.Default
    private Boolean isSharedToFeed = false;

    // ============ 낙관적 락 ============

    @Version
    @Column(name = "version")
    @Comment("낙관적 락 버전")
    private Long version;

    // ============ 비즈니스 로직 ============

    private static final long MINIMUM_EXECUTION_MINUTES = 1;

    /**
     * 미션 수행 시작
     */
    public void start() {
        if (this.status == ExecutionStatus.COMPLETED) {
            throw new IllegalStateException("이미 완료된 인스턴스입니다.");
        }
        if (this.status == ExecutionStatus.MISSED) {
            throw new IllegalStateException("미실행 처리된 인스턴스는 시작할 수 없습니다.");
        }
        if (this.startedAt != null) {
            throw new IllegalStateException("이미 시작된 인스턴스입니다.");
        }
        this.status = ExecutionStatus.IN_PROGRESS;
        this.startedAt = LocalDateTime.now();
    }

    /**
     * 미션 수행 완료 및 경험치 계산 (분당 1 EXP)
     */
    public void complete() {
        if (this.status == ExecutionStatus.COMPLETED) {
            throw new IllegalStateException("이미 완료된 인스턴스입니다.");
        }
        if (this.status == ExecutionStatus.MISSED) {
            throw new IllegalStateException("미실행 처리된 인스턴스는 완료할 수 없습니다.");
        }
        if (this.startedAt == null) {
            throw new IllegalStateException("미션을 먼저 시작해야 합니다.");
        }

        LocalDateTime now = LocalDateTime.now();
        long elapsedMinutes = Duration.between(this.startedAt, now).toMinutes();
        if (elapsedMinutes < MINIMUM_EXECUTION_MINUTES) {
            throw new IllegalStateException("1분 이하는 기록할 수 없습니다.");
        }

        this.status = ExecutionStatus.COMPLETED;
        this.completedAt = now;
        this.expEarned = calculateExpByDuration();
    }

    /**
     * 시작~종료 시간을 분으로 계산하여 경험치 반환 (분당 1 EXP)
     */
    public int calculateExpByDuration() {
        if (this.startedAt == null || this.completedAt == null) {
            return 0;
        }
        long durationMinutes = Duration.between(this.startedAt, this.completedAt).toMinutes();
        return (int) Math.max(1, Math.min(durationMinutes, 480));
    }

    /**
     * 미실행 처리 (마감 시간 경과)
     */
    public void markAsMissed() {
        if (this.status == ExecutionStatus.COMPLETED) {
            throw new IllegalStateException("완료된 인스턴스는 미실행 처리할 수 없습니다.");
        }
        this.status = ExecutionStatus.MISSED;
    }

    /**
     * 진행 취소 (PENDING 상태로 되돌림)
     */
    public void skip() {
        if (this.status == ExecutionStatus.COMPLETED) {
            throw new IllegalStateException("완료된 인스턴스는 취소할 수 없습니다.");
        }
        if (this.status == ExecutionStatus.MISSED) {
            throw new IllegalStateException("미실행 처리된 인스턴스는 취소할 수 없습니다.");
        }
        this.status = ExecutionStatus.PENDING;
        this.startedAt = null;
    }

    /**
     * 피드 공유 처리
     */
    public void shareToFeed(Long feedId) {
        this.feedId = feedId;
        this.isSharedToFeed = true;
    }

    /**
     * 피드 공유 취소
     */
    public void unshareFromFeed() {
        this.feedId = null;
        this.isSharedToFeed = false;
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

    /**
     * 미션 참여자와 미션 정보로부터 일일 인스턴스 생성
     */
    public static DailyMissionInstance createFrom(MissionParticipant participant, LocalDate date) {
        Mission mission = participant.getMission();
        String categoryName = (mission.getCategory() != null) ? mission.getCategory().getName() : mission.getCustomCategory();
        Long categoryId = (mission.getCategory() != null) ? mission.getCategory().getId() : null;

        return DailyMissionInstance.builder()
            .participant(participant)
            .instanceDate(date)
            .missionTitle(mission.getTitle())
            .missionDescription(mission.getDescription())
            .categoryName(categoryName)
            .categoryId(categoryId)
            .expPerCompletion(mission.getExpPerCompletion())
            .status(ExecutionStatus.PENDING)
            .expEarned(0)
            .isSharedToFeed(false)
            .build();
    }
}
