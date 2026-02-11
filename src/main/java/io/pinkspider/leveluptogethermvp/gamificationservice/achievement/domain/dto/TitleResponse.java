package io.pinkspider.leveluptogethermvp.gamificationservice.achievement.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.Title;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.enums.TitleAcquisitionType;
import io.pinkspider.global.enums.TitlePosition;
import io.pinkspider.global.enums.TitleRarity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class TitleResponse {

    private Long id;
    private String name;
    private String nameEn;
    private String nameAr;
    private String displayName;
    private String description;
    private TitleRarity rarity;
    private TitlePosition positionType;
    private String colorCode;
    private String iconUrl;
    private TitleAcquisitionType acquisitionType;
    private String acquisitionCondition;

    public static TitleResponse from(Title title) {
        return TitleResponse.builder()
            .id(title.getId())
            .name(title.getName())
            .nameEn(title.getNameEn())
            .nameAr(title.getNameAr())
            .displayName(title.getDisplayName())
            .description(title.getDescription())
            .rarity(title.getRarity())
            .positionType(title.getPositionType())
            .colorCode(title.getColorCode())
            .iconUrl(title.getIconUrl())
            .acquisitionType(title.getAcquisitionType())
            .acquisitionCondition(title.getAcquisitionCondition())
            .build();
    }
}
