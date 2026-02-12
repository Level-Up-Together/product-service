package io.pinkspider.leveluptogethermvp.gamificationservice.season.domain.entity;

import io.pinkspider.global.domain.auditentity.LocalDateTimeBaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
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
@Table(name = "season_rank_reward",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_season_rank_reward",
        columnNames = {"season_id", "category_id", "rank_start", "rank_end"}
    ),
    indexes = {
        @Index(name = "idx_season_rank_reward_season", columnList = "season_id"),
        @Index(name = "idx_season_rank_reward_active", columnList = "season_id, is_active"),
        @Index(name = "idx_season_rank_reward_category", columnList = "season_id, category_id")
    }
)
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Comment("시즌 순위별 보상 칭호")
public class SeasonRankReward extends LocalDateTimeBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    @Comment("ID")
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "season_id", nullable = false)
    @Comment("시즌")
    private Season season;

    @NotNull
    @Column(name = "rank_start", nullable = false)
    @Comment("순위 시작 (포함)")
    private Integer rankStart;

    @NotNull
    @Column(name = "rank_end", nullable = false)
    @Comment("순위 끝 (포함)")
    private Integer rankEnd;

    @NotNull
    @Column(name = "title_id", nullable = false)
    @Comment("보상 칭호 ID")
    private Long titleId;

    @Column(name = "title_name", length = 100)
    @Comment("보상 칭호 이름 (비정규화)")
    private String titleName;

    @Column(name = "title_rarity", length = 20)
    @Comment("보상 칭호 희귀도 (비정규화)")
    private String titleRarity;

    @Column(name = "category_id")
    @Comment("카테고리 ID (NULL이면 전체 랭킹)")
    private Long categoryId;

    @Column(name = "category_name", length = 100)
    @Comment("카테고리명 (비정규화)")
    private String categoryName;

    @Column(name = "sort_order", nullable = false)
    @Comment("정렬 순서 (1위 보상이 먼저)")
    @Builder.Default
    private Integer sortOrder = 0;

    @Column(name = "is_active", nullable = false)
    @Comment("활성화 여부")
    @Builder.Default
    private Boolean isActive = true;

    /**
     * 주어진 순위가 이 보상 구간에 해당하는지 확인
     */
    public boolean containsRank(int rank) {
        return rank >= rankStart && rank <= rankEnd;
    }

    /**
     * 순위 구간 표시 문자열 (예: "1위", "2~5위")
     */
    public String getRankRangeDisplay() {
        if (rankStart.equals(rankEnd)) {
            return rankStart + "위";
        }
        return rankStart + "~" + rankEnd + "위";
    }

    /**
     * 전체 랭킹 여부 확인
     */
    public boolean isOverallRanking() {
        return categoryId == null;
    }

    /**
     * 랭킹 타입 표시 문자열 (예: "전체", "운동")
     */
    public String getRankingTypeDisplay() {
        return categoryId == null ? "전체" : categoryName;
    }
}
