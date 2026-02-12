package io.pinkspider.leveluptogethermvp.userservice.home.api.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
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
public class TodayPlayerResponse {

    private String userId;
    private String nickname;
    private String profileImageUrl;
    private Integer level;
    private String title;
    private TitleRarity titleRarity;
    private String titleColorCode;
    private String leftTitle;
    private TitleRarity leftTitleRarity;
    private String leftTitleColorCode;
    private String rightTitle;
    private TitleRarity rightTitleRarity;
    private String rightTitleColorCode;
    private Long earnedExp;
    private Integer rank;

    public static TodayPlayerResponse of(
        String userId,
        String nickname,
        String profileImageUrl,
        Integer level,
        String title,
        TitleRarity titleRarity,
        String titleColorCode,
        String leftTitle,
        TitleRarity leftTitleRarity,
        String leftTitleColorCode,
        String rightTitle,
        TitleRarity rightTitleRarity,
        String rightTitleColorCode,
        Long earnedExp,
        Integer rank
    ) {
        return TodayPlayerResponse.builder()
            .userId(userId)
            .nickname(nickname)
            .profileImageUrl(profileImageUrl)
            .level(level)
            .title(title)
            .titleRarity(titleRarity)
            .titleColorCode(titleColorCode)
            .leftTitle(leftTitle)
            .leftTitleRarity(leftTitleRarity)
            .leftTitleColorCode(leftTitleColorCode)
            .rightTitle(rightTitle)
            .rightTitleRarity(rightTitleRarity)
            .rightTitleColorCode(rightTitleColorCode)
            .earnedExp(earnedExp)
            .rank(rank)
            .build();
    }
}
