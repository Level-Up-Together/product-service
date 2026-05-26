package io.pinkspider.leveluptogethermvp.gamificationservice.achievement.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.UserAchievement;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;
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
        return from(userAchievement, Collections.emptyMap());
    }

    /**
     * QA-149: mission_category_name 컬럼이 NULL 인 데이터가 다수라 메타 카테고리 이름을 lookup 으로 채운다.
     * achievement.missionCategoryName 이 있으면 그걸 우선, 없으면 categoryNamesById 에서 조회.
     */
    public static UserAchievementResponse from(UserAchievement userAchievement,
                                                Map<Long, String> categoryNamesById) {
        var achievement = userAchievement.getAchievement();
        return UserAchievementResponse.builder()
            .id(userAchievement.getId())
            .achievementId(achievement.getId())
            .name(achievement.getName())
            .description(achievement.getDescription())
            .categoryCode(achievement.getCategoryCode())
            .missionCategoryId(achievement.getMissionCategoryId())
            .missionCategoryName(resolveMissionCategoryName(achievement.getMissionCategoryName(),
                achievement.getMissionCategoryId(), categoryNamesById))
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

    private static String resolveMissionCategoryName(String stored, Long id,
                                                      Map<Long, String> categoryNamesById) {
        if (stored != null && !stored.isBlank()) {
            return stored;
        }
        if (id == null) {
            return null;
        }
        return categoryNamesById.get(id);
    }
}
