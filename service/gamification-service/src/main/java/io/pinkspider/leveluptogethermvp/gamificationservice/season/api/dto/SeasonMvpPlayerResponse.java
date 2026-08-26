package io.pinkspider.leveluptogethermvp.gamificationservice.season.api.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.global.enums.TitleRarity;
import io.pinkspider.global.facade.dto.EquippedItemRarityDto;
import java.util.List;

@JsonNaming(SnakeCaseStrategy.class)
public record SeasonMvpPlayerResponse(
    String userId,
    String nickname,
    String profileImageUrl,
    Integer level,
    String title,
    TitleRarity titleRarity,
    String leftTitle,
    TitleRarity leftTitleRarity,
    String rightTitle,
    TitleRarity rightTitleRarity,
    Long seasonExp,
    Integer rank,
    List<EquippedItemRarityDto> equippedItemRarities
) {
    public static SeasonMvpPlayerResponse of(
        String userId,
        String nickname,
        String profileImageUrl,
        Integer level,
        String title,
        TitleRarity titleRarity,
        String leftTitle,
        TitleRarity leftTitleRarity,
        String rightTitle,
        TitleRarity rightTitleRarity,
        Long seasonExp,
        Integer rank,
        List<EquippedItemRarityDto> equippedItemRarities
    ) {
        return new SeasonMvpPlayerResponse(
            userId,
            nickname,
            profileImageUrl,
            level,
            title,
            titleRarity,
            leftTitle,
            leftTitleRarity,
            rightTitle,
            rightTitleRarity,
            seasonExp,
            rank,
            equippedItemRarities != null ? equippedItemRarities : List.of()
        );
    }
}
