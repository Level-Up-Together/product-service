package io.pinkspider.leveluptogethermvp.gamificationservice.season.api.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.enums.TitleRarity;

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
    Integer rank
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
        Integer rank
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
            rank
        );
    }
}
