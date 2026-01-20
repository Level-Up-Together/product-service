package io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity;

import io.pinkspider.global.domain.auditentity.LocalDateTimeBaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "daily_mvp_category_stats",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_dmcs_date_user_category",
        columnNames = {"stats_date", "user_id", "category_id"}
    ),
    indexes = {
        @Index(name = "idx_dmcs_stats_date", columnList = "stats_date"),
        @Index(name = "idx_dmcs_category_id", columnList = "category_id"),
        @Index(name = "idx_dmcs_user_id", columnList = "user_id")
    }
)
@Comment("일간 MVP 카테고리별 통계")
public class DailyMvpCategoryStats extends LocalDateTimeBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    @Comment("ID")
    private Long id;

    @NotNull
    @Column(name = "stats_date", nullable = false)
    @Comment("통계 날짜")
    private LocalDate statsDate;

    @NotNull
    @Column(name = "user_id", nullable = false, length = 100)
    @Comment("사용자 ID")
    private String userId;

    @NotNull
    @Column(name = "category_id", nullable = false)
    @Comment("카테고리 ID")
    private Long categoryId;

    @NotNull
    @Column(name = "category_name", nullable = false, length = 50)
    @Comment("카테고리명")
    private String categoryName;

    @NotNull
    @Column(name = "earned_exp", nullable = false)
    @Comment("해당 카테고리에서 획득한 경험치")
    private Long earnedExp;

    @NotNull
    @Column(name = "activity_count", nullable = false)
    @Comment("활동 횟수 (경험치 획득 건수)")
    private Integer activityCount;
}
