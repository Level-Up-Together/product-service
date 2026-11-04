package io.pinkspider.leveluptogethermvp.gamificationservice.domain.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CheckLogicDataSource {
    USER_STATS("USER_STATS", "사용자 통계"),
    USER_EXPERIENCE("USER_EXPERIENCE", "사용자 경험치"),
    USER_CATEGORY_EXPERIENCE("USER_CATEGORY_EXPERIENCE", "카테고리별 경험치"),
    FRIEND_SERVICE("FRIEND_SERVICE", "친구 서비스"),
    GUILD_SERVICE("GUILD_SERVICE", "길드 서비스"),
    FEED_SERVICE("FEED_SERVICE", "피드 서비스"),
    /** LUT-634: 레벨업 탑 — visitedFloorCount(처음 들어가 본 층 수)·highestVisitedFloor(가장 높은 층) */
    TOWER_SERVICE("TOWER_SERVICE", "레벨업 탑");

    private final String code;
    private final String displayName;

    @JsonValue
    public String getCode() {
        return code;
    }

    @JsonCreator
    public static CheckLogicDataSource fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (CheckLogicDataSource dataSource : values()) {
            if (dataSource.code.equalsIgnoreCase(code)) {
                return dataSource;
            }
        }
        throw new IllegalArgumentException("Unknown DataSource code: " + code);
    }
}
