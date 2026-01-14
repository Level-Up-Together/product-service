package io.pinkspider.leveluptogethermvp.userservice.achievement.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.UserAchievement;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.enums.AchievementType;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class UserAchievementResponse {

    private Long id;
    private Long achievementId;
    private AchievementType achievementType;
    private String name;
    private String description;
    private String categoryCode;
    private Long missionCategoryId;
    private String missionCategoryName;
    private String iconUrl;
    private Integer currentCount;
    private Integer requiredCount;
    private Double progressPercent;
    private Boolean isCompleted;
    private LocalDateTime completedAt;
    private Boolean isRewardClaimed;
    private Integer rewardExp;
    private Long rewardTitleId;

    public static UserAchievementResponse from(UserAchievement userAchievement) {
        var achievement = userAchievement.getAchievement();
        return UserAchievementResponse.builder()
            .id(userAchievement.getId())
            .achievementId(achievement.getId())
            .achievementType(achievement.getAchievementType())
            .name(achievement.getName())
            .description(achievement.getDescription())
            .categoryCode(achievement.getCategoryCode())
            .missionCategoryId(achievement.getMissionCategoryId())
            .missionCategoryName(achievement.getMissionCategoryName())
            .iconUrl(achievement.getIconUrl())
            .currentCount(userAchievement.getCurrentCount())
            .requiredCount(achievement.getRequiredCount())
            .progressPercent(userAchievement.getProgressPercent())
            .isCompleted(userAchievement.getIsCompleted())
            .completedAt(userAchievement.getCompletedAt())
            .isRewardClaimed(userAchievement.getIsRewardClaimed())
            .rewardExp(achievement.getRewardExp())
            .rewardTitleId(achievement.getRewardTitleId())
            .build();
    }
}
