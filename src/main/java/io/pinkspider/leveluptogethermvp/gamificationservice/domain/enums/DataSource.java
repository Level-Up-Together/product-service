package io.pinkspider.leveluptogethermvp.gamificationservice.domain.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 업적 체크 로직의 데이터 소스
 */
@Getter
@RequiredArgsConstructor
public enum DataSource {
    USER_STATS("USER_STATS", "사용자 통계"),
    USER_EXPERIENCE("USER_EXPERIENCE", "사용자 경험치"),
    FRIEND_SERVICE("FRIEND_SERVICE", "친구 서비스"),
    GUILD_SERVICE("GUILD_SERVICE", "길드 서비스"),
    FEED_SERVICE("FEED_SERVICE", "피드 서비스");

    private final String code;
    private final String displayName;

    @JsonValue
    public String getCode() {
        return code;
    }

    @JsonCreator
    public static DataSource fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (DataSource dataSource : values()) {
            if (dataSource.code.equalsIgnoreCase(code)) {
                return dataSource;
            }
        }
        return null;
    }
}
