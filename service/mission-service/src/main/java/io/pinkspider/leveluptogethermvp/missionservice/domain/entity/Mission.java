package io.pinkspider.leveluptogethermvp.missionservice.domain.entity;

import io.pinkspider.global.domain.auditentity.LocalDateTimeBaseEntity;
import io.pinkspider.global.translation.LocaleUtils;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionInterval;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionParticipationType;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionExecutionMode;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionSource;
import io.pinkspider.global.enums.MissionStatus;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionType;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionVisibility;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
@Table(name = "mission",
    indexes = {
        @Index(name = "idx_mission_category", columnList = "category_id"),
        @Index(name = "idx_mission_guild", columnList = "guild_id")
    })
@Comment("미션")
public class Mission extends LocalDateTimeBaseEntity {

    /** 사용자별 활성 개인 미션(PERSONAL) 최대 보유 개수. 완료/이탈한 미션은 제외, 길드 미션은 별도. */
    public static final int MAX_PERSONAL_MISSIONS_PER_USER = 20;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    @Comment("미션 ID")
    private Long id;

    @NotNull
    @Size(max = 200)
    @Column(name = "title", nullable = false, length = 200)
    @Comment("미션 제목")
    private String title;

    @Size(max = 200)
    @Column(name = "title_en", length = 200)
    @Comment("미션 제목 (영어)")
    private String titleEn;

    @Size(max = 200)
    @Column(name = "title_ar", length = 200)
    @Comment("미션 제목 (아랍어)")
    private String titleAr;

    @Size(max = 200)
    @Column(name = "title_ja", length = 200)
    @Comment("미션 제목 (일본어)")
    private String titleJa;

    @Column(name = "description", columnDefinition = "TEXT")
    @Comment("미션 설명")
    private String description;

    @Column(name = "description_en", columnDefinition = "TEXT")
    @Comment("미션 설명 (영어)")
    private String descriptionEn;

    @Column(name = "description_ar", columnDefinition = "TEXT")
    @Comment("미션 설명 (아랍어)")
    private String descriptionAr;

    @Column(name = "description_ja", columnDefinition = "TEXT")
    @Comment("미션 설명 (일본어)")
    private String descriptionJa;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Comment("미션 상태")
    private MissionStatus status;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", nullable = false, length = 20)
    @Comment("공개 여부")
    private MissionVisibility visibility;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    @Comment("미션 타입 (개인/길드)")
    private MissionType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", length = 20)
    @Comment("미션 출처 (SYSTEM, USER, GUILD)")
    @Builder.Default
    private MissionSource source = MissionSource.USER;

    @Enumerated(EnumType.STRING)
    @Column(name = "participation_type", length = 20)
    @Comment("참여 방식 (DIRECT, TEMPLATE_ONLY)")
    @Builder.Default
    private MissionParticipationType participationType = MissionParticipationType.DIRECT;

    @Column(name = "base_mission_id")
    @Comment("원본 시스템 미션 ID (복제된 경우)")
    private Long baseMissionId;

    @Column(name = "is_customizable", nullable = false)
    @Comment("커스터마이징 가능 여부")
    @Builder.Default
    private Boolean isCustomizable = true;

    @Column(name = "is_pinned", nullable = false)
    @Comment("고정 미션 여부 (삭제할 때까지 목록에 유지)")
    @Builder.Default
    private Boolean isPinned = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "execution_mode", nullable = false, length = 20)
    @Comment("수행 방식 (TIMED: 시간 측정, SIMPLE: 수행 여부)")
    @Builder.Default
    private MissionExecutionMode executionMode = MissionExecutionMode.TIMED;

    @Column(name = "is_deleted", nullable = false)
    @Comment("삭제 여부")
    @Builder.Default
    private Boolean isDeleted = false;

    @Column(name = "deleted_at")
    @Comment("삭제 시간")
    private LocalDateTime deletedAt;

    @Column(name = "target_duration_minutes")
    @Comment("목표 수행 시간 (분) - 달성 시 보너스 XP")
    private Integer targetDurationMinutes;

    @Column(name = "daily_execution_limit")
    @Comment("하루 수행 횟수 제한 (null = 무제한)")
    private Integer dailyExecutionLimit;

    @Column(name = "reminder_hour")
    @Comment("LUT-282: 푸시 리마인더 시각 (유저 로컬 0-23시, null = 리마인더 없음)")
    private Integer reminderHour;

    @Size(max = 70)
    @Column(name = "reminder_days_of_week", length = 70)
    @Comment("LUT-282: 푸시 리마인더 요일 CSV (예: MONDAY,WEDNESDAY, null = 리마인더 없음)")
    private String reminderDaysOfWeek;

    @NotNull
    @Column(name = "creator_id", nullable = false)
    @Comment("생성자 ID")
    private String creatorId;

    @Column(name = "guild_id")
    @Comment("길드 ID (길드 미션인 경우)")
    private String guildId;

    @Size(max = 100)
    @Column(name = "guild_name", length = 100)
    @Comment("길드 이름 (길드 미션인 경우)")
    private String guildName;

    @Column(name = "max_participants")
    @Comment("최대 참여 인원")
    private Integer maxParticipants;

    @Column(name = "start_at")
    @Comment("미션 시작일시")
    private LocalDateTime startAt;

    @Column(name = "end_at")
    @Comment("미션 종료일시")
    private LocalDateTime endAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "mission_interval", length = 20)
    @Comment("미션 수행 주기")
    @Builder.Default
    private MissionInterval missionInterval = MissionInterval.DAILY;

    @Column(name = "duration_days")
    @Comment("미션 기간 (일수)")
    private Integer durationDays;

    @Column(name = "duration_minutes")
    @Comment("1회 수행시 필요 시간 (분)")
    private Integer durationMinutes;

    @Column(name = "exp_per_completion")
    @Comment("1회 완료시 경험치")
    @Builder.Default
    private Integer expPerCompletion = 10;

    @Column(name = "bonus_exp_on_full_completion")
    @Comment("전체 완료시 보너스 경험치")
    @Builder.Default
    private Integer bonusExpOnFullCompletion = 50;

    @Column(name = "guild_exp_per_completion")
    @Comment("1회 완료시 길드 경험치 (길드 미션 전용)")
    @Builder.Default
    private Integer guildExpPerCompletion = 5;

    @Column(name = "guild_bonus_exp_on_full_completion")
    @Comment("전체 완료시 길드 보너스 경험치 (길드 미션 전용)")
    @Builder.Default
    private Integer guildBonusExpOnFullCompletion = 20;

    @Column(name = "category_id")
    @Comment("카테고리 ID (스냅샷)")
    private Long categoryId;

    @Size(max = 100)
    @Column(name = "category_name", length = 100)
    @Comment("카테고리 이름 (스냅샷)")
    private String categoryName;

    @Size(max = 50)
    @Column(name = "custom_category", length = 50)
    @Comment("사용자 정의 카테고리 (직접 입력 시)")
    private String customCategory;

    @Builder.Default
    @OneToMany(mappedBy = "mission")
    private List<MissionParticipant> participants = new ArrayList<>();

    /** LUT-282: 리마인더 요일 CSV → DayOfWeek 리스트 (미설정/파싱 불가 값은 무시) */
    public List<DayOfWeek> getReminderDaysOfWeekList() {
        if (reminderDaysOfWeek == null || reminderDaysOfWeek.isBlank()) {
            return List.of();
        }
        return Arrays.stream(reminderDaysOfWeek.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .map(DayOfWeek::valueOf)
            .toList();
    }

    /** LUT-282: 리마인더 설정 (요일 리스트 → CSV 저장). null/빈 요일이면 리마인더 해제 */
    public void updateReminder(Integer hour, List<DayOfWeek> daysOfWeek) {
        if (hour == null || daysOfWeek == null || daysOfWeek.isEmpty()) {
            this.reminderHour = null;
            this.reminderDaysOfWeek = null;
            return;
        }
        this.reminderHour = hour;
        this.reminderDaysOfWeek = daysOfWeek.stream()
            .distinct()
            .sorted()
            .map(DayOfWeek::name)
            .reduce((a, b) -> a + "," + b)
            .orElse(null);
    }

    /**
     * 카테고리 이름 반환 (스냅샷 카테고리 또는 사용자 정의)
     * Lombok @Getter보다 우선하여 customCategory 폴백 로직 유지
     */
    public String getCategoryName() {
        if (categoryName != null) {
            return categoryName;
        }
        return customCategory;
    }

    public void updateStatus(MissionStatus newStatus) {
        this.status = newStatus;
    }

    public void open() {
        transitionTo(MissionStatus.OPEN);
    }

    public void start() {
        transitionTo(MissionStatus.IN_PROGRESS);
    }

    public void complete() {
        transitionTo(MissionStatus.COMPLETED);
    }

    public void cancel() {
        transitionTo(MissionStatus.CANCELLED);
    }

    private void transitionTo(MissionStatus target) {
        if (!this.status.canTransitionTo(target)) {
            throw new IllegalStateException(
                String.format("'%s' 상태에서 '%s' 상태로 변경할 수 없습니다.",
                    this.status.getDescription(), target.getDescription()));
        }
        this.status = target;
    }

    public boolean isGuildMission() {
        return this.type == MissionType.GUILD;
    }

    public Long getGuildIdAsLong() {
        if (guildId == null || guildId.isBlank()) {
            return null;
        }
        return Long.parseLong(guildId);
    }

    public boolean isPublic() {
        return this.visibility == MissionVisibility.PUBLIC;
    }

    public void delete() {
        this.isDeleted = true;
        this.deletedAt = LocalDateTime.now();
    }

    public void restore() {
        this.isDeleted = false;
        this.deletedAt = null;
    }

    /**
     * locale에 따라 미션 제목을 반환합니다.
     */
    public String getLocalizedTitle(String locale) {
        return LocaleUtils.getLocalizedText(title, titleEn, titleAr, titleJa, locale);
    }

    /**
     * locale에 따라 미션 설명을 반환합니다.
     */
    public String getLocalizedDescription(String locale) {
        return LocaleUtils.getLocalizedText(description, descriptionEn, descriptionAr, descriptionJa, locale);
    }
}
