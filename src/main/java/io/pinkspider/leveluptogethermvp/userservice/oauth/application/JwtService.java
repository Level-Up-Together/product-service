package io.pinkspider.leveluptogethermvp.userservice.oauth.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.pinkspider.global.exception.CustomException;
import io.pinkspider.leveluptogethermvp.userservice.core.api.UserApiStatus;
import io.pinkspider.leveluptogethermvp.userservice.core.exception.custom.JwtException;
import io.pinkspider.leveluptogethermvp.userservice.core.util.JwtUtil;
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

    public ReissueJwtResponseDto reissue(RefreshTokenRequestDto request) {
        try {
            String refreshToken = request.getRefreshToken();

            // 리프레시 토큰 검증
            if (!jwtUtil.validateToken(refreshToken)) {
                throw new JwtException(UserApiStatus.NOT_VALID_REFRESH_TOKEN.getResultCode(),
                    UserApiStatus.NOT_VALID_REFRESH_TOKEN.getResultMessage());
            }

            // 블랙리스트 확인
            if (tokenService.isTokenBlacklisted(refreshToken)) {
                throw new JwtException(UserApiStatus.BLACKLISTED_JWT.getResultCode(),
                    UserApiStatus.BLACKLISTED_JWT.getResultMessage());
            }

            // 최대 수명(30일) 초과 여부만 확인 - 초과 시 재로그인 필요
            if (!slidingExpirationService.isWithinMaxLifetime(refreshToken)) {
                throw new JwtException(UserApiStatus.TOKEN_EXCEEDED_MAXIMUM_LIFETIME.getResultCode(),
                    UserApiStatus.TOKEN_EXCEEDED_MAXIMUM_LIFETIME.getResultMessage());
            }

            // 토큰에서 정보 추출
            String userId = jwtUtil.getSubjectFromToken(refreshToken);
            String email = jwtUtil.getEmailFromToken(refreshToken);
            String deviceId = jwtUtil.getDeviceIdFromToken(refreshToken);

            // 저장된 리프레시 토큰과 비교
            String storedRefreshToken = tokenService.getRefreshToken(userId, request.getDeviceType(), deviceId);
            if (!refreshToken.equals(storedRefreshToken)) {
                throw new JwtException(UserApiStatus.NOT_VALID_REFRESH_TOKEN.getResultCode(),
                    UserApiStatus.NOT_VALID_REFRESH_TOKEN.getResultMessage());
            }

            // 새로운 액세스 토큰 생성
            String newAccessToken = jwtUtil.generateAccessToken(userId, email, deviceId);

            // Refresh Token 자동 갱신 확인 (Sliding Expiration)
            String newRefreshToken = refreshToken; // 기본값은 기존 토큰
            boolean refreshTokenRenewed = false;

            if (tokenService.shouldRenewRefreshToken(refreshToken)) {
                // 새로운 Refresh Token 생성
                newRefreshToken = jwtUtil.generateRefreshToken(userId, email, deviceId);
                refreshTokenRenewed = true;

                // 기존 Refresh Token 블랙리스트 처리
                tokenService.blacklistToken(refreshToken);
                log.info("Refresh token renewed for user: {}, device: {}", userId, deviceId);
            }

            // Redis에 토큰 업데이트
            tokenService.updateTokens(userId, request.getDeviceType(), deviceId,
                newAccessToken, refreshTokenRenewed ? newRefreshToken : null);

            return ReissueJwtResponseDto.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)  // 갱신된 토큰 반환 (갱신되지 않았으면 기존 토큰)
                .tokenType("Bearer")
                .expiresIn(900)
                .userId(userId)
                .deviceId(deviceId)
                .refreshTokenRenewed(refreshTokenRenewed)  // 갱신 여부 플래그
                .build();

        } catch (JwtException e) {
            throw e;
        } catch (Exception e) {
            throw new CustomException(UserApiStatus.TOKEN_REISSUE_FAILED.getResultCode(), UserApiStatus.TOKEN_REISSUE_FAILED.getResultMessage());
        }
    }


    public void logout(HttpServletRequest request) {
        try {
            String token = getTokenFromRequest(request);
            if (token != null && jwtUtil.validateToken(token)) {
                String userId = jwtUtil.getSubjectFromToken(token);
                // 모든 기기에서 로그아웃 (보안 강화)
                tokenService.logoutAllDevices(userId);
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
