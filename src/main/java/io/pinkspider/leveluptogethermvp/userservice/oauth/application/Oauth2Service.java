package io.pinkspider.leveluptogethermvp.userservice.oauth.application;

import com.nimbusds.jwt.JWTClaimsSet;
import io.pinkspider.global.api.ApiStatus;
import io.pinkspider.global.exception.CustomException;
import io.pinkspider.leveluptogethermvp.userservice.core.feignclient.google.GoogleOAuth2FeignClient;
import io.pinkspider.leveluptogethermvp.userservice.core.feignclient.google.GoogleUserInfoFeignClient;
import io.pinkspider.leveluptogethermvp.userservice.core.feignclient.kakao.KakaoOAuth2FeignClient;
import io.pinkspider.leveluptogethermvp.userservice.core.feignclient.kakao.KakaoUserInfoFeignClient;
import io.pinkspider.leveluptogethermvp.userservice.core.util.JwtUtil;
import io.pinkspider.leveluptogethermvp.userservice.oauth.components.DeviceIdentifier;
import io.pinkspider.leveluptogethermvp.userservice.oauth.domain.dto.OAuth2UserInfo;
import io.pinkspider.leveluptogethermvp.userservice.oauth.domain.dto.apple.AppleUserInfo;
import io.pinkspider.leveluptogethermvp.userservice.oauth.domain.dto.google.GoogleUserInfo;
import io.pinkspider.leveluptogethermvp.userservice.oauth.domain.dto.jwt.CreateJwtResponseDto;
import io.pinkspider.leveluptogethermvp.userservice.oauth.domain.dto.jwt.OAuth2LoginUriResponseDto;
import io.pinkspider.leveluptogethermvp.userservice.oauth.domain.dto.kakao.KakaoUserInfo;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.entity.Users;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.infrastructure.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.transaction.Transactional;
import java.text.ParseException;
import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

// OAuth 제공사 관련 서비스
@Service
@RequiredArgsConstructor
@Slf4j
public class Oauth2Service {

    private final ClientRegistrationRepository clientRegistrationRepository;

    private final KakaoOAuth2FeignClient kakaoOAuth2FeignClient;
    private final KakaoUserInfoFeignClient kakaoUserInfoFeignClient;

    private final GoogleOAuth2FeignClient googleOAuth2FeignClient;
    private final GoogleUserInfoFeignClient googleUserInfoFeignClient;

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final MultiDeviceTokenService tokenService;
    private final DeviceIdentifier deviceIdentifier;

    public OAuth2LoginUriResponseDto getOauth2LoginUri(String provider) {
        ClientRegistration clientRegistration = clientRegistrationRepository.findByRegistrationId(provider);
        if (clientRegistration == null) {
            throw new CustomException(ApiStatus.SYSTEM_ERROR.getResultCode(), ApiStatus.SYSTEM_ERROR.getResultMessage());
        }

        // OAuth2 인증 URL 생성
        assert clientRegistration != null;
        String authorizationUri = clientRegistration.getProviderDetails().getAuthorizationUri();
        String clientId = clientRegistration.getClientId();
        String redirectUri = clientRegistration.getRedirectUri();

        // 최종 OAuth2 인증 URL 구성
        String authUrl = UriComponentsBuilder.fromUriString(authorizationUri)
            .queryParam("client_id", clientId)
            .queryParam("redirect_uri", redirectUri)
            .queryParam("response_type", "code")
            .queryParam("scope", String.join(" ", clientRegistration.getScopes()))
            .build()
            .toUriString();

        return OAuth2LoginUriResponseDto.builder()
            .authUrl(authUrl)
            .build();
    }

    // Apple의 경우 OAuth Uri 별도 처리
    public OAuth2LoginUriResponseDto getAppleOauthUri(String provider) {
        ClientRegistration clientRegistration = clientRegistrationRepository.findByRegistrationId(provider);

        String authorizationUri = clientRegistration.getProviderDetails().getAuthorizationUri();
        String clientId = clientRegistration.getClientId();
        String redirectUri = clientRegistration.getRedirectUri();

        String state = UUID.randomUUID().toString(); // CSRF 방지를 위한 랜덤 값
        String authUrl = UriComponentsBuilder.fromUriString(authorizationUri)
            .queryParam("grant_type", "authorization_code")
            .queryParam("client_id", clientId)
            .queryParam("redirect_uri", redirectUri)
            .queryParam("response_type", "code id_token")
            .queryParam("scope", String.join(" ", clientRegistration.getScopes()))
            .queryParam("state", state)
            .queryParam("response_mode", "form_post")
            .toUriString();

        return OAuth2LoginUriResponseDto.builder()
            .authUrl(authUrl)
            .build();
    }

    // Kakao, Google, apple User 정보 받아서 DB 저장하고, 자체 JWT 발급
    public CreateJwtResponseDto createJwt(HttpServletRequest httpRequest,
                                          String provider,
                                          String code,
                                          String deviceType,
                                          String deviceId,
                                          String... idToken) throws Exception {
        if (code == null) {
            throw new Exception("Failed get authorization code");
        }

        // providerToken는 kakao, google일때는 access token, apple일때는 id token 이다.
        String providerToken = "apple".equals(provider) ? idToken[0] : getProviderAccessToken(provider, code);

        OAuth2UserInfo userInfo = getUserInfoFromOAuth2Provider(provider, providerToken);
        Users users = dbProcessOAuth2User(userInfo);

        deviceType = deviceType == null ? "web" : deviceType;
        if (deviceId == null || deviceId.trim().isEmpty()) {
            deviceId = deviceIdentifier.generateDeviceId(httpRequest, deviceType);
        }

        String userId = users.getId();
        String userEmail = users.getEmail();

        String accessToken = jwtUtil.generateAccessToken(userId, userEmail, deviceId);
        String refreshToken = jwtUtil.generateRefreshToken(userId, userEmail, deviceId);
        Date expiredTime = jwtUtil.getAccessTokenExpiredTime(accessToken);

        log.info("user id : {}", users.getId());

        // Redis에 토큰 저장
        tokenService.saveTokensToRedis(
            userId,
//                userId,
            deviceType,
            deviceId,
            accessToken,
            refreshToken
        );
//        return CreateJwtResponseDto.builder()
//            .accessToken(accessToken)
//            .refreshToken(refreshToken)
//            .expiredTime(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(expiredTime))
//            .build();

        return CreateJwtResponseDto.builder()
            .accessToken(accessToken)
            .refreshToken(refreshToken)
            .tokenType("Bearer")
            .expiresIn(900) // 15분
            .userId(userId)
            .deviceId(deviceId)
            .build();
    }

    @Transactional
    protected Users dbProcessOAuth2User(OAuth2UserInfo userInfo) {
        Optional<Users> existingUser = userRepository.findByEmailAndProvider(userInfo.getEmail(), userInfo.getProvider());

        if (existingUser.isPresent()) {
            return existingUser.get(); // 기존 사용자 로그인 처리
        }

        // 신규 사용자 저장
        Users newUsers = Users.builder()
            .email(userInfo.getEmail())
            .name(userInfo.getName())
            .provider(userInfo.getProvider())
//            .role(Role.USER)
            .build();

        return userRepository.save(newUsers);
    }

    private String getProviderAccessToken(String provider, String authorizationCode) {
        ClientRegistration clientRegistration = clientRegistrationRepository.findByRegistrationId(provider);

        String token;
        return switch (provider) {
            case "kakao" -> {
                token = getKakaoAccessToken(clientRegistration, authorizationCode);
                yield token;
            }
            case "google" -> {
                token = getGoogleAccessToken(clientRegistration, authorizationCode);
                yield token;
            }
            default -> throw new IllegalArgumentException("Unsupported provider: " + provider);
        };
    }

    private String getKakaoAccessToken(ClientRegistration clientRegistration, String authorizationCode) {
        Map<String, String> tokenResponse = kakaoOAuth2FeignClient.getAccessToken(
            "authorization_code",
            clientRegistration.getClientId(),
            clientRegistration.getClientSecret(),
            clientRegistration.getRedirectUri(),
            authorizationCode
        );

        if (!tokenResponse.containsKey("access_token")) {
//            return ResponseEntity.badRequest().body(Collections.singletonMap("error", "Failed to retrieve access token"));
        }

        return tokenResponse.get("access_token");
    }

    private String getGoogleAccessToken(ClientRegistration clientRegistration, String authorizationCode) {
        Map<String, String> tokenResponse = googleOAuth2FeignClient.getAccessToken(
            "authorization_code",
            clientRegistration.getClientId(),
            clientRegistration.getClientSecret(),
            clientRegistration.getRedirectUri(),
            authorizationCode
        );

        return tokenResponse.get("access_token");
    }

    // kakao, google providerToken = accessToken, apple은 idToken이다.
    private OAuth2UserInfo getUserInfoFromOAuth2Provider(String provider, String providerToken) throws ParseException {
        Map<String, Object> userInfo;
        return switch (provider) {
            case "google" -> {
                userInfo = googleUserInfoFeignClient.getUserInfo("Bearer " + providerToken);
                yield new GoogleUserInfo(userInfo);
            }
            case "kakao" -> {
                userInfo = kakaoUserInfoFeignClient.getUserInfo("Bearer " + providerToken);
                yield new KakaoUserInfo(userInfo);
            }
            case "apple" -> {
                JWTClaimsSet claims = jwtUtil.decodeIdToken(providerToken); // 🔥 Apple `id_token` 디코딩
                yield new AppleUserInfo(claims); // 🔥 Apple `id_token` 디코딩
            }
            default -> throw new IllegalArgumentException("Unsupported provider: " + provider);
        };
    }
}
