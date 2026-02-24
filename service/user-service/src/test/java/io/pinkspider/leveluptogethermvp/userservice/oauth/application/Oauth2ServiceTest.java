package io.pinkspider.leveluptogethermvp.userservice.oauth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.global.enums.NotificationType;
import io.pinkspider.global.exception.CustomException;
import io.pinkspider.global.security.JwtUtil;
import io.pinkspider.global.security.OAuth2Properties;
import io.pinkspider.global.util.CryptoUtils;
import io.pinkspider.leveluptogethermvp.notificationservice.application.NotificationService;
import io.pinkspider.leveluptogethermvp.userservice.core.feignclient.google.GoogleOAuth2FeignClient;
import io.pinkspider.leveluptogethermvp.userservice.core.feignclient.google.GoogleUserInfoFeignClient;
import io.pinkspider.leveluptogethermvp.userservice.core.feignclient.kakao.KakaoOAuth2FeignClient;
import io.pinkspider.leveluptogethermvp.userservice.core.feignclient.kakao.KakaoUserInfoFeignClient;
import io.pinkspider.leveluptogethermvp.userservice.geoip.GeoIpService;
import io.pinkspider.leveluptogethermvp.userservice.geoip.GeoIpService.GeoIpResult;
import io.pinkspider.leveluptogethermvp.userservice.oauth.components.DeviceIdentifier;
import io.pinkspider.leveluptogethermvp.userservice.oauth.domain.dto.jwt.CreateJwtResponseDto;
import io.pinkspider.leveluptogethermvp.userservice.oauth.domain.dto.jwt.OAuth2LoginUriResponseDto;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.entity.Users;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.enums.UserStatus;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.infrastructure.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;

@ExtendWith(MockitoExtension.class)
class Oauth2ServiceTest {

    @Mock
    private ClientRegistrationRepository clientRegistrationRepository;

    @Mock
    private KakaoOAuth2FeignClient kakaoOAuth2FeignClient;

    @Mock
    private KakaoUserInfoFeignClient kakaoUserInfoFeignClient;

    @Mock
    private GoogleOAuth2FeignClient googleOAuth2FeignClient;

    @Mock
    private GoogleUserInfoFeignClient googleUserInfoFeignClient;

    @Mock
    private UserRepository userRepository;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private MultiDeviceTokenService tokenService;

    @Mock
    private DeviceIdentifier deviceIdentifier;

    @Mock
    private OAuth2Properties oAuth2Properties;

    @Mock
    private GeoIpService geoIpService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private HttpServletRequest httpRequest;

    @InjectMocks
    private Oauth2Service oauth2Service;

    private static final String TEST_USER_ID = "test-user-uuid";
    private static final String TEST_EMAIL = "test@example.com";
    private static final String TEST_NICKNAME = "testNick";
    private static final String TEST_ACCESS_TOKEN = "test-access-token";
    private static final String TEST_REFRESH_TOKEN = "test-refresh-token";
    private static final String TEST_DEVICE_ID = "test-device-id";

    private ClientRegistration buildMockClientRegistration(String registrationId) {
        return ClientRegistration.withRegistrationId(registrationId)
            .clientId("client-id-" + registrationId)
            .clientSecret("client-secret")
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("https://example.com/callback/" + registrationId)
            .authorizationUri("https://provider.example.com/oauth/authorize")
            .tokenUri("https://provider.example.com/oauth/token")
            .scope("email", "profile")
            .build();
    }

    @Nested
    @DisplayName("getOauth2LoginUri 테스트")
    class GetOauth2LoginUriTest {

        @Test
        @DisplayName("Google 로그인 URI를 정상적으로 반환한다")
        void getOauth2LoginUri_google_success() {
            // given
            ClientRegistration registration = buildMockClientRegistration("google");
            when(clientRegistrationRepository.findByRegistrationId("google")).thenReturn(registration);
            when(httpRequest.getHeader("Origin")).thenReturn(null);
            when(httpRequest.getHeader("Referer")).thenReturn(null);

            // when
            OAuth2LoginUriResponseDto result = oauth2Service.getOauth2LoginUri("google", httpRequest);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getAuthUrl()).contains("client_id=client-id-google");
            assertThat(result.getAuthUrl()).contains("response_type=code");
            assertThat(result.getAuthUrl()).contains("scope=email");
        }

        @Test
        @DisplayName("Kakao 로그인 URI를 정상적으로 반환한다")
        void getOauth2LoginUri_kakao_success() {
            // given
            ClientRegistration registration = buildMockClientRegistration("kakao");
            when(clientRegistrationRepository.findByRegistrationId("kakao")).thenReturn(registration);
            when(httpRequest.getHeader("Origin")).thenReturn(null);
            when(httpRequest.getHeader("Referer")).thenReturn(null);

            // when
            OAuth2LoginUriResponseDto result = oauth2Service.getOauth2LoginUri("kakao", httpRequest);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getAuthUrl()).contains("client_id=client-id-kakao");
        }

        @Test
        @DisplayName("지원하지 않는 provider이면 예외를 던진다")
        void getOauth2LoginUri_unsupportedProvider_throwsException() {
            // given
            when(clientRegistrationRepository.findByRegistrationId("unknown")).thenReturn(null);

            // when & then
            assertThatThrownBy(() -> oauth2Service.getOauth2LoginUri("unknown", httpRequest))
                .isInstanceOf(CustomException.class);
        }

        @Test
        @DisplayName("허용된 Origin 헤더가 있으면 동적 redirect URI를 사용한다")
        void getOauth2LoginUri_withAllowedOrigin_usesDynamicRedirectUri() {
            // given
            ClientRegistration registration = buildMockClientRegistration("google");
            when(clientRegistrationRepository.findByRegistrationId("google")).thenReturn(registration);
            when(httpRequest.getHeader("Origin")).thenReturn("https://allowed.example.com");
            when(oAuth2Properties.isAllowedOrigin("https://allowed.example.com")).thenReturn(true);

            // when
            OAuth2LoginUriResponseDto result = oauth2Service.getOauth2LoginUri("google", httpRequest);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getAuthUrl()).contains("redirect_uri=https://allowed.example.com/oauth/callback/google");
        }

        @Test
        @DisplayName("허용되지 않은 Origin은 기본 redirect URI를 사용한다")
        void getOauth2LoginUri_withDisallowedOrigin_usesDefaultRedirectUri() {
            // given
            ClientRegistration registration = buildMockClientRegistration("google");
            when(clientRegistrationRepository.findByRegistrationId("google")).thenReturn(registration);
            when(httpRequest.getHeader("Origin")).thenReturn("https://evil.example.com");
            when(oAuth2Properties.isAllowedOrigin("https://evil.example.com")).thenReturn(false);
            when(clientRegistrationRepository.findByRegistrationId("google")).thenReturn(registration);

            // when
            OAuth2LoginUriResponseDto result = oauth2Service.getOauth2LoginUri("google", httpRequest);

            // then
            assertThat(result).isNotNull();
            // 기본 redirect URI 사용 (ClientRegistration에 설정된 URI)
            assertThat(result.getAuthUrl()).contains("redirect_uri=https://example.com/callback/google");
        }
    }

    @Nested
    @DisplayName("getAppleOauthUri 테스트")
    class GetAppleOauthUriTest {

        @Test
        @DisplayName("Apple 로그인 URI를 정상적으로 반환한다")
        void getAppleOauthUri_success() {
            // given
            ClientRegistration registration = buildMockClientRegistration("apple");
            when(clientRegistrationRepository.findByRegistrationId("apple")).thenReturn(registration);
            when(httpRequest.getHeader("Origin")).thenReturn(null);
            when(httpRequest.getHeader("Referer")).thenReturn(null);

            // when
            OAuth2LoginUriResponseDto result = oauth2Service.getAppleOauthUri("apple", httpRequest);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getAuthUrl()).contains("client_id=client-id-apple");
            // UriComponentsBuilder는 공백을 %20으로 인코딩함 (response_type=code%20id_token)
            assertThat(result.getAuthUrl()).contains("response_type=code");
            assertThat(result.getAuthUrl()).contains("id_token");
            assertThat(result.getAuthUrl()).contains("response_mode=form_post");
            assertThat(result.getAuthUrl()).contains("state=");
        }
    }

    @Nested
    @DisplayName("dbProcessOAuth2User 테스트")
    class DbProcessOAuth2UserTest {

        @Test
        @DisplayName("기존 사용자가 있으면 해당 사용자를 반환한다")
        void dbProcessOAuth2User_existingUser_returnsUser() {
            try (MockedStatic<CryptoUtils> mockedCrypto = mockStatic(CryptoUtils.class)) {
                // given
                Users existingUser = Users.builder()
                    .id(TEST_USER_ID)
                    .email(TEST_EMAIL)
                    .nickname(TEST_NICKNAME)
                    .provider("google")
                    .build();

                mockedCrypto.when(() -> CryptoUtils.encryptAes(TEST_EMAIL)).thenReturn("encrypted-email");
                when(userRepository.findByEncryptedEmailAndProvider("encrypted-email", "google"))
                    .thenReturn(Optional.of(existingUser));

                // 기존 사용자의 경우 save, event publish 등이 호출되지 않아야 함
                io.pinkspider.leveluptogethermvp.userservice.oauth.domain.dto.OAuth2UserInfo userInfo =
                    createMockUserInfo(TEST_EMAIL, TEST_NICKNAME, "google");

                // when
                Users result = oauth2Service.dbProcessOAuth2User(userInfo);

                // then
                assertThat(result).isNotNull();
                assertThat(result.getId()).isEqualTo(TEST_USER_ID);
                verify(userRepository, never()).save(any(Users.class));
                verify(eventPublisher, never()).publishEvent(any());
            }
        }

        @Test
        @DisplayName("탈퇴한 사용자가 로그인을 시도하면 예외를 던진다")
        void dbProcessOAuth2User_withdrawnUser_throwsException() {
            try (MockedStatic<CryptoUtils> mockedCrypto = mockStatic(CryptoUtils.class)) {
                // given
                Users withdrawnUser = Users.builder()
                    .id(TEST_USER_ID)
                    .email(TEST_EMAIL)
                    .nickname("탈퇴한 사용자")
                    .provider("google")
                    .status(UserStatus.WITHDRAWN)
                    .build();

                mockedCrypto.when(() -> CryptoUtils.encryptAes(TEST_EMAIL)).thenReturn("encrypted-email");
                when(userRepository.findByEncryptedEmailAndProvider("encrypted-email", "google"))
                    .thenReturn(Optional.of(withdrawnUser));

                io.pinkspider.leveluptogethermvp.userservice.oauth.domain.dto.OAuth2UserInfo userInfo =
                    createMockUserInfo(TEST_EMAIL, TEST_NICKNAME, "google");

                // when & then
                assertThatThrownBy(() -> oauth2Service.dbProcessOAuth2User(userInfo))
                    .isInstanceOf(CustomException.class)
                    .hasMessageContaining("탈퇴한 계정");
            }
        }

        @Test
        @DisplayName("신규 사용자는 저장하고 회원가입 이벤트를 발행한다")
        void dbProcessOAuth2User_newUser_savesAndPublishesEvent() {
            try (MockedStatic<CryptoUtils> mockedCrypto = mockStatic(CryptoUtils.class)) {
                // given
                Users savedUser = Users.builder()
                    .id(TEST_USER_ID)
                    .email(TEST_EMAIL)
                    .nickname(TEST_NICKNAME)
                    .provider("google")
                    .nicknameSet(false)
                    .build();

                mockedCrypto.when(() -> CryptoUtils.encryptAes(TEST_EMAIL)).thenReturn("encrypted-email");
                when(userRepository.findByEncryptedEmailAndProvider("encrypted-email", "google"))
                    .thenReturn(Optional.empty());
                when(userRepository.existsByNickname(TEST_NICKNAME)).thenReturn(false);
                when(userRepository.save(any(Users.class))).thenReturn(savedUser);

                io.pinkspider.leveluptogethermvp.userservice.oauth.domain.dto.OAuth2UserInfo userInfo =
                    createMockUserInfo(TEST_EMAIL, TEST_NICKNAME, "google");

                // when
                Users result = oauth2Service.dbProcessOAuth2User(userInfo);

                // then
                assertThat(result).isNotNull();
                assertThat(result.getId()).isEqualTo(TEST_USER_ID);
                assertThat(result.isNicknameSet()).isFalse();
                verify(userRepository).save(any(Users.class));
                verify(eventPublisher).publishEvent((Object) any());
            }
        }

        @Test
        @DisplayName("신규 사용자 닉네임이 중복되면 유니크한 닉네임으로 생성한다")
        void dbProcessOAuth2User_duplicateNickname_generatesUniqueNickname() {
            try (MockedStatic<CryptoUtils> mockedCrypto = mockStatic(CryptoUtils.class)) {
                // given
                Users savedUser = Users.builder()
                    .id(TEST_USER_ID)
                    .email(TEST_EMAIL)
                    .nickname(TEST_NICKNAME + "1234")
                    .provider("google")
                    .nicknameSet(false)
                    .build();

                mockedCrypto.when(() -> CryptoUtils.encryptAes(TEST_EMAIL)).thenReturn("encrypted-email");
                when(userRepository.findByEncryptedEmailAndProvider("encrypted-email", "google"))
                    .thenReturn(Optional.empty());
                // TEST_NICKNAME은 중복, 그 외의 닉네임(랜덤 생성된 것)은 중복 없음
                when(userRepository.existsByNickname(anyString()))
                    .thenAnswer(invocation -> {
                        String nickname = invocation.getArgument(0);
                        return TEST_NICKNAME.equals(nickname);
                    });
                when(userRepository.save(any(Users.class))).thenReturn(savedUser);

                io.pinkspider.leveluptogethermvp.userservice.oauth.domain.dto.OAuth2UserInfo userInfo =
                    createMockUserInfo(TEST_EMAIL, TEST_NICKNAME, "google");

                // when
                Users result = oauth2Service.dbProcessOAuth2User(userInfo);

                // then
                assertThat(result).isNotNull();
                verify(userRepository).save(any(Users.class));
            }
        }

        @Test
        @DisplayName("닉네임이 null인 신규 사용자도 정상적으로 가입한다")
        void dbProcessOAuth2User_nullNickname_savesUser() {
            try (MockedStatic<CryptoUtils> mockedCrypto = mockStatic(CryptoUtils.class)) {
                // given
                Users savedUser = Users.builder()
                    .id(TEST_USER_ID)
                    .email(TEST_EMAIL)
                    .nickname(null)
                    .provider("kakao")
                    .nicknameSet(false)
                    .build();

                mockedCrypto.when(() -> CryptoUtils.encryptAes(TEST_EMAIL)).thenReturn("encrypted-email");
                when(userRepository.findByEncryptedEmailAndProvider("encrypted-email", "kakao"))
                    .thenReturn(Optional.empty());
                when(userRepository.save(any(Users.class))).thenReturn(savedUser);

                io.pinkspider.leveluptogethermvp.userservice.oauth.domain.dto.OAuth2UserInfo userInfo =
                    createMockUserInfo(TEST_EMAIL, null, "kakao");

                // when
                Users result = oauth2Service.dbProcessOAuth2User(userInfo);

                // then
                assertThat(result).isNotNull();
                verify(userRepository).save(any(Users.class));
                verify(eventPublisher).publishEvent((Object) any());
            }
        }

        @Test
        @DisplayName("환영 알림 발송이 실패해도 회원가입은 성공한다")
        void dbProcessOAuth2User_notificationFails_signupSucceeds() {
            try (MockedStatic<CryptoUtils> mockedCrypto = mockStatic(CryptoUtils.class)) {
                // given
                Users savedUser = Users.builder()
                    .id(TEST_USER_ID)
                    .email(TEST_EMAIL)
                    .nickname(TEST_NICKNAME)
                    .provider("google")
                    .nicknameSet(false)
                    .build();

                mockedCrypto.when(() -> CryptoUtils.encryptAes(TEST_EMAIL)).thenReturn("encrypted-email");
                when(userRepository.findByEncryptedEmailAndProvider("encrypted-email", "google"))
                    .thenReturn(Optional.empty());
                when(userRepository.existsByNickname(TEST_NICKNAME)).thenReturn(false);
                when(userRepository.save(any(Users.class))).thenReturn(savedUser);

                // 알림 발송 실패 시뮬레이션
                try {
                    org.mockito.Mockito.doThrow(new RuntimeException("알림 서버 오류"))
                        .when(notificationService).sendNotification(
                            anyString(), any(NotificationType.class), any(), any(), anyString());
                } catch (Exception e) {
                    // ignore - just setup
                }

                io.pinkspider.leveluptogethermvp.userservice.oauth.domain.dto.OAuth2UserInfo userInfo =
                    createMockUserInfo(TEST_EMAIL, TEST_NICKNAME, "google");

                // when
                Users result = oauth2Service.dbProcessOAuth2User(userInfo);

                // then - 알림 실패에도 회원가입은 성공
                assertThat(result).isNotNull();
                assertThat(result.getId()).isEqualTo(TEST_USER_ID);
                verify(eventPublisher).publishEvent((Object) any());
            }
        }
    }

    @Nested
    @DisplayName("createJwtFromMobileToken 테스트")
    class CreateJwtFromMobileTokenTest {

        @Test
        @DisplayName("Google 모바일 토큰으로 JWT를 발급한다")
        void createJwtFromMobileToken_google_success() {
            try (MockedStatic<CryptoUtils> mockedCrypto = mockStatic(CryptoUtils.class)) {
                // given
                Users existingUser = Users.builder()
                    .id(TEST_USER_ID)
                    .email(TEST_EMAIL)
                    .nickname(TEST_NICKNAME)
                    .provider("google")
                    .nicknameSet(true)
                    .build();

                Map<String, Object> googleUserInfoMap = new HashMap<>();
                googleUserInfoMap.put("sub", "google-sub-123");
                googleUserInfoMap.put("email", TEST_EMAIL);
                googleUserInfoMap.put("name", TEST_NICKNAME);

                mockedCrypto.when(() -> CryptoUtils.encryptAes(TEST_EMAIL)).thenReturn("encrypted-email");
                when(googleUserInfoFeignClient.getUserInfo("Bearer google-provider-token"))
                    .thenReturn(googleUserInfoMap);
                when(userRepository.findByEncryptedEmailAndProvider("encrypted-email", "google"))
                    .thenReturn(Optional.of(existingUser));
                when(geoIpService.extractClientIp(httpRequest)).thenReturn("127.0.0.1");
                when(geoIpService.lookupCountry("127.0.0.1")).thenReturn(GeoIpResult.empty());
                when(jwtUtil.generateAccessToken(TEST_USER_ID, TEST_EMAIL, TEST_DEVICE_ID))
                    .thenReturn(TEST_ACCESS_TOKEN);
                when(jwtUtil.generateRefreshToken(TEST_USER_ID, TEST_EMAIL, TEST_DEVICE_ID))
                    .thenReturn(TEST_REFRESH_TOKEN);

                // when
                CreateJwtResponseDto result = oauth2Service.createJwtFromMobileToken(
                    httpRequest, "google", "google-provider-token", "mobile", TEST_DEVICE_ID);

                // then
                assertThat(result).isNotNull();
                assertThat(result.getAccessToken()).isEqualTo(TEST_ACCESS_TOKEN);
                assertThat(result.getRefreshToken()).isEqualTo(TEST_REFRESH_TOKEN);
                assertThat(result.getTokenType()).isEqualTo("Bearer");
                assertThat(result.getUserId()).isEqualTo(TEST_USER_ID);
                assertThat(result.getDeviceId()).isEqualTo(TEST_DEVICE_ID);
                assertThat(result.isNicknameSet()).isTrue();
                verify(tokenService).saveTokensToRedis(
                    eq(TEST_USER_ID), eq("mobile"), eq(TEST_DEVICE_ID),
                    eq(TEST_ACCESS_TOKEN), eq(TEST_REFRESH_TOKEN));
            }
        }

        @Test
        @DisplayName("deviceId가 null이면 자동 생성된 deviceId를 사용한다")
        void createJwtFromMobileToken_nullDeviceId_generatesDeviceId() {
            try (MockedStatic<CryptoUtils> mockedCrypto = mockStatic(CryptoUtils.class)) {
                // given
                Users existingUser = Users.builder()
                    .id(TEST_USER_ID)
                    .email(TEST_EMAIL)
                    .nickname(TEST_NICKNAME)
                    .provider("google")
                    .nicknameSet(true)
                    .build();

                Map<String, Object> googleUserInfoMap = new HashMap<>();
                googleUserInfoMap.put("sub", "google-sub-123");
                googleUserInfoMap.put("email", TEST_EMAIL);
                googleUserInfoMap.put("name", TEST_NICKNAME);

                String generatedDeviceId = "generated-device-id";

                mockedCrypto.when(() -> CryptoUtils.encryptAes(TEST_EMAIL)).thenReturn("encrypted-email");
                when(googleUserInfoFeignClient.getUserInfo("Bearer google-provider-token"))
                    .thenReturn(googleUserInfoMap);
                when(userRepository.findByEncryptedEmailAndProvider("encrypted-email", "google"))
                    .thenReturn(Optional.of(existingUser));
                when(geoIpService.extractClientIp(httpRequest)).thenReturn("127.0.0.1");
                when(geoIpService.lookupCountry("127.0.0.1")).thenReturn(GeoIpResult.empty());
                when(deviceIdentifier.generateDeviceId(httpRequest, "mobile")).thenReturn(generatedDeviceId);
                when(jwtUtil.generateAccessToken(TEST_USER_ID, TEST_EMAIL, generatedDeviceId))
                    .thenReturn(TEST_ACCESS_TOKEN);
                when(jwtUtil.generateRefreshToken(TEST_USER_ID, TEST_EMAIL, generatedDeviceId))
                    .thenReturn(TEST_REFRESH_TOKEN);

                // when
                CreateJwtResponseDto result = oauth2Service.createJwtFromMobileToken(
                    httpRequest, "google", "google-provider-token", "mobile", null);

                // then
                assertThat(result).isNotNull();
                assertThat(result.getDeviceId()).isEqualTo(generatedDeviceId);
            }
        }

        @Test
        @DisplayName("deviceType이 null이면 mobile로 기본 설정된다")
        void createJwtFromMobileToken_nullDeviceType_defaultsMobile() {
            try (MockedStatic<CryptoUtils> mockedCrypto = mockStatic(CryptoUtils.class)) {
                // given
                Users existingUser = Users.builder()
                    .id(TEST_USER_ID)
                    .email(TEST_EMAIL)
                    .nickname(TEST_NICKNAME)
                    .provider("google")
                    .nicknameSet(true)
                    .build();

                Map<String, Object> googleUserInfoMap = new HashMap<>();
                googleUserInfoMap.put("sub", "google-sub-123");
                googleUserInfoMap.put("email", TEST_EMAIL);
                googleUserInfoMap.put("name", TEST_NICKNAME);

                mockedCrypto.when(() -> CryptoUtils.encryptAes(TEST_EMAIL)).thenReturn("encrypted-email");
                when(googleUserInfoFeignClient.getUserInfo("Bearer google-provider-token"))
                    .thenReturn(googleUserInfoMap);
                when(userRepository.findByEncryptedEmailAndProvider("encrypted-email", "google"))
                    .thenReturn(Optional.of(existingUser));
                when(geoIpService.extractClientIp(httpRequest)).thenReturn("127.0.0.1");
                when(geoIpService.lookupCountry("127.0.0.1")).thenReturn(GeoIpResult.empty());
                when(deviceIdentifier.generateDeviceId(httpRequest, "mobile")).thenReturn(TEST_DEVICE_ID);
                when(jwtUtil.generateAccessToken(anyString(), anyString(), anyString()))
                    .thenReturn(TEST_ACCESS_TOKEN);
                when(jwtUtil.generateRefreshToken(anyString(), anyString(), anyString()))
                    .thenReturn(TEST_REFRESH_TOKEN);

                // when
                CreateJwtResponseDto result = oauth2Service.createJwtFromMobileToken(
                    httpRequest, "google", "google-provider-token", null, null);

                // then
                assertThat(result).isNotNull();
                // deviceType이 null이면 "mobile"이 사용됨
                verify(tokenService).saveTokensToRedis(
                    anyString(), eq("mobile"), anyString(), anyString(), anyString());
            }
        }

        @Test
        @DisplayName("OAuth2 사용자 정보 조회 실패 시 CustomException을 던진다")
        void createJwtFromMobileToken_userInfoFails_throwsException() {
            // given
            when(googleUserInfoFeignClient.getUserInfo("Bearer invalid-token"))
                .thenThrow(new RuntimeException("Provider error"));

            // when & then
            assertThatThrownBy(() -> oauth2Service.createJwtFromMobileToken(
                httpRequest, "google", "invalid-token", "mobile", TEST_DEVICE_ID))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("소셜 로그인 실패");
        }
    }

    @Nested
    @DisplayName("updateLoginInfo 테스트")
    class UpdateLoginInfoTest {

        @Test
        @DisplayName("IP와 국가 정보를 업데이트한다")
        void updateLoginInfo_success() {
            // given
            Users user = Users.builder()
                .id(TEST_USER_ID)
                .email(TEST_EMAIL)
                .nickname(TEST_NICKNAME)
                .provider("google")
                .build();

            when(geoIpService.extractClientIp(httpRequest)).thenReturn("203.0.113.1");
            when(geoIpService.lookupCountry("203.0.113.1"))
                .thenReturn(new GeoIpResult("South Korea", "KR"));
            when(userRepository.save(any(Users.class))).thenReturn(user);

            // when
            oauth2Service.updateLoginInfo(httpRequest, user);

            // then
            assertThat(user.getLastLoginIp()).isEqualTo("203.0.113.1");
            assertThat(user.getLastLoginCountry()).isEqualTo("South Korea");
            assertThat(user.getLastLoginCountryCode()).isEqualTo("KR");
            verify(userRepository).save(user);
        }

        @Test
        @DisplayName("GeoIP 조회 실패 시에도 예외를 던지지 않는다")
        void updateLoginInfo_geoIpFails_noException() {
            // given
            Users user = Users.builder()
                .id(TEST_USER_ID)
                .email(TEST_EMAIL)
                .nickname(TEST_NICKNAME)
                .provider("google")
                .build();

            when(geoIpService.extractClientIp(httpRequest)).thenThrow(new RuntimeException("GeoIP failure"));

            // when - 예외가 발생하지 않아야 함
            oauth2Service.updateLoginInfo(httpRequest, user);

            // then
            verify(userRepository, never()).save(any());
        }
    }

    // ========== 헬퍼 메서드 ==========

    private io.pinkspider.leveluptogethermvp.userservice.oauth.domain.dto.OAuth2UserInfo createMockUserInfo(
        String email, String nickname, String provider) {
        return new io.pinkspider.leveluptogethermvp.userservice.oauth.domain.dto.OAuth2UserInfo() {
            @Override
            public String getId() {
                return "provider-id-123";
            }

            @Override
            public String getNickname() {
                return nickname;
            }

            @Override
            public String getEmail() {
                return email;
            }

            @Override
            public String getProvider() {
                return provider;
            }
        };
    }
}
