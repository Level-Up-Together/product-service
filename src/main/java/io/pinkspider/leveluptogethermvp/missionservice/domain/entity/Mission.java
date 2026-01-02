package io.pinkspider.leveluptogethermvp.missionservice.domain.entity;

import io.pinkspider.global.domain.auditentity.LocalDateTimeBaseEntity;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionInterval;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionParticipationType;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionSource;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionStatus;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionType;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionVisibility;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.ArrayList;
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
@Table(name = "mission")
@Comment("미션")
public class Mission extends LocalDateTimeBaseEntity {

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

    @Column(name = "description", columnDefinition = "TEXT")
    @Comment("미션 설명")
    private String description;

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

    @NotNull
    @Column(name = "creator_id", nullable = false)
    @Comment("생성자 ID")
    private String creatorId;

    @Column(name = "guild_id")
    @Comment("길드 ID (길드 미션인 경우)")
    private String guildId;

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    @Comment("카테고리 ID (기존 카테고리 선택 시)")
    private MissionCategory category;

    @Size(max = 50)
    @Column(name = "custom_category", length = 50)
    @Comment("사용자 정의 카테고리 (직접 입력 시)")
    private String customCategory;

    @Builder.Default
    @OneToMany(mappedBy = "mission")
    private List<MissionParticipant> participants = new ArrayList<>();

    /**
     * 카테고리 이름 반환 (기존 카테고리 또는 사용자 정의)
     */
    public String getCategoryName() {
        if (category != null) {
            return category.getName();
        }
        return customCategory;
    }

    public void updateStatus(MissionStatus newStatus) {
        this.status = newStatus;
    }

    public void open() {
        if (this.status != MissionStatus.DRAFT) {
            throw new IllegalStateException("작성중 상태의 미션만 오픈할 수 있습니다.");
        }
        this.status = MissionStatus.OPEN;
    }

    public void start() {
        if (this.status != MissionStatus.OPEN) {
            throw new IllegalStateException("모집중 상태의 미션만 시작할 수 있습니다.");
        }
        this.status = MissionStatus.IN_PROGRESS;
    }

    public void complete() {
        if (this.status != MissionStatus.IN_PROGRESS) {
            throw new IllegalStateException("진행중 상태의 미션만 완료할 수 있습니다.");
        }
        this.status = MissionStatus.COMPLETED;
    }

    public void cancel() {
        if (this.status == MissionStatus.COMPLETED) {
            throw new IllegalStateException("완료된 미션은 취소할 수 없습니다.");
        }
        this.status = MissionStatus.CANCELLED;
    }

    public boolean isGuildMission() {
        return this.type == MissionType.GUILD;
    }

    public boolean isPublic() {
        return this.visibility == MissionVisibility.PUBLIC;
    }
}
