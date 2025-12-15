package io.pinkspider.leveluptogethermvp.userservice.home.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TodayPlayerResponse {

    private String userId;
    private String nickname;
    private String profileImageUrl;
    private Integer level;
    private String title;
    private Long earnedExp;
    private Integer rank;

    public static TodayPlayerResponse of(
        String userId,
        String nickname,
        String profileImageUrl,
        Integer level,
        String title,
        Long earnedExp,
        Integer rank
    ) {
        return TodayPlayerResponse.builder()
            .userId(userId)
            .nickname(nickname)
            .profileImageUrl(profileImageUrl)
            .level(level)
            .title(title)
            .earnedExp(earnedExp)
            .rank(rank)
            .build();
    }
}
