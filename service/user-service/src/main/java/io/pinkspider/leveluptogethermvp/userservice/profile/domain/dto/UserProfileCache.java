package io.pinkspider.leveluptogethermvp.userservice.profile.domain.dto;

import io.pinkspider.global.enums.TitleRarity;
import java.io.Serializable;

/**
 * 사용자 프로필 캐시 DTO
 * - 피드 생성, 길드 멤버 조회 등에서 사용되는 사용자 기본 정보
 * - Redis 캐싱을 위해 Serializable 구현
 */
public record UserProfileCache(
    String userId,
    String nickname,
    String picture,
    Integer level,
    String titleName,
    TitleRarity titleRarity,
    String titleColorCode
) implements Serializable {

    private static final long serialVersionUID = 2L;

    /**
     * 기본값으로 생성 (사용자 정보를 찾을 수 없는 경우)
     */
    public static UserProfileCache defaultProfile(String userId) {
        return new UserProfileCache(userId, "사용자", null, 1, null, null, null);
    }
}
