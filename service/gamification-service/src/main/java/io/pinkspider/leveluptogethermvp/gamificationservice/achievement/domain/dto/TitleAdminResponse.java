package io.pinkspider.leveluptogethermvp.gamificationservice.achievement.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.Title;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.enums.TitleAcquisitionType;
import io.pinkspider.global.enums.TitlePosition;
import io.pinkspider.global.enums.TitleRarity;
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
public class TitleAdminResponse {

    private Long id;
    private String name;
    private String nameEn;
    private String nameAr;
    private String nameJa;
    private String description;
    private TitleRarity rarity;
    private TitlePosition positionType;
    private String colorCode;
    private String iconUrl;
    private TitleAcquisitionType acquisitionType;
    private String acquisitionCondition;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime modifiedAt;

    private Long linkedAchievementId;
    private String linkedAchievementName;

    public static TitleAdminResponse from(Title entity) {
        return from(entity, null, null);
    }

    public static TitleAdminResponse from(Title entity, Long linkedAchievementId, String linkedAchievementName) {
        return TitleAdminResponse.builder()
            .id(entity.getId())
            .name(entity.getName())
            .nameEn(entity.getNameEn())
            .nameAr(entity.getNameAr())
            .nameJa(entity.getNameJa())
            .description(entity.getDescription())
            .rarity(entity.getRarity())
            .positionType(entity.getPositionType())
            .colorCode(entity.getColorCode())
            .iconUrl(entity.getIconUrl())
            .acquisitionType(entity.getAcquisitionType())
            .acquisitionCondition(entity.getAcquisitionCondition())
            .isActive(entity.getIsActive())
            .createdAt(entity.getCreatedAt())
            .modifiedAt(entity.getModifiedAt())
            .linkedAchievementId(linkedAchievementId)
            .linkedAchievementName(linkedAchievementName)
            .build();
    }
}
