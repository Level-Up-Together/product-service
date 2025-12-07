package io.pinkspider.leveluptogethermvp.userservice.achievement.domain.entity;

import io.pinkspider.global.domain.auditentity.LocalDateTimeBaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
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
@Table(name = "user_stats",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_user_stats_user_id",
        columnNames = {"user_id"}
    )
)
@Comment("유저 통계")
public class UserStats extends LocalDateTimeBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    @Comment("ID")
    private Long id;

    @NotNull
    @Column(name = "user_id", nullable = false)
    @Comment("유저 ID")
    private String userId;

    // 미션 관련 통계
    @Column(name = "total_mission_completions", nullable = false)
    @Comment("총 미션 수행 완료 횟수")
    @Builder.Default
    private Integer totalMissionCompletions = 0;

    @Column(name = "total_mission_full_completions", nullable = false)
    @Comment("총 미션 전체 완료 횟수")
    @Builder.Default
    private Integer totalMissionFullCompletions = 0;

    @Column(name = "total_guild_mission_completions", nullable = false)
    @Comment("총 길드 미션 수행 완료 횟수")
    @Builder.Default
    private Integer totalGuildMissionCompletions = 0;

    // 연속 활동 통계
    @Column(name = "current_streak", nullable = false)
    @Comment("현재 연속 활동 일수")
    @Builder.Default
    private Integer currentStreak = 0;

    @Column(name = "max_streak", nullable = false)
    @Comment("최대 연속 활동 일수")
    @Builder.Default
    private Integer maxStreak = 0;

    @Column(name = "last_activity_date")
    @Comment("마지막 활동 날짜")
    private LocalDate lastActivityDate;

    // 업적/칭호 통계
    @Column(name = "total_achievements_completed", nullable = false)
    @Comment("완료한 업적 수")
    @Builder.Default
    private Integer totalAchievementsCompleted = 0;

    @Column(name = "total_titles_acquired", nullable = false)
    @Comment("획득한 칭호 수")
    @Builder.Default
    private Integer totalTitlesAcquired = 0;

    // 랭킹용 포인트
    @Column(name = "ranking_points", nullable = false)
    @Comment("랭킹 포인트")
    @Builder.Default
    private Long rankingPoints = 0L;

    public void incrementMissionCompletion() {
        this.totalMissionCompletions++;
        updateRankingPoints();
    }

    public void incrementMissionFullCompletion() {
        this.totalMissionFullCompletions++;
        updateRankingPoints();
    }

    public void incrementGuildMissionCompletion() {
        this.totalGuildMissionCompletions++;
        updateRankingPoints();
    }

    public void updateStreak(LocalDate today) {
        if (lastActivityDate == null) {
            this.currentStreak = 1;
        } else if (lastActivityDate.equals(today)) {
            // 같은 날 중복 호출 - 아무것도 하지 않음
            return;
        } else if (lastActivityDate.plusDays(1).equals(today)) {
            // 연속
            this.currentStreak++;
        } else {
            // 연속 끊김
            this.currentStreak = 1;
        }

        if (this.currentStreak > this.maxStreak) {
            this.maxStreak = this.currentStreak;
        }
        this.lastActivityDate = today;
        updateRankingPoints();
    }

    public void incrementAchievementCompleted() {
        this.totalAchievementsCompleted++;
        updateRankingPoints();
    }

    public void incrementTitleAcquired() {
        this.totalTitlesAcquired++;
    }

    private void updateRankingPoints() {
        // 랭킹 포인트 계산: 미션완료*10 + 전체완료*50 + 길드미션*15 + 연속일수*5 + 업적*100
        this.rankingPoints = (long) (
            totalMissionCompletions * 10 +
            totalMissionFullCompletions * 50 +
            totalGuildMissionCompletions * 15 +
            maxStreak * 5 +
            totalAchievementsCompleted * 100
        );
    }
}
