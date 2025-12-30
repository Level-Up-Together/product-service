package io.pinkspider.leveluptogethermvp.userservice.oauth.application;

import com.nimbusds.jwt.JWTClaimsSet;
import io.pinkspider.global.api.ApiStatus;
import io.pinkspider.global.exception.CustomException;
import io.pinkspider.global.util.CryptoUtils;
import io.pinkspider.leveluptogethermvp.userservice.core.feignclient.google.GoogleOAuth2FeignClient;
import io.pinkspider.leveluptogethermvp.userservice.core.feignclient.google.GoogleUserInfoFeignClient;
import io.pinkspider.leveluptogethermvp.userservice.core.feignclient.kakao.KakaoOAuth2FeignClient;
import io.pinkspider.leveluptogethermvp.userservice.core.feignclient.kakao.KakaoUserInfoFeignClient;
import io.pinkspider.leveluptogethermvp.userservice.core.properties.OAuth2Properties;
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
    private final OAuth2Properties oAuth2Properties;

    public OAuth2LoginUriResponseDto getOauth2LoginUri(String provider, HttpServletRequest request) {
        ClientRegistration clientRegistration = clientRegistrationRepository.findByRegistrationId(provider);
        if (clientRegistration == null) {
            throw new CustomException(ApiStatus.SYSTEM_ERROR.getResultCode(), ApiStatus.SYSTEM_ERROR.getResultMessage());
        }

        // OAuth2 인증 URL 생성
        String authorizationUri = clientRegistration.getProviderDetails().getAuthorizationUri();
        String clientId = clientRegistration.getClientId();
        String redirectUri = resolveRedirectUri(request, provider);

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
    public OAuth2LoginUriResponseDto getAppleOauthUri(String provider, HttpServletRequest request) {
        ClientRegistration clientRegistration = clientRegistrationRepository.findByRegistrationId(provider);

        String authorizationUri = clientRegistration.getProviderDetails().getAuthorizationUri();
        String clientId = clientRegistration.getClientId();
        String redirectUri = resolveRedirectUri(request, provider);

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

    /**
     * Origin 헤더 기반으로 redirect URI를 동적으로 결정합니다.
     * 허용된 origin이 아닌 경우 설정 파일의 기본 redirect-url을 사용합니다.
     */
    private String resolveRedirectUri(HttpServletRequest request, String provider) {
        String origin = request.getHeader("Origin");
        if (origin == null || origin.isBlank()) {
            origin = request.getHeader("Referer");
            if (origin != null && !origin.isBlank()) {
                // Referer에서 origin 부분만 추출 (path 제거)
                try {
                    java.net.URI uri = java.net.URI.create(origin);
                    origin = uri.getScheme() + "://" + uri.getHost();
                    if (uri.getPort() != -1 && uri.getPort() != 80 && uri.getPort() != 443) {
                        origin += ":" + uri.getPort();
                    }
                } catch (Exception e) {
                    log.warn("Failed to parse Referer header: {}", origin);
                    origin = null;
                }
            }
        }

        if (origin != null && oAuth2Properties.isAllowedOrigin(origin)) {
            log.info("Using dynamic redirect URI from origin: {}", origin);
            return origin + "/oauth/callback/" + provider;
        }

        // 허용된 origin이 아니면 기본 설정 사용
        ClientRegistration clientRegistration = clientRegistrationRepository.findByRegistrationId(provider);
        String defaultRedirectUri = clientRegistration.getRedirectUri();
        log.info("Using default redirect URI: {}", defaultRedirectUri);
        return defaultRedirectUri;
    }

    /**
     * 모바일 앱용 JWT 발급
     * 네이티브 SDK에서 받은 access_token/id_token을 직접 사용하여 JWT 발급
     */
    public CreateJwtResponseDto createJwtFromMobileToken(HttpServletRequest httpRequest,
                                                          String provider,
                                                          String providerToken,
                                                          String deviceType,
                                                          String deviceId) {
        try {
            OAuth2UserInfo userInfo = getUserInfoFromOAuth2Provider(provider, providerToken);
            Users users = dbProcessOAuth2User(userInfo);

            deviceType = deviceType == null ? "mobile" : deviceType;
            if (deviceId == null || deviceId.trim().isEmpty()) {
                deviceId = deviceIdentifier.generateDeviceId(httpRequest, deviceType);
            }

            String userId = users.getId();
            String userEmail = users.getEmail();

            String accessToken = jwtUtil.generateAccessToken(userId, userEmail, deviceId);
            String refreshToken = jwtUtil.generateRefreshToken(userId, userEmail, deviceId);

            log.info("Mobile login - user id: {}, provider: {}", users.getId(), provider);

            // Redis에 토큰 저장
            tokenService.saveTokensToRedis(
                userId,
                deviceType,
                deviceId,
                accessToken,
                refreshToken
            );

            return CreateJwtResponseDto.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(900) // 15분
                .userId(userId)
                .deviceId(deviceId)
                .build();
        } catch (Exception e) {
            log.error("Mobile login failed - provider: {}, error: {}", provider, e.getMessage());
            throw new CustomException(ApiStatus.INVALID_ACCESS.getResultCode(), "소셜 로그인 실패: " + e.getMessage());
        }
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
        String providerToken = "apple".equals(provider) ? idToken[0] : getProviderAccessToken(httpRequest, provider, code);

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
        // 이메일을 암호화하여 기존 사용자 조회 (JPA @Convert는 쿼리 파라미터에 적용되지 않음)
        String encryptedEmail = CryptoUtils.encryptAes(userInfo.getEmail());
        Optional<Users> existingUser = userRepository.findByEncryptedEmailAndProvider(
            encryptedEmail,
            userInfo.getProvider()
        );

        if (existingUser.isPresent()) {
            log.info("기존 사용자 로그인: userId={}, provider={}", existingUser.get().getId(), userInfo.getProvider());
            return existingUser.get(); // 기존 사용자 로그인 처리
        }

        // 닉네임 중복 확인 및 처리
        String nickname = userInfo.getNickname();
        boolean nicknameSet = false;

        if (nickname != null && !nickname.isBlank()) {
            // 닉네임이 이미 존재하면 유니크한 닉네임 생성
            if (userRepository.existsByNickname(nickname)) {
                nickname = generateUniqueNickname(nickname);
                nicknameSet = false; // 자동 생성된 닉네임이므로 사용자가 다시 설정해야 함
            } else {
                nicknameSet = true; // 원래 닉네임을 그대로 사용
            }
        }

        // 신규 사용자 저장 (provider는 소문자로 정규화)
        Users newUsers = Users.builder()
            .email(userInfo.getEmail())
            .nickname(nickname)
            .provider(userInfo.getProvider().toLowerCase())
            .nicknameSet(nicknameSet)
            .build();

        Users savedUser = userRepository.save(newUsers);
        log.info("신규 사용자 가입: userId={}, provider={}", savedUser.getId(), userInfo.getProvider());
        return savedUser;
    }

    /**
     * 중복되지 않는 유니크한 닉네임 생성
     * 기존 닉네임에 랜덤 숫자를 붙여서 생성
     */
    private String generateUniqueNickname(String baseNickname) {
        // 닉네임이 10자 제한이므로 기본 닉네임을 6자로 자르고 4자리 숫자 추가
        String prefix = baseNickname.length() > 6 ? baseNickname.substring(0, 6) : baseNickname;
        String uniqueNickname;
        int maxAttempts = 100;
        int attempts = 0;

        do {
            int randomNum = (int) (Math.random() * 10000);
            uniqueNickname = prefix + String.format("%04d", randomNum);
            attempts++;
        } while (userRepository.existsByNickname(uniqueNickname) && attempts < maxAttempts);

        if (attempts >= maxAttempts) {
            // 극히 드문 경우: UUID 일부 사용
            uniqueNickname = prefix + UUID.randomUUID().toString().substring(0, 4);
        }

        return uniqueNickname;
    }

    private String getProviderAccessToken(HttpServletRequest request, String provider, String authorizationCode) {
        ClientRegistration clientRegistration = clientRegistrationRepository.findByRegistrationId(provider);
        String redirectUri = resolveRedirectUri(request, provider);

        String token;
        return switch (provider) {
            case "kakao" -> {
                token = getKakaoAccessToken(clientRegistration, redirectUri, authorizationCode);
                yield token;
            }
            case "google" -> {
                token = getGoogleAccessToken(clientRegistration, redirectUri, authorizationCode);
                yield token;
            }
            default -> throw new IllegalArgumentException("Unsupported provider: " + provider);
        };
    }

    private String getKakaoAccessToken(ClientRegistration clientRegistration, String redirectUri, String authorizationCode) {
        Map<String, String> tokenResponse = kakaoOAuth2FeignClient.getAccessToken(
            "authorization_code",
            clientRegistration.getClientId(),
            clientRegistration.getClientSecret(),
            redirectUri,
            authorizationCode
        );

        if (!tokenResponse.containsKey("access_token")) {
//            return ResponseEntity.badRequest().body(Collections.singletonMap("error", "Failed to retrieve access token"));
        }

        return tokenResponse.get("access_token");
    }

    private String getGoogleAccessToken(ClientRegistration clientRegistration, String redirectUri, String authorizationCode) {
        Map<String, String> tokenResponse = googleOAuth2FeignClient.getAccessToken(
            "authorization_code",
            clientRegistration.getClientId(),
            clientRegistration.getClientSecret(),
            redirectUri,
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
