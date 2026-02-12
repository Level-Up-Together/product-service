package io.pinkspider.leveluptogethermvp.userservice.oauth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.pinkspider.leveluptogethermvp.userservice.core.exception.custom.JwtException;
import io.pinkspider.global.security.JwtUtil;
import io.pinkspider.leveluptogethermvp.userservice.oauth.domain.dto.jwt.ReissueJwtResponseDto;
import io.pinkspider.leveluptogethermvp.userservice.oauth.domain.dto.request.RefreshTokenRequestDto;
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
}
