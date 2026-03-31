package io.pinkspider.leveluptogethermvp.gamificationservice.achievement.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.Achievement;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(SnakeCaseStrategy.class)
public class AchievementAdminResponse {

    private Long id;
    private String name;
    private String nameEn;
    private String nameAr;
    private String nameJa;
    private String description;
    private String descriptionEn;
    private String descriptionAr;
    private String descriptionJa;
    private Long categoryId;
    private String categoryCode;
    private String categoryName;
    private Long missionCategoryId;
    private String missionCategoryName;
    private Long checkLogicTypeId;
    private String checkLogicTypeName;
    private String checkLogicDataSource;
    private String checkLogicDataField;
    private String comparisonOperator;
    private String iconUrl;
    private Integer requiredCount;
    private Integer rewardExp;
    private Long rewardTitleId;
    private String rewardTitleName;
    private Boolean isHidden;
    private Boolean isActive;
    private Long eventId;
    private String eventName;
    private LocalDateTime createdAt;
    private LocalDateTime modifiedAt;

    public static AchievementAdminResponse from(Achievement entity) {
        return from(entity, null, null);
    }

    public static AchievementAdminResponse from(Achievement entity, String rewardTitleName, String checkLogicTypeName) {
        return AchievementAdminResponse.builder()
            .id(entity.getId())
            .name(entity.getName())
            .nameEn(entity.getNameEn())
            .nameAr(entity.getNameAr())
            .nameJa(entity.getNameJa())
            .description(entity.getDescription())
            .descriptionEn(entity.getDescriptionEn())
            .descriptionAr(entity.getDescriptionAr())
            .descriptionJa(entity.getDescriptionJa())
            .categoryId(entity.getCategory() != null ? entity.getCategory().getId() : null)
            .categoryCode(entity.getCategoryCode())
            .categoryName(entity.getCategory() != null ? entity.getCategory().getName() : null)
            .missionCategoryId(entity.getMissionCategoryId())
            .missionCategoryName(entity.getMissionCategoryName())
            .checkLogicTypeId(entity.getCheckLogicTypeId())
            .checkLogicTypeName(checkLogicTypeName)
            .checkLogicDataSource(entity.getCheckLogicDataSource())
            .checkLogicDataField(entity.getCheckLogicDataField())
            .comparisonOperator(entity.getComparisonOperator())
            .iconUrl(entity.getIconUrl())
            .requiredCount(entity.getRequiredCount())
            .rewardExp(entity.getRewardExp())
            .rewardTitleId(entity.getRewardTitleId())
            .rewardTitleName(rewardTitleName)
            .isHidden(entity.getIsHidden())
            .isActive(entity.getIsActive())
            .eventId(entity.getEventId())
            .eventName(entity.getEventName())
            .createdAt(entity.getCreatedAt())
            .modifiedAt(entity.getModifiedAt())
            .build();
    }
}
