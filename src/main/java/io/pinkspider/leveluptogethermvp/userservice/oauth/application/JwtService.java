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

            if (!slidingExpirationService.isWithinMaxLifetime(refreshToken)) {
                throw new JwtException(UserApiStatus.TOKEN_EXCEEDED_MAXIMUM_LIFETIME.getResultCode(),
                    UserApiStatus.TOKEN_EXCEEDED_MAXIMUM_LIFETIME.getResultMessage());
            }

            // 갱신 가능 여부 확인
            if (!tokenService.canRenewRefreshToken(refreshToken)) {
                throw new JwtException(UserApiStatus.TOKEN_CANNOT_BE_RENEWED.getResultCode(),
                    UserApiStatus.TOKEN_CANNOT_BE_RENEWED.getResultMessage());
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

            // TODO 갱신 정보를 헤더에 추가
            HttpHeaders headers = new HttpHeaders();
            if (refreshTokenRenewed) {
                headers.add("X-Refresh-Token-Renewed", "true");
                headers.add("X-New-Refresh-Expiry", String.valueOf(jwtUtil.getRemainingTime(newRefreshToken)));
            }

            // return ResponseEntity.ok().headers(headers).body(response);

            return ReissueJwtResponseDto.builder()
                .accessToken(newAccessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(900)
                .userId(userId)
                .deviceId(deviceId)
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
                String deviceId = jwtUtil.getDeviceIdFromToken(token);
                String deviceType = request.getHeader("X-Device-Type");

                if (deviceType == null) {
                    deviceType = "web";
                }

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
            String refreshToken = (String) sessionInfo.get("refresh_token");

            Map<String, Object> status = new HashMap<>();
            status.put("access_token_valid", true);
            status.put("access_token_remaining", jwtUtil.getRemainingTime(token));

            if (refreshToken != null) {
                status.put("refresh_token_valid", jwtUtil.validateToken(refreshToken));
                status.put("refresh_token_remaining", jwtUtil.getRemainingTime(refreshToken));
                status.put("should_renew_refresh_token", tokenService.shouldRenewRefreshToken(refreshToken));
                status.put("can_renew_refresh_token", tokenService.canRenewRefreshToken(refreshToken));
            }

            status.put("last_refresh_time", sessionInfo.get("last_refresh_time"));
            status.put("login_time", sessionInfo.get("login_time"));

//            objectMapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
            return objectMapper.convertValue(status, TokenStatusResponseDto.class);
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
