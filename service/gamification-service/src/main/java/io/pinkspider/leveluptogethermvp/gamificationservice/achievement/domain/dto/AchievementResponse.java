package io.pinkspider.leveluptogethermvp.gamificationservice.achievement.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.Achievement;
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
public class AchievementResponse {

    private Long id;
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
        return from(achievement, Collections.emptyMap());
    }

    public static AchievementResponse from(Achievement achievement, Map<Long, String> categoryNamesById) {
        return from(achievement, categoryNamesById, null);
    }

    /**
     * QA-149: mission_category_name 컬럼이 NULL 인 데이터가 다수라 메타 카테고리 이름을 lookup 으로 채운다.
     * LUT-255: locale이 지정되면 저장된 한국어 이름 대신 locale이 반영된 lookup 이름을 우선한다.
     */
    public static AchievementResponse from(Achievement achievement, Map<Long, String> categoryNamesById,
                                            String locale) {
        String lookup = achievement.getMissionCategoryId() != null
            ? categoryNamesById.get(achievement.getMissionCategoryId()) : null;
        String missionCategoryName;
        if (locale != null && lookup != null && !lookup.isBlank()) {
            missionCategoryName = lookup;
        } else if (achievement.getMissionCategoryName() != null
            && !achievement.getMissionCategoryName().isBlank()) {
            missionCategoryName = achievement.getMissionCategoryName();
        } else {
            missionCategoryName = lookup;
        }
        return AchievementResponse.builder()
            .id(achievement.getId())
            .name(achievement.getLocalizedName(locale))
            .description(achievement.getLocalizedDescription(locale))
            .categoryCode(achievement.getCategoryCode())
            .missionCategoryId(achievement.getMissionCategoryId())
            .missionCategoryName(missionCategoryName)
            .iconUrl(achievement.getIconUrl())
            .requiredCount(achievement.getRequiredCount())
            .rewardExp(achievement.getRewardExp())
            .rewardTitleId(achievement.getRewardTitleId())
            .isHidden(achievement.getIsHidden())
            .build();
    }
}
