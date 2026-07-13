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
    private final SlidingExpirationService slidingExpirationService;
    private final ObjectMapper objectMapper;

    @Value("${app.jwt.access-token-expiry:86400000}")
    private long accessTokenExpiryMs;

    public ReissueJwtResponseDto reissue(RefreshTokenRequestDto request) {
        String refreshToken = request.getRefreshToken();
        String deviceType = request.getDeviceType();
        log.info("[reissue] start deviceType={}", deviceType);

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

            // 저장된 리프레시 토큰과 비교
            String storedRefreshToken = tokenService.getRefreshToken(userId, deviceType, deviceId);
            if (!refreshToken.equals(storedRefreshToken)) {
                // rotation 직후 응답 유실 재시도: grace window 내 직전 토큰이면
                // 현재 세션의 refresh 토큰을 그대로 재전달해 불필요한 로그아웃을 막는다
                if (storedRefreshToken != null
                    && tokenService.isWithinRotationGrace(userId, deviceType, deviceId, refreshToken)) {
                    String retryAccessToken = jwtUtil.generateAccessToken(userId, email, deviceId);
                    tokenService.updateTokens(userId, deviceType, deviceId, retryAccessToken, null);
                    log.info("[reissue] rotation grace retry userId={} deviceId={} deviceType={}",
                        userId, deviceId, deviceType);
                    return ReissueJwtResponseDto.builder()
                        .accessToken(retryAccessToken)
                        .refreshToken(storedRefreshToken)
                        .tokenType("Bearer")
                        .expiresIn((int) (accessTokenExpiryMs / 1000))
                        .userId(userId)
                        .deviceId(deviceId)
                        .refreshTokenRenewed(true) // 클라이언트가 보낸 토큰과 다르므로 갱신으로 알린다
                        .build();
                }
                log.info("[reissue] reject reason=NOT_VALID_REFRESH_TOKEN stage=storedTokenMismatch storedNull={} userId={} deviceId={} deviceType={}",
                    storedRefreshToken == null, userId, deviceId, deviceType);
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
        }
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
                String deviceType = request.getHeader("X-Device-Type");
                if (deviceType == null) {
                    deviceType = "web";
                }
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
            String deviceType = request.getHeader("X-Device-Type");
            if (deviceType == null) {
                deviceType = "web";
            }

            Map<String, Object> sessionInfo = tokenService.getSessionInfo(memberId, deviceType, deviceId);
            String refreshToken = (String) sessionInfo.get("refreshToken");

            TokenStatusResponseDto.TokenStatusResponseDtoBuilder builder = TokenStatusResponseDto.builder()
                .accessTokenValid(true)
                .accessTokenRemaining(java.math.BigInteger.valueOf(jwtUtil.getRemainingTime(token)));

            if (refreshToken != null) {
                builder.refreshTokenValid(jwtUtil.validateToken(refreshToken))
                    .refreshTokenRemaining(java.math.BigInteger.valueOf(jwtUtil.getRemainingTime(refreshToken)))
                    .shouldRenewRefreshToken(tokenService.shouldRenewRefreshToken(refreshToken))
                    .canRenewRefreshToken(tokenService.canRenewRefreshToken(refreshToken));
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
