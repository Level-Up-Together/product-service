package io.pinkspider.leveluptogethermvp.userservice.achievement.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.Achievement;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.enums.AchievementType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class AchievementResponse {

    private Long id;
    private AchievementType achievementType;
    private String name;
    private String description;
    private String categoryCode;
    private Long missionCategoryId;
    private String missionCategoryName;
    private String iconUrl;
    private Integer requiredCount;
    private Integer rewardExp;
    private Long rewardTitleId;
    private Boolean isHidden;

    public static AchievementResponse from(Achievement achievement) {
        return AchievementResponse.builder()
            .id(achievement.getId())
            .achievementType(achievement.getAchievementType())
            .name(achievement.getName())
            .description(achievement.getDescription())
            .categoryCode(achievement.getCategoryCode())
            .missionCategoryId(achievement.getMissionCategoryId())
            .missionCategoryName(achievement.getMissionCategoryName())
            .iconUrl(achievement.getIconUrl())
            .requiredCount(achievement.getRequiredCount())
            .rewardExp(achievement.getRewardExp())
            .rewardTitleId(achievement.getRewardTitleId())
            .isHidden(achievement.getIsHidden())
            .build();
    }
}
