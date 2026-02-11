package io.pinkspider.leveluptogethermvp.feedservice.domain.dto;

import io.pinkspider.global.enums.TitleRarity;

/**
 * 사용자의 장착된 칭호 정보
 * 조합된 칭호 이름, 가장 높은 등급, 가장 높은 등급의 색상 코드를 포함합니다.
 */
public record UserTitleInfo(
    String titleName,
    TitleRarity titleRarity,
    String colorCode
) {
    public static UserTitleInfo empty() {
        return new UserTitleInfo(null, null, null);
    }
}
