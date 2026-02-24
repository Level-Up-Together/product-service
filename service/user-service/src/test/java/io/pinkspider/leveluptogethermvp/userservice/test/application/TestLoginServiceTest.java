package io.pinkspider.leveluptogethermvp.userservice.test.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.global.security.JwtUtil;
import io.pinkspider.global.util.CryptoUtils;
import io.pinkspider.leveluptogethermvp.userservice.oauth.application.MultiDeviceTokenService;
import io.pinkspider.leveluptogethermvp.userservice.oauth.components.DeviceIdentifier;
import io.pinkspider.leveluptogethermvp.userservice.oauth.domain.dto.jwt.CreateJwtResponseDto;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.entity.Users;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.infrastructure.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class TestLoginServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private MultiDeviceTokenService tokenService;

    @Mock
    private DeviceIdentifier deviceIdentifier;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private HttpServletRequest httpRequest;

    @InjectMocks
    private TestLoginService testLoginService;

    @Nested
    @DisplayName("loginAsTestUser 테스트")
    class LoginAsTestUserTest {

        @Test
        @DisplayName("기존 사용자가 있으면 해당 사용자로 로그인한다")
        void loginWithExistingUser() {
            try (MockedStatic<CryptoUtils> mockedCrypto = mockStatic(CryptoUtils.class)) {
                // given
                Users existingUser = Users.builder()
                    .id("existing-user-id")
                    .email("test@test.com")
                    .nickname("tester")
                    .provider("test")
                    .build();

                mockedCrypto.when(() -> CryptoUtils.encryptAes("test@test.com"))
                    .thenReturn("encrypted-email");
                when(userRepository.findByEncryptedEmailAndProvider("encrypted-email", "test"))
                    .thenReturn(Optional.of(existingUser));
                when(jwtUtil.generateAccessToken(anyString(), anyString(), anyString()))
                    .thenReturn("access-token");
                when(jwtUtil.generateRefreshToken(anyString(), anyString(), anyString()))
                    .thenReturn("refresh-token");
                when(deviceIdentifier.generateDeviceId(any(), anyString()))
                    .thenReturn("device-123");

                // when
                CreateJwtResponseDto result = testLoginService.loginAsTestUser(
                    httpRequest, null, "test@test.com", null, null, null);

                // then
                assertThat(result.getAccessToken()).isEqualTo("access-token");
                assertThat(result.getRefreshToken()).isEqualTo("refresh-token");
                assertThat(result.getTokenType()).isEqualTo("Bearer");
                verify(tokenService).saveTokensToRedis(anyString(), anyString(), anyString(), anyString(), anyString());
            }
        }

        @Test
        @DisplayName("새 사용자를 생성하고 로그인한다")
        void loginWithNewUser() {
            try (MockedStatic<CryptoUtils> mockedCrypto = mockStatic(CryptoUtils.class)) {
                // given
                Users newUser = Users.builder()
                    .id("new-user-id")
                    .email("new@test.com")
                    .nickname("newuser")
                    .provider("test")
                    .build();

                mockedCrypto.when(() -> CryptoUtils.encryptAes("new@test.com"))
                    .thenReturn("encrypted");
                when(userRepository.findByEncryptedEmailAndProvider("encrypted", "test"))
                    .thenReturn(Optional.empty());
                when(userRepository.existsByNickname(anyString())).thenReturn(false);
                when(userRepository.save(any(Users.class))).thenReturn(newUser);
                when(jwtUtil.generateAccessToken(anyString(), anyString(), anyString()))
                    .thenReturn("access-token");
                when(jwtUtil.generateRefreshToken(anyString(), anyString(), anyString()))
                    .thenReturn("refresh-token");

                // when
                CreateJwtResponseDto result = testLoginService.loginAsTestUser(
                    httpRequest, null, "new@test.com", "newuser", "mobile", "my-device");

                // then
                assertThat(result.getAccessToken()).isEqualTo("access-token");
                assertThat(result.getUserId()).isEqualTo("new-user-id");
                assertThat(result.getDeviceId()).isEqualTo("my-device");
                verify(eventPublisher).publishEvent((Object) any());
            }
        }

        @Test
        @DisplayName("testUserId로 기존 사용자를 조회한다")
        void loginWithTestUserId() {
            try (MockedStatic<CryptoUtils> mockedCrypto = mockStatic(CryptoUtils.class)) {
                // given
                Users existingUser = Users.builder()
                    .id("specific-id")
                    .email("spec@test.com")
                    .nickname("spec")
                    .provider("test")
                    .build();

                mockedCrypto.when(() -> CryptoUtils.encryptAes("spec@test.com"))
                    .thenReturn("encrypted");
                when(userRepository.findByEncryptedEmailAndProvider("encrypted", "test"))
                    .thenReturn(Optional.empty());
                when(userRepository.findById("specific-id"))
                    .thenReturn(Optional.of(existingUser));
                when(jwtUtil.generateAccessToken(anyString(), anyString(), anyString()))
                    .thenReturn("at");
                when(jwtUtil.generateRefreshToken(anyString(), anyString(), anyString()))
                    .thenReturn("rt");
                when(deviceIdentifier.generateDeviceId(any(), anyString()))
                    .thenReturn("dev");

                // when
                CreateJwtResponseDto result = testLoginService.loginAsTestUser(
                    httpRequest, "specific-id", "spec@test.com", null, null, null);

                // then
                assertThat(result.getUserId()).isEqualTo("specific-id");
            }
        }
    }
}
