package io.pinkspider.leveluptogethermvp.gamificationservice.achievement.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.UserStats;
import io.pinkspider.global.enums.TitleRarity;
import io.pinkspider.global.facade.dto.EquippedItemRarityDto;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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
    private String equippedTitleColorCode;
    private String leftTitleName;
    private TitleRarity leftTitleRarity;
    private String rightTitleName;
    private TitleRarity rightTitleRarity;

    // LUT-424: 장착 아이템 타입·희귀도 (썸네일 등급 표식용). 미장착이면 빈 배열.
    @Setter
    @Builder.Default
    private List<EquippedItemRarityDto> equippedItemRarities = List.of();

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
                                       String equippedTitleName, TitleRarity equippedTitleRarity,
                                       String equippedTitleColorCode,
                                       String leftTitleName, TitleRarity leftTitleRarity,
                                       String rightTitleName, TitleRarity rightTitleRarity) {
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
            .equippedTitleColorCode(equippedTitleColorCode)
            .leftTitleName(leftTitleName)
            .leftTitleRarity(leftTitleRarity)
            .rightTitleName(rightTitleName)
            .rightTitleRarity(rightTitleRarity)
            .build();
    }
}
