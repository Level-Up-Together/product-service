package io.pinkspider.leveluptogethermvp.userservice.achievement.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.UserStats;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class UserStatsResponse {

    private String userId;
    private Integer totalMissionCompletions;
    private Integer totalMissionFullCompletions;
    private Integer totalGuildMissionCompletions;
    private Integer currentStreak;
    private Integer maxStreak;
    private LocalDate lastActivityDate;
    private Integer totalAchievementsCompleted;
    private Integer totalTitlesAcquired;
    private Long rankingPoints;
    private Integer maxCompletedMissionDuration;

    public static UserStatsResponse from(UserStats stats) {
        return UserStatsResponse.builder()
            .userId(stats.getUserId())
            .totalMissionCompletions(stats.getTotalMissionCompletions())
            .totalMissionFullCompletions(stats.getTotalMissionFullCompletions())
            .totalGuildMissionCompletions(stats.getTotalGuildMissionCompletions())
            .currentStreak(stats.getCurrentStreak())
            .maxStreak(stats.getMaxStreak())
            .lastActivityDate(stats.getLastActivityDate())
            .totalAchievementsCompleted(stats.getTotalAchievementsCompleted())
            .totalTitlesAcquired(stats.getTotalTitlesAcquired())
            .rankingPoints(stats.getRankingPoints())
            .maxCompletedMissionDuration(stats.getMaxCompletedMissionDuration())
            .build();
    }
}
