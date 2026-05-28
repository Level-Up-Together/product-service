package io.pinkspider.leveluptogethermvp.gamificationservice.achievement.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.global.enums.TitleRarity;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.Title;
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

    // QA-159: 보상 칭호 이름 + 등급 (achievement.reward_title_id -> Title 조회)
    private String rewardTitleName;
    private TitleRarity rewardTitleRarity;

    // QA-159: 카테고리 내 시리즈 구분용 (GUILD/SOCIAL 카테고리에서 다른 체크로직 그룹화)
    private Long checkLogicTypeId;
    private String checkLogicDataField;

    public static UserAchievementResponse from(UserAchievement userAchievement) {
        return from(userAchievement, Collections.emptyMap(), Collections.emptyMap());
    }

    public static UserAchievementResponse from(UserAchievement userAchievement,
                                                Map<Long, String> categoryNamesById) {
        return from(userAchievement, categoryNamesById, Collections.emptyMap());
    }

    /**
     * QA-149: mission_category_name 컬럼이 NULL 인 데이터가 다수라 메타 카테고리 이름을 lookup 으로 채운다.
     * QA-159: 보상 칭호 이름/등급, 체크로직 식별 필드도 함께 채워서 프론트가 시리즈 그룹화 가능하게 한다.
     */
    public static UserAchievementResponse from(UserAchievement userAchievement,
                                                Map<Long, String> categoryNamesById,
                                                Map<Long, Title> titlesById) {
        var achievement = userAchievement.getAchievement();
        Title rewardTitle = achievement.getRewardTitleId() != null
            ? titlesById.get(achievement.getRewardTitleId()) : null;
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
            .rewardTitleName(rewardTitle != null ? rewardTitle.getName() : null)
            .rewardTitleRarity(rewardTitle != null ? rewardTitle.getRarity() : null)
            .checkLogicTypeId(achievement.getCheckLogicTypeId())
            .checkLogicDataField(achievement.getCheckLogicDataField())
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
