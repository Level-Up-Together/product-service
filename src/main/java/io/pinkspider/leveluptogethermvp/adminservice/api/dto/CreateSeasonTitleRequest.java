package io.pinkspider.leveluptogethermvp.adminservice.api.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.enums.TitlePosition;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.enums.TitleRarity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@JsonNaming(SnakeCaseStrategy.class)
public record CreateSeasonTitleRequest(
    @NotBlank String name,
    String nameEn,
    String nameAr,
    String description,
    @NotNull TitleRarity rarity,
    @NotNull TitlePosition positionType,
    String iconUrl,
    String seasonName,
    String rankRange
) {

}
