package io.pinkspider.leveluptogethermvp.userservice.oauth.application;

import io.jsonwebtoken.Claims;
import io.pinkspider.leveluptogethermvp.userservice.core.properties.JwtProperties;
import io.pinkspider.global.security.JwtUtil;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SlidingExpirationService {

    private final JwtProperties jwtProperties;
    private final JwtUtil jwtUtil;

    // 잔여시간(ms) 기반 갱신 필요 여부 — 해시 저장 세션(원문 없음)의 exp 메타데이터용 (QA-231)
    public boolean shouldRenewByRemainingMillis(long remainingMillis) {
        return remainingMillis > 0 && remainingMillis < jwtProperties.getRenewalThresholdMillis();
    }

    // Refresh Token 갱신 필요 여부 확인 (설정 기반)
    public boolean shouldRenewRefreshToken(String refreshToken) {
        if (refreshToken == null) {
            return false;
        }

        try {
            long remainingTime = jwtUtil.getRemainingTime(refreshToken);
            return remainingTime > 0 && remainingTime < jwtProperties.getRenewalThresholdMillis();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 세션 최초 로그인 시각 기준 절대 상한 판정.
     * rotation 시 토큰의 iat 가 매번 리셋되므로 토큰 나이(isWithinMaxLifetime)로는
     * 절대 상한을 강제할 수 없다 — 세션의 loginTime 이 정확한 기준이다.
     * loginTime 이 없는 레거시 세션은 관대하게 통과시킨다.
     */
    public boolean isSessionWithinMaxLifetime(Long loginTimeMillis) {
        if (loginTimeMillis == null) {
            return true;
        }
        return System.currentTimeMillis() - loginTimeMillis < jwtProperties.getMaxLifetimeMillis();
    }

    // 토큰이 최대 수명 내에 있는지 확인
    public boolean isWithinMaxLifetime(String refreshToken) {
        try {
            Claims claims = jwtUtil.getClaimsFromToken(refreshToken);
            Date issuedAt = claims.getIssuedAt();
            long tokenAge = System.currentTimeMillis() - issuedAt.getTime();

            return tokenAge < jwtProperties.getMaxLifetimeMillis();
        } catch (Exception e) {
            return false;
        }
    }

    // 최대 수명까지 남은 시간 계산
    public long calculateRemainingMaxLifetime(String refreshToken) {
        try {
            Claims claims = jwtUtil.getClaimsFromToken(refreshToken);
            Date issuedAt = claims.getIssuedAt();
            long tokenAge = System.currentTimeMillis() - issuedAt.getTime();

            return Math.max(0, jwtProperties.getMaxLifetimeMillis() - tokenAge);
        } catch (Exception e) {
            return 0;
        }
    }

    // 토큰 갱신 정책 검증
    public boolean canRenewToken(String refreshToken) {
        return isWithinMaxLifetime(refreshToken) && shouldRenewRefreshToken(refreshToken);
    }

    // 갱신 정보를 포함한 토큰 상태 반환
    public Map<String, Object> getTokenRenewalInfo(String refreshToken) {
        Map<String, Object> info = new HashMap<>();

        try {
            long remainingTime = jwtUtil.getRemainingTime(refreshToken);
            long remainingMaxLifetime = calculateRemainingMaxLifetime(refreshToken);

            info.put("remaining_time", remainingTime);
            info.put("remaining_max_lifetime", remainingMaxLifetime);
            info.put("should_renew", shouldRenewRefreshToken(refreshToken));
            info.put("can_renew", canRenewToken(refreshToken));
            info.put("within_max_lifetime", isWithinMaxLifetime(refreshToken));
            info.put("renewal_threshold", jwtProperties.getRenewalThresholdMillis());
        } catch (Exception e) {
            info.put("error", e.getMessage());
            info.put("canRenew", false);
        }

        return info;
    }
}
