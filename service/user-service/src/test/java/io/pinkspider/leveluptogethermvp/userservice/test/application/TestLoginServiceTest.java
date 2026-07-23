package io.pinkspider.leveluptogethermvp.userservice.test.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
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
                when(userRepository.findActiveByEncryptedEmailAndProvider("encrypted-email", "test"))
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
                when(userRepository.findActiveByEncryptedEmailAndProvider("encrypted", "test"))
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
        @DisplayName("testUserId로 기존 사용자를 조회한다 (이메일 조회보다 우선)")
        void loginWithTestUserId() {
            // given
            Users existingUser = Users.builder()
                .id("specific-id")
                .email("spec@test.com")
                .nickname("spec")
                .provider("test")
                .build();

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
            verify(userRepository, never()).findActiveByEncryptedEmailAndProvider(anyString(), anyString());
        }

        @Test
        @DisplayName("testUserId 명시 + DB 미존재: testUserId 무시하고 자동 UUID로 신규 생성")
        void loginWithTestUserId_notFound_createsNewUserWithAutoId() {
            try (MockedStatic<CryptoUtils> mockedCrypto = mockStatic(CryptoUtils.class)) {
                // given
                String testUserId = "e2e00001-0000-0000-0000-000000000001";
                String email = "e2e-user-001@test.com";
                String autoId = "generated-uuid";
                Users savedUser = Users.builder()
                    .id(autoId).email(email).nickname("e2e-user-").provider("test").build();

                mockedCrypto.when(() -> CryptoUtils.encryptAes(email)).thenReturn("encrypted");
                when(userRepository.findById(testUserId)).thenReturn(Optional.empty());
                when(userRepository.findActiveByEncryptedEmailAndProvider("encrypted", "test"))
                    .thenReturn(Optional.empty());
                when(userRepository.existsByNickname(anyString())).thenReturn(false);
                when(userRepository.save(any(Users.class))).thenReturn(savedUser);
                when(jwtUtil.generateAccessToken(anyString(), anyString(), anyString())).thenReturn("at");
                when(jwtUtil.generateRefreshToken(anyString(), anyString(), anyString())).thenReturn("rt");
                when(deviceIdentifier.generateDeviceId(any(), anyString())).thenReturn("dev");

                // when
                CreateJwtResponseDto result = testLoginService.loginAsTestUser(
                    httpRequest, testUserId, email, null, null, null);

                // then: save 호출, 응답 ID = 자동 부여 ID (요청 testUserId 아님)
                verify(userRepository).save(any(Users.class));
                assertThat(result.getUserId()).isEqualTo(autoId);
                assertThat(result.getUserId()).isNotEqualTo(testUserId);
            }
        }

        @Test
        @DisplayName("testUserId 미지정 + 이메일 매칭 시 기존 user 반환")
        void loginWithoutTestUserId_emailMatch_returnsExisting() {
            try (MockedStatic<CryptoUtils> mockedCrypto = mockStatic(CryptoUtils.class)) {
                // given
                String email = "exist@test.com";
                Users existing = Users.builder()
                    .id("existing-id").email(email).nickname("exist").provider("test").build();

                mockedCrypto.when(() -> CryptoUtils.encryptAes(email)).thenReturn("encrypted");
                when(userRepository.findActiveByEncryptedEmailAndProvider("encrypted", "test"))
                    .thenReturn(Optional.of(existing));
                when(jwtUtil.generateAccessToken(anyString(), anyString(), anyString())).thenReturn("at");
                when(jwtUtil.generateRefreshToken(anyString(), anyString(), anyString())).thenReturn("rt");
                when(deviceIdentifier.generateDeviceId(any(), anyString())).thenReturn("dev");

                // when
                CreateJwtResponseDto result = testLoginService.loginAsTestUser(
                    httpRequest, null, email, null, null, null);

                // then: 신규 save 미호출
                verify(userRepository, never()).save(any(Users.class));
                assertThat(result.getUserId()).isEqualTo("existing-id");
            }
        }
    }
}
