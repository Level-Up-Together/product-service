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
        return from(userAchievement, categoryNamesById, titlesById, null);
    }

    /** LUT-255: 업적명/설명/보상 칭호명/카테고리명을 locale에 맞게 채운다 */
    public static UserAchievementResponse from(UserAchievement userAchievement,
                                                Map<Long, String> categoryNamesById,
                                                Map<Long, Title> titlesById,
                                                String locale) {
        var achievement = userAchievement.getAchievement();
        Title rewardTitle = achievement.getRewardTitleId() != null
            ? titlesById.get(achievement.getRewardTitleId()) : null;
        return UserAchievementResponse.builder()
            .id(userAchievement.getId())
            .achievementId(achievement.getId())
            .name(achievement.getLocalizedName(locale))
            .description(achievement.getLocalizedDescription(locale))
            .categoryCode(achievement.getCategoryCode())
            .missionCategoryId(achievement.getMissionCategoryId())
            .missionCategoryName(resolveMissionCategoryName(achievement.getMissionCategoryName(),
                achievement.getMissionCategoryId(), categoryNamesById, locale))
            .iconUrl(achievement.getIconUrl())
            .currentCount(userAchievement.getCurrentCount())
            .requiredCount(achievement.getRequiredCount())
            .progressPercent(userAchievement.getProgressPercent())
            .isCompleted(userAchievement.getIsCompleted())
            .completedAt(userAchievement.getCompletedAt())
            .isRewardClaimed(userAchievement.getIsRewardClaimed())
            .rewardExp(achievement.getRewardExp())
            .rewardTitleId(achievement.getRewardTitleId())
            .rewardTitleName(rewardTitle != null ? rewardTitle.getLocalizedName(locale) : null)
            .rewardTitleRarity(rewardTitle != null ? rewardTitle.getRarity() : null)
            .checkLogicTypeId(achievement.getCheckLogicTypeId())
            .checkLogicDataField(achievement.getCheckLogicDataField())
            .build();
    }

    /**
     * QA-149: 저장된 이름이 없으면 메타 lookup으로 보충. LUT-255: locale이 지정되면 저장된 한국어 이름 대신
     * locale이 반영된 lookup 이름을 우선한다.
     */
    private static String resolveMissionCategoryName(String stored, Long id,
                                                      Map<Long, String> categoryNamesById,
                                                      String locale) {
        String lookup = id != null ? categoryNamesById.get(id) : null;
        if (locale != null && lookup != null && !lookup.isBlank()) {
            return lookup;
        }
        if (stored != null && !stored.isBlank()) {
            return stored;
        }
        return lookup;
    }
}
