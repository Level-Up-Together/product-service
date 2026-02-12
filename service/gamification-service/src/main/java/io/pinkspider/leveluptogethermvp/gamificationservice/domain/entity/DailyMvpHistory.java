package io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity;

import io.pinkspider.global.domain.auditentity.LocalDateTimeBaseEntity;
import io.pinkspider.global.enums.TitleRarity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.Comment;

import java.time.LocalDate;

@Entity
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(name = "daily_mvp_history",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_daily_mvp_history_date_rank",
        columnNames = {"mvp_date", "mvp_rank"}
    ),
    indexes = {
        @Index(name = "idx_dmh_user_id", columnList = "user_id"),
        @Index(name = "idx_dmh_mvp_date", columnList = "mvp_date")
    }
)
@Comment("일간 MVP 히스토리")
public class DailyMvpHistory extends LocalDateTimeBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    @Comment("ID")
    private Long id;

    @NotNull
    @Column(name = "mvp_date", nullable = false)
    @Comment("MVP 선정 날짜")
    private LocalDate mvpDate;

    @NotNull
    @Column(name = "mvp_rank", nullable = false)
    @Comment("MVP 순위 (1~5)")
    private Integer mvpRank;

    @NotNull
    @Column(name = "user_id", nullable = false, length = 100)
    @Comment("사용자 ID")
    private String userId;

    @Column(name = "nickname", length = 50)
    @Comment("선정 당시 닉네임 (스냅샷)")
    private String nickname;

    @Column(name = "picture", length = 500)
    @Comment("선정 당시 프로필 이미지 (스냅샷)")
    private String picture;

    @Column(name = "user_level")
    @Comment("선정 당시 레벨 (스냅샷)")
    private Integer userLevel;

    @NotNull
    @Column(name = "earned_exp", nullable = false)
    @Comment("해당일 획득 경험치")
    private Long earnedExp;

    @Column(name = "top_category_name", length = 50)
    @Comment("가장 많이 활동한 카테고리명")
    private String topCategoryName;

    @Column(name = "top_category_id")
    @Comment("가장 많이 활동한 카테고리 ID")
    private Long topCategoryId;

    @Column(name = "top_category_exp")
    @Comment("가장 많이 활동한 카테고리에서 획득한 경험치")
    private Long topCategoryExp;

    @Column(name = "title_name", length = 100)
    @Comment("선정 당시 장착 칭호명 (스냅샷)")
    private String titleName;

    @Enumerated(EnumType.STRING)
    @Column(name = "title_rarity", length = 20)
    @Comment("선정 당시 장착 칭호 등급 (스냅샷)")
    private TitleRarity titleRarity;
}
