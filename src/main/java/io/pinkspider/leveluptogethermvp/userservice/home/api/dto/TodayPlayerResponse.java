package io.pinkspider.leveluptogethermvp.userservice.home.api.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.userservice.achievement.domain.enums.TitleRarity;
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
    private Long earnedExp;
    private Integer rank;

    public static TodayPlayerResponse of(
        String userId,
        String nickname,
        String profileImageUrl,
        Integer level,
        String title,
        TitleRarity titleRarity,
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
            .earnedExp(earnedExp)
            .rank(rank)
            .build();
    }
}
