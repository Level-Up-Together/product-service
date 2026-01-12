package io.pinkspider.leveluptogethermvp.adminservice.domain.entity;

import io.pinkspider.global.domain.auditentity.LocalDateTimeBaseEntity;
import io.pinkspider.leveluptogethermvp.adminservice.domain.enums.SeasonRewardStatus;
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
@Table(name = "season_reward_history",
    indexes = {
        @Index(name = "idx_season_reward_history_season", columnList = "season_id"),
        @Index(name = "idx_season_reward_history_user", columnList = "user_id"),
        @Index(name = "idx_season_reward_history_status", columnList = "season_id, status")
    }
)
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Comment("시즌 보상 부여 이력")
public class SeasonRewardHistory extends LocalDateTimeBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    @Comment("ID")
    private Long id;

    @NotNull
    @Column(name = "season_id", nullable = false)
    @Comment("시즌 ID")
    private Long seasonId;

    @NotNull
    @Column(name = "user_id", nullable = false, length = 100)
    @Comment("유저 ID")
    private String userId;

    @NotNull
    @Column(name = "final_rank", nullable = false)
    @Comment("최종 순위")
    private Integer finalRank;

    @NotNull
    @Column(name = "total_exp", nullable = false)
    @Comment("시즌 총 경험치")
    private Long totalExp;

    @NotNull
    @Column(name = "title_id", nullable = false)
    @Comment("부여된 칭호 ID")
    private Long titleId;

    @Column(name = "title_name", length = 100)
    @Comment("부여된 칭호 이름")
    private String titleName;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Comment("보상 부여 상태")
    @Builder.Default
    private SeasonRewardStatus status = SeasonRewardStatus.PENDING;

    @Column(name = "error_message", length = 500)
    @Comment("오류 메시지 (실패 시)")
    private String errorMessage;

    public void markSuccess() {
        this.status = SeasonRewardStatus.SUCCESS;
    }

    public void markFailed(String errorMessage) {
        this.status = SeasonRewardStatus.FAILED;
        this.errorMessage = errorMessage;
    }

    public void markSkipped(String reason) {
        this.status = SeasonRewardStatus.SKIPPED;
        this.errorMessage = reason;
    }
}
