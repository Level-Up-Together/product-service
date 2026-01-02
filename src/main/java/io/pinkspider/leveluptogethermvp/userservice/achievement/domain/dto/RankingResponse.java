package io.pinkspider.leveluptogethermvp.userservice.achievement.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.userservice.achievement.domain.entity.UserStats;
import io.pinkspider.leveluptogethermvp.userservice.achievement.domain.enums.TitleRarity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class RankingResponse {

    private Long rank;
    private String userId;
    private Long rankingPoints;
    private Integer totalMissionCompletions;
    private Integer maxStreak;
    private Integer totalAchievementsCompleted;

    // 추가 정보 (프로필 조회 시 조인해서 가져올 수 있음)
    private String nickname;
    private Integer userLevel;
    private String equippedTitleName;
    private TitleRarity equippedTitleRarity;

    public static RankingResponse from(UserStats stats, Long rank) {
        return RankingResponse.builder()
            .rank(rank)
            .userId(stats.getUserId())
            .rankingPoints(stats.getRankingPoints())
            .totalMissionCompletions(stats.getTotalMissionCompletions())
            .maxStreak(stats.getMaxStreak())
            .totalAchievementsCompleted(stats.getTotalAchievementsCompleted())
            .build();
    }

    public static RankingResponse from(UserStats stats, Long rank, String nickname, Integer userLevel,
                                       String equippedTitleName, TitleRarity equippedTitleRarity) {
        return RankingResponse.builder()
            .rank(rank)
            .userId(stats.getUserId())
            .rankingPoints(stats.getRankingPoints())
            .totalMissionCompletions(stats.getTotalMissionCompletions())
            .maxStreak(stats.getMaxStreak())
            .totalAchievementsCompleted(stats.getTotalAchievementsCompleted())
            .nickname(nickname)
            .userLevel(userLevel)
            .equippedTitleName(equippedTitleName)
            .equippedTitleRarity(equippedTitleRarity)
            .build();
    }
}
