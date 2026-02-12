package io.pinkspider.leveluptogethermvp.userservice.oauth.webhook.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 카카오 연결 해제 경로 타입
 */
@Getter
@RequiredArgsConstructor
public enum KakaoUnlinkReferrerType {

    /**
     * 카카오 계정 삭제로 인한 연결 끊기
     */
    ACCOUNT_DELETE("ACCOUNT_DELETE", "카카오 계정 삭제"),

    /**
     * 카카오 계정 설정 > 연결된 앱 관리에서 연결 끊기
     */
    UNLINK_FROM_APPS("UNLINK_FROM_APPS", "연결된 앱 관리에서 연결 끊기"),

    /**
     * 관리자에 의한 강제 연결 끊기
     */
    FORCED_UNLINK_BY_ADMIN("FORCED_UNLINK_BY_ADMIN", "관리자 강제 연결 끊기"),

    /**
     * 알 수 없는 타입
     */
    UNKNOWN("UNKNOWN", "알 수 없음");

    private final String value;
    private final String description;

    public static KakaoUnlinkReferrerType fromValue(String value) {
        if (value == null) {
            return UNKNOWN;
        }
        for (KakaoUnlinkReferrerType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        return UNKNOWN;
    }
}
