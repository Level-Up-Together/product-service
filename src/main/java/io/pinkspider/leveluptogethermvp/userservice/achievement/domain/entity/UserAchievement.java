package io.pinkspider.leveluptogethermvp.userservice.achievement.domain.entity;

import io.pinkspider.global.domain.auditentity.LocalDateTimeBaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;
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
@Table(name = "user_achievement",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_user_achievement",
        columnNames = {"user_id", "achievement_id"}
    )
)
@Comment("유저 업적")
public class UserAchievement extends LocalDateTimeBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    @Comment("ID")
    private Long id;

    @NotNull
    @Column(name = "user_id", nullable = false)
    @Comment("유저 ID")
    private String userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "achievement_id", nullable = false)
    @Comment("업적")
    private Achievement achievement;

    @NotNull
    @Column(name = "current_count", nullable = false)
    @Comment("현재 진행 횟수")
    @Builder.Default
    private Integer currentCount = 0;

    @NotNull
    @Column(name = "is_completed", nullable = false)
    @Comment("완료 여부")
    @Builder.Default
    private Boolean isCompleted = false;

    @Column(name = "completed_at")
    @Comment("완료 일시")
    private LocalDateTime completedAt;

    @NotNull
    @Column(name = "is_reward_claimed", nullable = false)
    @Comment("보상 수령 여부")
    @Builder.Default
    private Boolean isRewardClaimed = false;

    @Column(name = "reward_claimed_at")
    @Comment("보상 수령 일시")
    private LocalDateTime rewardClaimedAt;

    public void incrementCount() {
        this.currentCount++;
        checkCompletion();
    }

    public void addCount(int count) {
        this.currentCount += count;
        checkCompletion();
    }

    public void setCount(int count) {
        this.currentCount = count;
        checkCompletion();
    }

    private void checkCompletion() {
        if (!this.isCompleted && this.currentCount >= this.achievement.getRequiredCount()) {
            this.isCompleted = true;
            this.completedAt = LocalDateTime.now();
        }
    }

    public void claimReward() {
        if (!this.isCompleted) {
            throw new IllegalStateException("업적을 완료하지 않았습니다.");
        }
        if (this.isRewardClaimed) {
            throw new IllegalStateException("이미 보상을 수령했습니다.");
        }
        this.isRewardClaimed = true;
        this.rewardClaimedAt = LocalDateTime.now();
    }

    public double getProgressPercent() {
        if (achievement.getRequiredCount() == 0) return 100.0;
        return Math.min(100.0, (double) currentCount / achievement.getRequiredCount() * 100);
    }
}
