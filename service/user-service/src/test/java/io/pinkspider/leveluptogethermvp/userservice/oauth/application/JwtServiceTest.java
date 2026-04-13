package io.pinkspider.leveluptogethermvp.userservice.oauth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.pinkspider.global.exception.CustomException;
import io.pinkspider.leveluptogethermvp.userservice.core.exception.custom.JwtException;
import io.pinkspider.global.security.JwtUtil;
import io.pinkspider.leveluptogethermvp.userservice.oauth.domain.dto.jwt.ReissueJwtResponseDto;
import io.pinkspider.leveluptogethermvp.userservice.oauth.domain.dto.request.RefreshTokenRequestDto;
import io.pinkspider.leveluptogethermvp.userservice.oauth.domain.dto.response.SessionsResponseDto;
import io.pinkspider.leveluptogethermvp.userservice.oauth.domain.dto.response.SessionsResponseDto.Session;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JwtServiceTest {

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private MultiDeviceTokenService tokenService;

    @Mock
    private SlidingExpirationService slidingExpirationService;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private JwtService jwtService;

    private String testUserId;
    private String testEmail;
    private String testDeviceId;
    private String testDeviceType;
    private String testRefreshToken;
    private String testAccessToken;

    @BeforeEach
    void setUp() {
        testUserId = "test-user-id";
        testEmail = "test@example.com";
        testDeviceId = "test-device-id";
        testDeviceType = "web";
        testRefreshToken = "test-refresh-token";
        testAccessToken = "test-access-token";
    }

    @Nested
    @DisplayName("토큰 재발급 테스트")
    class ReissueTest {

        @Test
        @DisplayName("유효한 refresh token으로 정상적으로 access token을 재발급한다")
        void reissue_success() {
            // given
            RefreshTokenRequestDto request = RefreshTokenRequestDto.builder()
                .refreshToken(testRefreshToken)
                .deviceType(testDeviceType)
                .build();

            String newAccessToken = "new-access-token";

            when(jwtUtil.validateToken(testRefreshToken)).thenReturn(true);
            when(tokenService.isTokenBlacklisted(testRefreshToken)).thenReturn(false);
            when(slidingExpirationService.isWithinMaxLifetime(testRefreshToken)).thenReturn(true);
            when(jwtUtil.getSubjectFromToken(testRefreshToken)).thenReturn(testUserId);
            when(jwtUtil.getEmailFromToken(testRefreshToken)).thenReturn(testEmail);
            when(jwtUtil.getDeviceIdFromToken(testRefreshToken)).thenReturn(testDeviceId);
            when(tokenService.getRefreshToken(testUserId, testDeviceType, testDeviceId)).thenReturn(testRefreshToken);
            when(jwtUtil.generateAccessToken(testUserId, testEmail, testDeviceId)).thenReturn(newAccessToken);
            when(tokenService.shouldRenewRefreshToken(testRefreshToken)).thenReturn(false);

            // when
            ReissueJwtResponseDto response = jwtService.reissue(request);

            // then
            assertThat(response).isNotNull();
            assertThat(response.getAccessToken()).isEqualTo(newAccessToken);
            assertThat(response.getRefreshToken()).isEqualTo(testRefreshToken);
            assertThat(response.getUserId()).isEqualTo(testUserId);
            assertThat(response.getDeviceId()).isEqualTo(testDeviceId);
            assertThat(response.isRefreshTokenRenewed()).isFalse();

            verify(tokenService).updateTokens(testUserId, testDeviceType, testDeviceId, newAccessToken, null);
        }

        @Test
        @DisplayName("refresh token이 3일 미만 남으면 sliding expiration으로 새 refresh token을 발급한다")
        void reissue_withSlidingExpiration() {
            // given
            RefreshTokenRequestDto request = RefreshTokenRequestDto.builder()
                .refreshToken(testRefreshToken)
                .deviceType(testDeviceType)
                .build();

            String newAccessToken = "new-access-token";
            String newRefreshToken = "new-refresh-token";

            when(jwtUtil.validateToken(testRefreshToken)).thenReturn(true);
            when(tokenService.isTokenBlacklisted(testRefreshToken)).thenReturn(false);
            when(slidingExpirationService.isWithinMaxLifetime(testRefreshToken)).thenReturn(true);
            when(jwtUtil.getSubjectFromToken(testRefreshToken)).thenReturn(testUserId);
            when(jwtUtil.getEmailFromToken(testRefreshToken)).thenReturn(testEmail);
            when(jwtUtil.getDeviceIdFromToken(testRefreshToken)).thenReturn(testDeviceId);
            when(tokenService.getRefreshToken(testUserId, testDeviceType, testDeviceId)).thenReturn(testRefreshToken);
            when(jwtUtil.generateAccessToken(testUserId, testEmail, testDeviceId)).thenReturn(newAccessToken);
            when(tokenService.shouldRenewRefreshToken(testRefreshToken)).thenReturn(true);
            when(jwtUtil.generateRefreshToken(testUserId, testEmail, testDeviceId)).thenReturn(newRefreshToken);

            // when
            ReissueJwtResponseDto response = jwtService.reissue(request);

            // then
            assertThat(response).isNotNull();
            assertThat(response.getAccessToken()).isEqualTo(newAccessToken);
            assertThat(response.getRefreshToken()).isEqualTo(newRefreshToken);
            assertThat(response.isRefreshTokenRenewed()).isTrue();

            verify(tokenService).blacklistToken(testRefreshToken);
            verify(tokenService).updateTokens(testUserId, testDeviceType, testDeviceId, newAccessToken, newRefreshToken);
        }

        @Test
        @DisplayName("유효하지 않은 refresh token으로는 재발급할 수 없다")
        void reissue_failWhenInvalidToken() {
            // given
            RefreshTokenRequestDto request = RefreshTokenRequestDto.builder()
                .refreshToken(testRefreshToken)
                .deviceType(testDeviceType)
                .build();

            when(jwtUtil.validateToken(testRefreshToken)).thenReturn(false);

            // when & then
            assertThatThrownBy(() -> jwtService.reissue(request))
                .isInstanceOf(JwtException.class);

            verify(tokenService, never()).updateTokens(anyString(), anyString(), anyString(), anyString(), any());
        }

        @Test
        @DisplayName("블랙리스트된 토큰으로는 재발급할 수 없다")
        void reissue_failWhenBlacklisted() {
            // given
            RefreshTokenRequestDto request = RefreshTokenRequestDto.builder()
                .refreshToken(testRefreshToken)
                .deviceType(testDeviceType)
                .build();

            when(jwtUtil.validateToken(testRefreshToken)).thenReturn(true);
            when(tokenService.isTokenBlacklisted(testRefreshToken)).thenReturn(true);

            // when & then
            assertThatThrownBy(() -> jwtService.reissue(request))
                .isInstanceOf(JwtException.class);

            verify(tokenService, never()).updateTokens(anyString(), anyString(), anyString(), anyString(), any());
        }

        @Test
        @DisplayName("최대 수명(30일)을 초과한 토큰으로는 재발급할 수 없다")
        void reissue_failWhenExceededMaxLifetime() {
            // given
            RefreshTokenRequestDto request = RefreshTokenRequestDto.builder()
                .refreshToken(testRefreshToken)
                .deviceType(testDeviceType)
                .build();

            when(jwtUtil.validateToken(testRefreshToken)).thenReturn(true);
            when(tokenService.isTokenBlacklisted(testRefreshToken)).thenReturn(false);
            when(slidingExpirationService.isWithinMaxLifetime(testRefreshToken)).thenReturn(false);

            // when & then
            assertThatThrownBy(() -> jwtService.reissue(request))
                .isInstanceOf(JwtException.class);

            verify(tokenService, never()).updateTokens(anyString(), anyString(), anyString(), anyString(), any());
        }

        @Test
        @DisplayName("저장된 refresh token과 일치하지 않으면 재발급할 수 없다")
        void reissue_failWhenTokenMismatch() {
            // given
            RefreshTokenRequestDto request = RefreshTokenRequestDto.builder()
                .refreshToken(testRefreshToken)
                .deviceType(testDeviceType)
                .build();

            String differentStoredToken = "different-stored-token";

            when(jwtUtil.validateToken(testRefreshToken)).thenReturn(true);
            when(tokenService.isTokenBlacklisted(testRefreshToken)).thenReturn(false);
            when(slidingExpirationService.isWithinMaxLifetime(testRefreshToken)).thenReturn(true);
            when(jwtUtil.getSubjectFromToken(testRefreshToken)).thenReturn(testUserId);
            when(jwtUtil.getEmailFromToken(testRefreshToken)).thenReturn(testEmail);
            when(jwtUtil.getDeviceIdFromToken(testRefreshToken)).thenReturn(testDeviceId);
            when(tokenService.getRefreshToken(testUserId, testDeviceType, testDeviceId)).thenReturn(differentStoredToken);

            // when & then
            assertThatThrownBy(() -> jwtService.reissue(request))
                .isInstanceOf(JwtException.class);

            verify(tokenService, never()).updateTokens(anyString(), anyString(), anyString(), anyString(), any());
        }
    }

    @Nested
    @DisplayName("Sliding Expiration 정책 테스트")
    class SlidingExpirationPolicyTest {

        @Test
        @DisplayName("refresh token 잔여시간이 3일 이상이면 access token만 재발급한다")
        void onlyAccessTokenRenewedWhenMoreThan3DaysRemaining() {
            // given
            RefreshTokenRequestDto request = RefreshTokenRequestDto.builder()
                .refreshToken(testRefreshToken)
                .deviceType(testDeviceType)
                .build();

            String newAccessToken = "new-access-token";

            when(jwtUtil.validateToken(testRefreshToken)).thenReturn(true);
            when(tokenService.isTokenBlacklisted(testRefreshToken)).thenReturn(false);
            when(slidingExpirationService.isWithinMaxLifetime(testRefreshToken)).thenReturn(true);
            when(jwtUtil.getSubjectFromToken(testRefreshToken)).thenReturn(testUserId);
            when(jwtUtil.getEmailFromToken(testRefreshToken)).thenReturn(testEmail);
            when(jwtUtil.getDeviceIdFromToken(testRefreshToken)).thenReturn(testDeviceId);
            when(tokenService.getRefreshToken(testUserId, testDeviceType, testDeviceId)).thenReturn(testRefreshToken);
            when(jwtUtil.generateAccessToken(testUserId, testEmail, testDeviceId)).thenReturn(newAccessToken);
            when(tokenService.shouldRenewRefreshToken(testRefreshToken)).thenReturn(false);

            // when
            ReissueJwtResponseDto response = jwtService.reissue(request);

            // then
            assertThat(response.isRefreshTokenRenewed()).isFalse();
            assertThat(response.getRefreshToken()).isEqualTo(testRefreshToken);

            verify(jwtUtil, never()).generateRefreshToken(anyString(), anyString(), anyString());
            verify(tokenService, never()).blacklistToken(anyString());
        }

        @Test
        @DisplayName("refresh token 잔여시간이 3일 미만이면 refresh token도 새로 발급한다")
        void refreshTokenRenewedWhenLessThan3DaysRemaining() {
            // given
            RefreshTokenRequestDto request = RefreshTokenRequestDto.builder()
                .refreshToken(testRefreshToken)
                .deviceType(testDeviceType)
                .build();

            String newAccessToken = "new-access-token";
            String newRefreshToken = "new-refresh-token";

            when(jwtUtil.validateToken(testRefreshToken)).thenReturn(true);
            when(tokenService.isTokenBlacklisted(testRefreshToken)).thenReturn(false);
            when(slidingExpirationService.isWithinMaxLifetime(testRefreshToken)).thenReturn(true);
            when(jwtUtil.getSubjectFromToken(testRefreshToken)).thenReturn(testUserId);
            when(jwtUtil.getEmailFromToken(testRefreshToken)).thenReturn(testEmail);
            when(jwtUtil.getDeviceIdFromToken(testRefreshToken)).thenReturn(testDeviceId);
            when(tokenService.getRefreshToken(testUserId, testDeviceType, testDeviceId)).thenReturn(testRefreshToken);
            when(jwtUtil.generateAccessToken(testUserId, testEmail, testDeviceId)).thenReturn(newAccessToken);
            when(tokenService.shouldRenewRefreshToken(testRefreshToken)).thenReturn(true);
            when(jwtUtil.generateRefreshToken(testUserId, testEmail, testDeviceId)).thenReturn(newRefreshToken);

            // when
            ReissueJwtResponseDto response = jwtService.reissue(request);

            // then
            assertThat(response.isRefreshTokenRenewed()).isTrue();
            assertThat(response.getRefreshToken()).isEqualTo(newRefreshToken);

            verify(tokenService).blacklistToken(testRefreshToken);
            verify(tokenService).updateTokens(testUserId, testDeviceType, testDeviceId, newAccessToken, newRefreshToken);
        }
    }

    @Nested
    @DisplayName("logout 테스트")
    class LogoutTest {

        @Test
        @DisplayName("유효한 토큰으로 로그아웃 성공")
        void logout_success() {
            // given
            HttpServletRequest request = mock(HttpServletRequest.class);
            when(request.getHeader("Authorization")).thenReturn("Bearer " + testAccessToken);
            when(jwtUtil.validateToken(testAccessToken)).thenReturn(true);
            when(jwtUtil.getSubjectFromToken(testAccessToken)).thenReturn(testUserId);

            // when
            jwtService.logout(request);

            // then
            verify(tokenService).logoutAllDevices(testUserId);
        }

        @Test
        @DisplayName("토큰이 null이면 로그아웃을 수행하지 않는다")
        void logout_nullToken_noLogout() {
            // given
            HttpServletRequest request = mock(HttpServletRequest.class);
            when(request.getHeader("Authorization")).thenReturn(null);

            // when
            jwtService.logout(request);

            // then
            verify(tokenService, never()).logoutAllDevices(anyString());
        }

        @Test
        @DisplayName("유효하지 않은 토큰이면 로그아웃을 수행하지 않는다")
        void logout_invalidToken_noLogout() {
            // given
            HttpServletRequest request = mock(HttpServletRequest.class);
            when(request.getHeader("Authorization")).thenReturn("Bearer " + testAccessToken);
            when(jwtUtil.validateToken(testAccessToken)).thenReturn(false);

            // when
            jwtService.logout(request);

            // then
            verify(tokenService, never()).logoutAllDevices(anyString());
        }

        @Test
        @DisplayName("예외 발생 시 CustomException을 던진다")
        void logout_exception_throwsCustomException() {
            // given
            HttpServletRequest request = mock(HttpServletRequest.class);
            when(request.getHeader("Authorization")).thenReturn("Bearer " + testAccessToken);
            when(jwtUtil.validateToken(testAccessToken)).thenThrow(new RuntimeException("error"));

            // when & then
            assertThatThrownBy(() -> jwtService.logout(request))
                .isInstanceOf(CustomException.class);
        }
    }

    @Nested
    @DisplayName("logoutAll 테스트")
    class LogoutAllTest {

        @Test
        @DisplayName("유효한 토큰으로 전체 로그아웃 성공")
        void logoutAll_success() {
            // given
            HttpServletRequest request = mock(HttpServletRequest.class);
            when(request.getHeader("Authorization")).thenReturn("Bearer " + testAccessToken);
            when(jwtUtil.validateToken(testAccessToken)).thenReturn(true);
            when(jwtUtil.getSubjectFromToken(testAccessToken)).thenReturn(testUserId);

            // when
            jwtService.logoutAll(request);

            // then
            verify(tokenService).logoutAllDevices(testUserId);
        }

        @Test
        @DisplayName("토큰이 없으면 전체 로그아웃을 수행하지 않는다")
        void logoutAll_noToken_noLogout() {
            // given
            HttpServletRequest request = mock(HttpServletRequest.class);
            when(request.getHeader("Authorization")).thenReturn(null);

            // when
            jwtService.logoutAll(request);

            // then
            verify(tokenService, never()).logoutAllDevices(anyString());
        }

        @Test
        @DisplayName("예외 발생 시 CustomException을 던진다")
        void logoutAll_exception_throwsCustomException() {
            // given
            HttpServletRequest request = mock(HttpServletRequest.class);
            when(request.getHeader("Authorization")).thenReturn("Bearer " + testAccessToken);
            when(jwtUtil.validateToken(testAccessToken)).thenThrow(new RuntimeException("error"));

            // when & then
            assertThatThrownBy(() -> jwtService.logoutAll(request))
                .isInstanceOf(CustomException.class);
        }
    }

    @Nested
    @DisplayName("getActiveSessions 테스트")
    class GetActiveSessionsTest {

        @Test
        @DisplayName("유효한 토큰으로 활성 세션 목록을 조회한다")
        void getActiveSessions_success() {
            // given
            HttpServletRequest request = mock(HttpServletRequest.class);
            when(request.getHeader("Authorization")).thenReturn("Bearer " + testAccessToken);
            when(jwtUtil.validateToken(testAccessToken)).thenReturn(true);
            when(jwtUtil.getSubjectFromToken(testAccessToken)).thenReturn(testUserId);
            when(tokenService.getActiveSessions(testUserId)).thenReturn(List.of());

            // when
            SessionsResponseDto result = jwtService.getActiveSessions(request);

            // then
            assertThat(result).isNotNull();
            verify(tokenService).getActiveSessions(testUserId);
        }

        @Test
        @DisplayName("토큰이 null이면 null을 반환한다")
        void getActiveSessions_nullToken_returnsNull() {
            // given
            HttpServletRequest request = mock(HttpServletRequest.class);
            when(request.getHeader("Authorization")).thenReturn(null);

            // when
            SessionsResponseDto result = jwtService.getActiveSessions(request);

            // then
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("유효하지 않은 토큰이면 null을 반환한다")
        void getActiveSessions_invalidToken_returnsNull() {
            // given
            HttpServletRequest request = mock(HttpServletRequest.class);
            when(request.getHeader("Authorization")).thenReturn("Bearer " + testAccessToken);
            when(jwtUtil.validateToken(testAccessToken)).thenReturn(false);

            // when
            SessionsResponseDto result = jwtService.getActiveSessions(request);

            // then
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("예외 발생 시 CustomException을 던진다")
        void getActiveSessions_exception_throwsCustomException() {
            // given
            HttpServletRequest request = mock(HttpServletRequest.class);
            when(request.getHeader("Authorization")).thenReturn("Bearer " + testAccessToken);
            when(jwtUtil.validateToken(testAccessToken)).thenThrow(new RuntimeException("error"));

            // when & then
            assertThatThrownBy(() -> jwtService.getActiveSessions(request))
                .isInstanceOf(CustomException.class);
        }
    }

    @Nested
    @DisplayName("getTokenStatus 테스트")
    class GetTokenStatusTest {

        @Test
        @DisplayName("refreshToken이 없으면 accessToken 정보만 반환한다")
        void getTokenStatus_noRefreshToken() {
            // given
            HttpServletRequest request = mock(HttpServletRequest.class);
            when(request.getHeader("Authorization")).thenReturn("Bearer " + testAccessToken);
            when(request.getHeader("X-Device-Type")).thenReturn(null);
            when(jwtUtil.validateToken(testAccessToken)).thenReturn(true);
            when(jwtUtil.getSubjectFromToken(testAccessToken)).thenReturn(testUserId);
            when(jwtUtil.getDeviceIdFromToken(testAccessToken)).thenReturn(testDeviceId);
            when(jwtUtil.getRemainingTime(testAccessToken)).thenReturn(3600000L);

            java.util.Map<String, Object> sessionInfo = new java.util.HashMap<>();
            sessionInfo.put("loginTime", "1000000");
            when(tokenService.getSessionInfo(testUserId, "web", testDeviceId)).thenReturn(sessionInfo);

            // when
            var result = jwtService.getTokenStatus(request);

            // then
            assertThat(result).isNotNull();
            assertThat(result.isAccessTokenValid()).isTrue();
        }

        @Test
        @DisplayName("refreshToken이 있으면 refreshToken 정보도 반환한다")
        void getTokenStatus_withRefreshToken() {
            // given
            HttpServletRequest request = mock(HttpServletRequest.class);
            when(request.getHeader("Authorization")).thenReturn("Bearer " + testAccessToken);
            when(request.getHeader("X-Device-Type")).thenReturn(testDeviceType);
            when(jwtUtil.validateToken(testAccessToken)).thenReturn(true);
            when(jwtUtil.getSubjectFromToken(testAccessToken)).thenReturn(testUserId);
            when(jwtUtil.getDeviceIdFromToken(testAccessToken)).thenReturn(testDeviceId);
            when(jwtUtil.getRemainingTime(testAccessToken)).thenReturn(3600000L);

            java.util.Map<String, Object> sessionInfo = new java.util.HashMap<>();
            sessionInfo.put("refreshToken", testRefreshToken);
            sessionInfo.put("loginTime", "1000000");
            when(tokenService.getSessionInfo(testUserId, testDeviceType, testDeviceId)).thenReturn(sessionInfo);
            when(jwtUtil.validateToken(testRefreshToken)).thenReturn(true);
            when(jwtUtil.getRemainingTime(testRefreshToken)).thenReturn(86400000L);
            when(tokenService.shouldRenewRefreshToken(testRefreshToken)).thenReturn(false);
            when(tokenService.canRenewRefreshToken(testRefreshToken)).thenReturn(true);

            // when
            var result = jwtService.getTokenStatus(request);

            // then
            assertThat(result).isNotNull();
            assertThat(result.isRefreshTokenValid()).isTrue();
        }

        @Test
        @DisplayName("loginTime이 있으면 loginTime을 반환한다")
        void getTokenStatus_withLoginTime() {
            // given
            HttpServletRequest request = mock(HttpServletRequest.class);
            when(request.getHeader("Authorization")).thenReturn("Bearer " + testAccessToken);
            when(request.getHeader("X-Device-Type")).thenReturn(testDeviceType);
            when(jwtUtil.validateToken(testAccessToken)).thenReturn(true);
            when(jwtUtil.getSubjectFromToken(testAccessToken)).thenReturn(testUserId);
            when(jwtUtil.getDeviceIdFromToken(testAccessToken)).thenReturn(testDeviceId);
            when(jwtUtil.getRemainingTime(testAccessToken)).thenReturn(3600000L);

            java.util.Map<String, Object> sessionInfo = new java.util.HashMap<>();
            sessionInfo.put("loginTime", "1713000000000");
            sessionInfo.put("lastRefreshTime", "1713010000000");
            when(tokenService.getSessionInfo(testUserId, testDeviceType, testDeviceId)).thenReturn(sessionInfo);

            // when
            var result = jwtService.getTokenStatus(request);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getLoginTime()).isNotNull();
            assertThat(result.getLastRefreshTime()).isEqualTo("1713010000000");
        }

        @Test
        @DisplayName("예외 발생 시 CustomException을 던진다")
        void getTokenStatus_exception_throwsCustomException() {
            // given
            HttpServletRequest request = mock(HttpServletRequest.class);
            when(request.getHeader("Authorization")).thenReturn("Bearer " + testAccessToken);
            when(jwtUtil.validateToken(testAccessToken)).thenReturn(true);
            when(jwtUtil.getSubjectFromToken(testAccessToken)).thenThrow(new RuntimeException("error"));

            // when & then
            assertThatThrownBy(() -> jwtService.getTokenStatus(request))
                .isInstanceOf(CustomException.class);
        }
    }
}
