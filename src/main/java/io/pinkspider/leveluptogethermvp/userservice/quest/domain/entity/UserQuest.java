package io.pinkspider.leveluptogethermvp.userservice.quest.domain.entity;

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

@Entity
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(name = "user_quest",
    uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "quest_id", "period_key"}),
    indexes = {
        @Index(name = "idx_user_quest_user", columnList = "user_id"),
        @Index(name = "idx_user_quest_period", columnList = "period_key")
    })
@Comment("유저 퀘스트 진행")
public class UserQuest extends LocalDateTimeBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    @Comment("ID")
    private Long id;

    @NotNull
    @Column(name = "user_id", nullable = false)
    @Comment("사용자 ID")
    private String userId;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quest_id", nullable = false)
    @Comment("퀘스트")
    private Quest quest;

    @Column(name = "period_key", nullable = false, length = 20)
    @Comment("기간 키 (일일: 2024-01-01, 주간: 2024-W01)")
    private String periodKey;

    @Column(name = "current_count")
    @Comment("현재 진행 횟수")
    @Builder.Default
    private Integer currentCount = 0;

    @Column(name = "is_completed")
    @Comment("완료 여부")
    @Builder.Default
    private Boolean isCompleted = false;

    @Column(name = "completed_at")
    @Comment("완료 시간")
    private LocalDateTime completedAt;

    @Column(name = "is_reward_claimed")
    @Comment("보상 수령 여부")
    @Builder.Default
    private Boolean isRewardClaimed = false;

    @Column(name = "reward_claimed_at")
    @Comment("보상 수령 시간")
    private LocalDateTime rewardClaimedAt;

    public void incrementProgress() {
        incrementProgress(1);
    }

    public void incrementProgress(int amount) {
        this.currentCount += amount;
        checkCompletion();
    }

    public void setProgress(int count) {
        this.currentCount = count;
        checkCompletion();
    }

    private void checkCompletion() {
        if (!this.isCompleted && this.currentCount >= this.quest.getRequiredCount()) {
            this.isCompleted = true;
            this.completedAt = LocalDateTime.now();
        }
    }

    public void claimReward() {
        if (!this.isCompleted) {
            throw new IllegalStateException("퀘스트를 완료하지 않았습니다.");
        }
        if (this.isRewardClaimed) {
            throw new IllegalStateException("이미 보상을 수령했습니다.");
        }
        this.isRewardClaimed = true;
        this.rewardClaimedAt = LocalDateTime.now();
    }

    public int getProgress() {
        return Math.min(100, (currentCount * 100) / quest.getRequiredCount());
    }

    public boolean canClaimReward() {
        return isCompleted && !isRewardClaimed;
    }

    public static String generateDailyPeriodKey(LocalDate date) {
        return date.toString();
    }

    public static String generateWeeklyPeriodKey(LocalDate date) {
        int weekOfYear = date.get(java.time.temporal.WeekFields.ISO.weekOfWeekBasedYear());
        int year = date.get(java.time.temporal.WeekFields.ISO.weekBasedYear());
        return year + "-W" + String.format("%02d", weekOfYear);
    }
}
