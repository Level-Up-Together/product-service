package io.pinkspider.leveluptogethermvp.userservice.home.api.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.global.enums.TitleRarity;
import io.pinkspider.global.facade.dto.EquippedItemRarityDto;
import java.util.List;
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

    // LUT-424: 장착 아이템 타입·희귀도 (썸네일 등급 표식용). 미장착이면 빈 배열.
    private List<EquippedItemRarityDto> equippedItemRarities;

    /** 구버전 Redis 캐시(todayPlayers*) 역직렬화 시 필드 부재 → null 방지 (항상 배열 보장) */
    public List<EquippedItemRarityDto> getEquippedItemRarities() {
        return equippedItemRarities != null ? equippedItemRarities : List.of();
    }

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
        Integer rank,
        List<EquippedItemRarityDto> equippedItemRarities
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
            .equippedItemRarities(equippedItemRarities != null ? equippedItemRarities : List.of())
            .build();
    }
}
