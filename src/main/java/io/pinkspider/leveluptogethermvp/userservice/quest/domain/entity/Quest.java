package io.pinkspider.leveluptogethermvp.userservice.quest.domain.entity;

import io.pinkspider.global.domain.auditentity.LocalDateTimeBaseEntity;
import io.pinkspider.leveluptogethermvp.userservice.quest.domain.enums.QuestActionType;
import io.pinkspider.leveluptogethermvp.userservice.quest.domain.enums.QuestCategory;
import io.pinkspider.leveluptogethermvp.userservice.quest.domain.enums.QuestType;
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
@Table(name = "quest",
    indexes = {
        @Index(name = "idx_quest_type", columnList = "quest_type"),
        @Index(name = "idx_quest_active", columnList = "is_active")
    })
@Comment("퀘스트 정의")
public class Quest extends LocalDateTimeBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    @Comment("ID")
    private Long id;

    @NotNull
    @Column(name = "name", nullable = false, length = 100)
    @Comment("퀘스트 이름")
    private String name;

    @Column(name = "description", length = 500)
    @Comment("퀘스트 설명")
    private String description;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "quest_type", nullable = false, length = 20)
    @Comment("퀘스트 타입 (DAILY/WEEKLY/SPECIAL)")
    private QuestType questType;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 20)
    @Comment("카테고리")
    private QuestCategory category;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 30)
    @Comment("행동 타입")
    private QuestActionType actionType;

    @Column(name = "required_count", nullable = false)
    @Comment("필요 횟수")
    @Builder.Default
    private Integer requiredCount = 1;

    @Column(name = "reward_exp")
    @Comment("보상 경험치")
    @Builder.Default
    private Integer rewardExp = 0;

    @Column(name = "reward_points")
    @Comment("보상 포인트")
    @Builder.Default
    private Integer rewardPoints = 0;

    @Column(name = "reward_title_id")
    @Comment("보상 칭호 ID")
    private Long rewardTitleId;

    @Column(name = "icon_url", length = 500)
    @Comment("아이콘 URL")
    private String iconUrl;

    @Column(name = "sort_order")
    @Comment("정렬 순서")
    @Builder.Default
    private Integer sortOrder = 0;

    @Column(name = "is_active")
    @Comment("활성화 여부")
    @Builder.Default
    private Boolean isActive = true;

    public boolean isDaily() {
        return questType == QuestType.DAILY;
    }

    public boolean isWeekly() {
        return questType == QuestType.WEEKLY;
    }
}
