package io.pinkspider.leveluptogethermvp.userservice.oauth.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.pinkspider.global.exception.CustomException;
import io.pinkspider.leveluptogethermvp.userservice.core.api.UserApiStatus;
import io.pinkspider.leveluptogethermvp.userservice.core.exception.custom.JwtException;
import io.pinkspider.global.security.JwtUtil;
import io.pinkspider.leveluptogethermvp.userservice.oauth.domain.dto.jwt.ReissueJwtResponseDto;
import io.pinkspider.leveluptogethermvp.userservice.oauth.domain.dto.request.RefreshTokenRequestDto;
import io.pinkspider.leveluptogethermvp.userservice.oauth.domain.dto.response.SessionsResponseDto;
import io.pinkspider.leveluptogethermvp.userservice.oauth.domain.dto.response.SessionsResponseDto.Session;
import io.pinkspider.leveluptogethermvp.userservice.oauth.components.DeviceTypeResolver;
import io.pinkspider.leveluptogethermvp.userservice.oauth.domain.dto.response.TokenStatusResponseDto;
import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
//@Transactional(readOnly = true)
@Slf4j
public class JwtService {

    private final JwtUtil jwtUtil;
    private final MultiDeviceTokenService tokenService;
    private final DeviceTypeResolver deviceTypeResolver;
    private final SlidingExpirationService slidingExpirationService;
    private final ObjectMapper objectMapper;

    @Value("${app.jwt.access-token-expiry:86400000}")
    private long accessTokenExpiryMs;

    // LUT-336: 세션 rotation 락 대기 (최대 1초). 선행 재발급이 끝나기를 잠깐 기다린다.
    private static final int SESSION_LOCK_MAX_ATTEMPTS = 5;
    private static final long SESSION_LOCK_RETRY_INTERVAL_MS = 200L;

    public ReissueJwtResponseDto reissue(RefreshTokenRequestDto request) {
        String refreshToken = request.getRefreshToken();
        String deviceType = request.getDeviceType();
        log.info("[reissue] start deviceType={}", deviceType);

        // LUT-336: 아래에서 세션 락을 잡으면 채워지고, finally 에서 해제한다
        String lockedUserId = null;
        String lockedDeviceId = null;

        try {
            // 리프레시 토큰 검증
            if (!jwtUtil.validateToken(refreshToken)) {
                log.info("[reissue] reject reason=NOT_VALID_REFRESH_TOKEN stage=validateToken deviceType={}", deviceType);
                throw new JwtException(UserApiStatus.NOT_VALID_REFRESH_TOKEN.getResultCode(),
                    UserApiStatus.NOT_VALID_REFRESH_TOKEN.getResultMessage());
            }

            // 블랙리스트 확인
            if (tokenService.isTokenBlacklisted(refreshToken)) {
                String userIdForLog = safeGetUserId(refreshToken);
                String deviceIdForLog = safeGetDeviceId(refreshToken);
                log.info("[reissue] reject reason=BLACKLISTED_JWT userId={} deviceId={} deviceType={}",
                    userIdForLog, deviceIdForLog, deviceType);
                throw new JwtException(UserApiStatus.BLACKLISTED_JWT.getResultCode(),
                    UserApiStatus.BLACKLISTED_JWT.getResultMessage());
            }

            // 최대 수명(30일) 초과 여부만 확인 - 초과 시 재로그인 필요
            if (!slidingExpirationService.isWithinMaxLifetime(refreshToken)) {
                String userIdForLog = safeGetUserId(refreshToken);
                String deviceIdForLog = safeGetDeviceId(refreshToken);
                log.info("[reissue] reject reason=TOKEN_EXCEEDED_MAXIMUM_LIFETIME userId={} deviceId={} deviceType={}",
                    userIdForLog, deviceIdForLog, deviceType);
                throw new JwtException(UserApiStatus.TOKEN_EXCEEDED_MAXIMUM_LIFETIME.getResultCode(),
                    UserApiStatus.TOKEN_EXCEEDED_MAXIMUM_LIFETIME.getResultMessage());
            }

            // 토큰에서 정보 추출
            String userId = jwtUtil.getSubjectFromToken(refreshToken);
            String email = jwtUtil.getEmailFromToken(refreshToken);
            String deviceId = jwtUtil.getDeviceIdFromToken(refreshToken);

            // 절대 상한: rotation 이 토큰 iat 를 매번 리셋하므로 세션 최초 로그인 시각 기준으로 판정
            Long loginTime = tokenService.getLoginTime(userId, deviceType, deviceId);
            if (!slidingExpirationService.isSessionWithinMaxLifetime(loginTime)) {
                log.info("[reissue] reject reason=TOKEN_EXCEEDED_MAXIMUM_LIFETIME stage=sessionLoginTime userId={} deviceId={} deviceType={}",
                    userId, deviceId, deviceType);
                throw new JwtException(UserApiStatus.TOKEN_EXCEEDED_MAXIMUM_LIFETIME.getResultCode(),
                    UserApiStatus.TOKEN_EXCEEDED_MAXIMUM_LIFETIME.getResultMessage());
            }

            // LUT-336: 같은 세션의 동시 재발급을 직렬화한다. 락 없이 두 요청이 같은 토큰으로 rotation 하면
            // 두 번째가 첫 번째로 방금 발급한 세대를 폐기해, 그 토큰을 쥔 클라이언트가 다음 갱신에서
            // 재로그인당한다. 여기서부터 토큰 비교·rotation 까지가 임계 구역이다.
            if (acquireSessionLock(userId, deviceId)) {
                lockedUserId = userId;
                lockedDeviceId = deviceId;
            }

            // 저장된 리프레시 토큰과 비교 (QA-231: 해시 저장 — 비교는 tokenService 가 수행)
            MultiDeviceTokenService.RefreshTokenMatch tokenMatch =
                tokenService.checkRefreshToken(userId, deviceType, deviceId, refreshToken);
            if (tokenMatch != MultiDeviceTokenService.RefreshTokenMatch.MATCH) {
                // rotation 직후 응답 유실 재시도: grace window 내 직전 토큰이면 새 토큰을 재발급한다.
                // (해시 저장이라 기존 토큰 원문 재전달 불가. previous 기록은 유지되므로
                //  grace window 내 반복 재시도도 허용된다.)
                if (tokenMatch == MultiDeviceTokenService.RefreshTokenMatch.MISMATCH
                    && tokenService.isWithinRotationGrace(userId, deviceType, deviceId, refreshToken)) {
                    String retryAccessToken = jwtUtil.generateAccessToken(userId, email, deviceId);
                    String retryRefreshToken = jwtUtil.generateRefreshToken(userId, email, deviceId);
                    tokenService.updateTokensForGraceRetry(userId, deviceType, deviceId,
                        retryAccessToken, retryRefreshToken);
                    log.info("[reissue] rotation grace retry userId={} deviceId={} deviceType={}",
                        userId, deviceId, deviceType);
                    return ReissueJwtResponseDto.builder()
                        .accessToken(retryAccessToken)
                        .refreshToken(retryRefreshToken)
                        .tokenType("Bearer")
                        .expiresIn((int) (accessTokenExpiryMs / 1000))
                        .userId(userId)
                        .deviceId(deviceId)
                        .refreshTokenRenewed(true)
                        .build();
                }
                log.info("[reissue] reject reason=NOT_VALID_REFRESH_TOKEN stage=storedTokenMismatch storedNull={} userId={} deviceId={} deviceType={}",
                    tokenMatch == MultiDeviceTokenService.RefreshTokenMatch.NO_SESSION,
                    userId, deviceId, deviceType);
                throw new JwtException(UserApiStatus.NOT_VALID_REFRESH_TOKEN.getResultCode(),
                    UserApiStatus.NOT_VALID_REFRESH_TOKEN.getResultMessage());
            }

            // 새로운 액세스 토큰 생성
            String newAccessToken = jwtUtil.generateAccessToken(userId, email, deviceId);

            // Refresh Token 자동 갱신 확인 (Sliding Expiration)
            String newRefreshToken = refreshToken; // 기본값은 기존 토큰
            boolean refreshTokenRenewed = false;

            if (tokenService.shouldRenewRefreshToken(refreshToken)) {
                // 새로운 Refresh Token 생성.
                // 기존 토큰은 즉시 블랙리스트하지 않는다 — updateTokens 가 previous 로 보관해
                // grace window 재시도를 허용하고, 다음 rotation 시점에 블랙리스트한다.
                newRefreshToken = jwtUtil.generateRefreshToken(userId, email, deviceId);
                refreshTokenRenewed = true;
                log.info("Refresh token renewed for user: {}, device: {}", userId, deviceId);
            }

            // Redis에 토큰 업데이트
            tokenService.updateTokens(userId, deviceType, deviceId,
                newAccessToken, refreshTokenRenewed ? newRefreshToken : null);

            log.info("[reissue] success userId={} deviceId={} deviceType={} refreshTokenRenewed={}",
                userId, deviceId, deviceType, refreshTokenRenewed);

            return ReissueJwtResponseDto.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)  // 갱신된 토큰 반환 (갱신되지 않았으면 기존 토큰)
                .tokenType("Bearer")
                .expiresIn((int) (accessTokenExpiryMs / 1000))
                .userId(userId)
                .deviceId(deviceId)
                .refreshTokenRenewed(refreshTokenRenewed)  // 갱신 여부 플래그
                .build();

        } catch (JwtException e) {
            throw e;
        } catch (Exception e) {
            log.error("[reissue] unexpected error deviceType={} message={}", deviceType, e.getMessage(), e);
            throw new CustomException(UserApiStatus.TOKEN_REISSUE_FAILED.getResultCode(), UserApiStatus.TOKEN_REISSUE_FAILED.getResultMessage());
        } finally {
            if (lockedUserId != null) {
                tokenService.unlockSession(lockedUserId, lockedDeviceId);
            }
        }
    }

    /**
     * 세션 rotation 락 획득 (LUT-336).
     *
     * <p>선점된 경우 짧게 대기하며 재시도한다. 끝내 못 잡아도 진행한다(fail-open) — 락은 정합성 게이트가
     * 아니라 경합 완화 수단이고, Redis 문제로 재발급 자체가 막히면 그게 더 큰 장애다. 대기 후에는 보통
     * 선행 요청이 끝나 있어, 이어지는 토큰 비교가 grace 경로로 흘러 정상 재발급된다.
     */
    private boolean acquireSessionLock(String userId, String deviceId) {
        for (int attempt = 0; attempt < SESSION_LOCK_MAX_ATTEMPTS; attempt++) {
            if (tokenService.tryLockSession(userId, deviceId)) {
                return true;
            }
            try {
                Thread.sleep(SESSION_LOCK_RETRY_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        log.warn("[reissue] session lock not acquired, proceeding userId={} deviceId={}",
            userId, deviceId);
        return false;
    }

    private String safeGetUserId(String token) {
        try {
            return jwtUtil.getSubjectFromToken(token);
        } catch (Exception e) {
            return null;
        }
    }

    private String safeGetDeviceId(String token) {
        try {
            return jwtUtil.getDeviceIdFromToken(token);
        } catch (Exception e) {
            return null;
        }
    }


    public void logout(HttpServletRequest request) {
        try {
            String token = getTokenFromRequest(request);
            if (token != null && jwtUtil.validateToken(token)) {
                String userId = jwtUtil.getSubjectFromToken(token);
                String deviceId = jwtUtil.getDeviceIdFromToken(token);
                // LUT-336: 재발급(본문)·로그인과 같은 규칙으로 정규화
                String deviceType =
                    deviceTypeResolver.resolve(request, request.getHeader("X-Device-Type"));
                // 현재 디바이스만 로그아웃 (다른 디바이스 세션은 유지)
                tokenService.logout(userId, deviceType, deviceId);
            }
        } catch (Exception e) {
            throw new CustomException(UserApiStatus.LOGOUT_FAILED.getResultCode(), UserApiStatus.LOGOUT_FAILED.getResultMessage());
        }
    }

    public void logoutAll(HttpServletRequest request) {
        try {
            String token = getTokenFromRequest(request);
            if (token != null && jwtUtil.validateToken(token)) {
                String userId = jwtUtil.getSubjectFromToken(token);
                tokenService.logoutAllDevices(userId);
            }
        } catch (Exception e) {
            throw new CustomException(UserApiStatus.LOGOUT_ALL_FAILED.getResultCode(), UserApiStatus.LOGOUT_ALL_FAILED.getResultMessage());
        }
    }

    public SessionsResponseDto getActiveSessions(HttpServletRequest request) {
        try {
            String token = getTokenFromRequest(request);
            if (token != null && jwtUtil.validateToken(token)) {
                String memberId = jwtUtil.getSubjectFromToken(token);
                List<Session> sessionList = tokenService.getActiveSessions(memberId);

                return SessionsResponseDto.builder()
                    .sessionList(sessionList)
                    .build();
            }
        } catch (Exception e) {
            throw new CustomException(UserApiStatus.FAILED_TO_GET_SESSIONS.getResultCode(), UserApiStatus.FAILED_TO_GET_SESSIONS.getResultMessage());
        }
        return null;
    }

    public TokenStatusResponseDto getTokenStatus(HttpServletRequest request) {
        try {
            String token = getTokenFromRequest(request);
            if (token == null || !jwtUtil.validateToken(token)) {
//                throw new JwtException(UserApiStatus.NOT_VALID_ACCESS_TOKEN.getResultCode(),
//                    UserApiStatus.NOT_VALID_ACCESS_TOKEN.getResultMessage());
            }

            String memberId = jwtUtil.getSubjectFromToken(token);
            String deviceId = jwtUtil.getDeviceIdFromToken(token);
            String deviceType =
                deviceTypeResolver.resolve(request, request.getHeader("X-Device-Type"));

            // QA-231: 세션은 refresh 원문을 보관하지 않으므로 getSessionInfo 가 계산한 판정값 사용
            Map<String, Object> sessionInfo = tokenService.getSessionInfo(memberId, deviceType, deviceId);

            TokenStatusResponseDto.TokenStatusResponseDtoBuilder builder = TokenStatusResponseDto.builder()
                .accessTokenValid(true)
                .accessTokenRemaining(java.math.BigInteger.valueOf(jwtUtil.getRemainingTime(token)));

            Object refreshTokenValid = sessionInfo.get("refreshTokenValid");
            if (refreshTokenValid != null) {
                builder.refreshTokenValid((Boolean) refreshTokenValid)
                    .refreshTokenRemaining(java.math.BigInteger.valueOf(
                        ((Number) sessionInfo.get("refreshTokenRemaining")).longValue()))
                    .shouldRenewRefreshToken((Boolean) sessionInfo.get("shouldRenewRefreshToken"))
                    .canRenewRefreshToken((Boolean) sessionInfo.get("canRenewRefreshToken"));
            }

            String lastRefreshTime = (String) sessionInfo.get("lastRefreshTime");
            builder.lastRefreshTime(lastRefreshTime);

            Object loginTimeObj = sessionInfo.get("loginTime");
            if (loginTimeObj != null) {
                builder.loginTime(new java.math.BigInteger(loginTimeObj.toString()));
            }

            return builder.build();
        } catch (Exception e) {
            throw new CustomException(UserApiStatus.FAILED_TO_GET_TOKEN_STATUS.getResultCode(),
                UserApiStatus.FAILED_TO_GET_TOKEN_STATUS.getResultMessage());
        }
    }

    private String getTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
