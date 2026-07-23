package io.pinkspider.leveluptogethermvp.gamificationservice.achievement.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.Title;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.enums.TitleAcquisitionType;
import io.pinkspider.global.enums.TitlePosition;
import io.pinkspider.global.enums.TitleRarity;
import io.pinkspider.global.translation.LocaleUtils;
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
    private String nameJa;
    private String displayName;
    private String description;
    private TitleRarity rarity;
    private TitlePosition positionType;
    private String colorCode;
    private String iconUrl;
    private TitleAcquisitionType acquisitionType;
    private String acquisitionCondition;

    public static TitleResponse from(Title title) {
        return from(title, null);
    }

    /** LUT-255: locale에 맞는 칭호명/설명을 대표 필드(name, displayName, description)에 채운다 */
    public static TitleResponse from(Title title, String locale) {
        return TitleResponse.builder()
            .id(title.getId())
            .name(title.getLocalizedName(locale))
            .nameEn(title.getNameEn())
            .nameAr(title.getNameAr())
            .nameJa(title.getNameJa())
            .displayName(title.getLocalizedName(locale))
            .description(LocaleUtils.getLocalizedText(title.getDescription(),
                title.getDescriptionEn(), title.getDescriptionAr(), title.getDescriptionJa(), locale))
            .rarity(title.getRarity())
            .positionType(title.getPositionType())
            .colorCode(title.getColorCode())
            .iconUrl(title.getIconUrl())
            .acquisitionType(title.getAcquisitionType())
            .acquisitionCondition(title.getAcquisitionCondition())
            .build();
    }
}
