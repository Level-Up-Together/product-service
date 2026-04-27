package io.pinkspider.leveluptogethermvp.userservice.oauth.application;

import com.nimbusds.jwt.JWTClaimsSet;
import io.pinkspider.global.api.ApiStatus;
import io.pinkspider.global.exception.CustomException;
import io.pinkspider.global.util.CryptoUtils;
import io.pinkspider.leveluptogethermvp.userservice.core.feignclient.google.GoogleOAuth2FeignClient;
import io.pinkspider.leveluptogethermvp.userservice.core.feignclient.google.GoogleUserInfoFeignClient;
import io.pinkspider.leveluptogethermvp.userservice.core.feignclient.kakao.KakaoOAuth2FeignClient;
import io.pinkspider.leveluptogethermvp.userservice.core.feignclient.kakao.KakaoUserInfoFeignClient;
import io.pinkspider.global.security.OAuth2Properties;
import io.pinkspider.global.security.JwtUtil;
import io.pinkspider.leveluptogethermvp.userservice.oauth.components.DeviceIdentifier;
import io.pinkspider.leveluptogethermvp.userservice.oauth.domain.dto.OAuth2UserInfo;
import io.pinkspider.leveluptogethermvp.userservice.oauth.domain.dto.apple.AppleUserInfo;
import io.pinkspider.leveluptogethermvp.userservice.oauth.domain.dto.google.GoogleUserInfo;
import io.pinkspider.leveluptogethermvp.userservice.oauth.domain.SignupSessionData;
import io.pinkspider.leveluptogethermvp.userservice.oauth.domain.dto.jwt.CreateJwtResponseDto;
import io.pinkspider.leveluptogethermvp.userservice.oauth.domain.dto.jwt.OAuth2LoginUriResponseDto;
import io.pinkspider.leveluptogethermvp.userservice.oauth.domain.dto.jwt.SocialLoginResponseDto;
import io.pinkspider.leveluptogethermvp.userservice.oauth.domain.dto.kakao.KakaoUserInfo;
import io.pinkspider.leveluptogethermvp.userservice.oauth.domain.dto.request.CompleteSignupRequestDto;
import io.pinkspider.leveluptogethermvp.userservice.terms.application.UserTermsService;
import io.pinkspider.leveluptogethermvp.userservice.terms.domain.request.AgreementTermsByUserRequestDto;
import io.pinkspider.leveluptogethermvp.userservice.terms.domain.request.AgreementTermsByUserRequestDto.AgreementTerms;
import io.pinkspider.global.enums.NotificationType;
import io.pinkspider.global.event.UserSignedUpEvent;
import io.pinkspider.leveluptogethermvp.userservice.geoip.GeoIpService;
import io.pinkspider.leveluptogethermvp.notificationservice.application.NotificationService;
import io.pinkspider.leveluptogethermvp.userservice.geoip.GeoIpService.GeoIpResult;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.entity.Users;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.enums.UserStatus;
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
import org.springframework.context.ApplicationEventPublisher;
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
    private final GeoIpService geoIpService;
    private final NotificationService notificationService;
    private final SignupTokenService signupTokenService;
    private final UserTermsService userTermsService;

    @org.springframework.beans.factory.annotation.Value("${app.jwt.access-token-expiry:86400000}")
    private long accessTokenExpiryMs;
    private final ApplicationEventPublisher eventPublisher;

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
     * 모바일 앱용 소셜 로그인 (QA-108)
     *
     * <p>기존 사용자: 정상 JWT 발급
     * <p>신규 사용자: signup token만 발급 (DB INSERT 안 함). 닉네임/약관 입력 후 complete-signup 호출 필요.
     */
    public SocialLoginResponseDto createJwtFromMobileToken(HttpServletRequest httpRequest,
                                                            String provider,
                                                            String providerToken,
                                                            String deviceType,
                                                            String deviceId,
                                                            String preferredLocale,
                                                            String preferredTimezone) {
        try {
            OAuth2UserInfo userInfo = getUserInfoFromOAuth2Provider(provider, providerToken);
            Optional<Users> existingUserOpt = findExistingUser(userInfo, preferredLocale, preferredTimezone);

            if (existingUserOpt.isEmpty()) {
                // 신규 사용자: signup token만 발급 (DB INSERT 보류)
                String token = prepareSignupSession(userInfo, preferredLocale, preferredTimezone);
                String suggested = resolveSuggestedNickname(userInfo);
                log.info("Mobile login - 신규 사용자 signup session 발급: provider={}", provider);
                return SocialLoginResponseDto.newUser(token, suggested);
            }

            Users users = existingUserOpt.get();
            updateLoginInfo(httpRequest, users);

            CreateJwtResponseDto jwt = issueJwt(httpRequest, users,
                deviceType == null ? "mobile" : deviceType, deviceId);
            log.info("Mobile login - 기존 사용자: userId={}, provider={}", users.getId(), provider);
            return SocialLoginResponseDto.existingUser(jwt);
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            log.error("Mobile login failed - provider: {}, error: {}", provider, e.getMessage());
            throw new CustomException(ApiStatus.INVALID_ACCESS.getResultCode(), "소셜 로그인 실패: " + e.getMessage());
        }
    }

    // Kakao, Google, apple User 정보 받아서 처리 (QA-108: 기존 사용자만 JWT 발급, 신규는 signup token)
    public SocialLoginResponseDto createJwt(HttpServletRequest httpRequest,
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
        Optional<Users> existingUserOpt = findExistingUser(userInfo, null, null);

        if (existingUserOpt.isEmpty()) {
            // 신규 사용자: signup token만 발급
            String token = prepareSignupSession(userInfo, null, null);
            String suggested = resolveSuggestedNickname(userInfo);
            log.info("Web callback - 신규 사용자 signup session 발급: provider={}", provider);
            return SocialLoginResponseDto.newUser(token, suggested);
        }

        Users users = existingUserOpt.get();
        updateLoginInfo(httpRequest, users);

        CreateJwtResponseDto jwt = issueJwt(httpRequest, users,
            deviceType == null ? "web" : deviceType, deviceId);
        log.info("Web callback - 기존 사용자: userId={}", users.getId());
        return SocialLoginResponseDto.existingUser(jwt);
    }

    /**
     * 기존 사용자만 조회. WITHDRAWN 상태이면 예외, locale/timezone 변경이 있으면 업데이트.
     * 신규 사용자는 INSERT하지 않고 빈 Optional 반환 (QA-108).
     */
    @Transactional
    protected Optional<Users> findExistingUser(OAuth2UserInfo userInfo, String preferredLocale, String preferredTimezone) {
        String encryptedEmail = CryptoUtils.encryptAes(userInfo.getEmail());
        Optional<Users> existingUser = userRepository.findByEncryptedEmailAndProvider(
            encryptedEmail,
            userInfo.getProvider()
        );

        if (existingUser.isEmpty()) {
            return Optional.empty();
        }

        Users user = existingUser.get();
        if (user.getStatus() == UserStatus.WITHDRAWN) {
            log.warn("탈퇴한 사용자 로그인 시도: userId={}, provider={}", user.getId(), userInfo.getProvider());
            throw new CustomException("030001", "탈퇴한 계정입니다. 새로 가입해 주세요.");
        }

        boolean needsSave = false;
        if (preferredLocale != null
            && io.pinkspider.global.translation.enums.SupportedLocale.isSupported(preferredLocale)
            && "en".equals(user.getPreferredLocale())
            && !preferredLocale.equals("en")) {
            user.updatePreferredLocale(preferredLocale);
            needsSave = true;
            log.info("기존 사용자 locale 업데이트: userId={}, locale={}", user.getId(), preferredLocale);
        }
        if (preferredTimezone != null
            && io.pinkspider.global.translation.enums.SupportedTimezone.isValid(preferredTimezone)
            && "Asia/Seoul".equals(user.getPreferredTimezone())
            && !preferredTimezone.equals(user.getPreferredTimezone())) {
            user.updatePreferredTimezone(preferredTimezone);
            needsSave = true;
            log.info("기존 사용자 timezone 업데이트: userId={}, timezone={}", user.getId(), preferredTimezone);
        }
        if (needsSave) {
            userRepository.save(user);
        }
        log.info("기존 사용자 로그인: userId={}, provider={}", user.getId(), userInfo.getProvider());
        return existingUser;
    }

    /**
     * 신규 사용자용 signup session을 Redis에 임시 저장하고 token 반환 (QA-108).
     * 같은 (provider, email)로 이미 진행 중이면 이전 token을 무효화하고 새 token 발급.
     */
    private String prepareSignupSession(OAuth2UserInfo userInfo, String preferredLocale, String preferredTimezone) {
        String locale = (preferredLocale != null && io.pinkspider.global.translation.enums.SupportedLocale.isSupported(preferredLocale))
            ? preferredLocale : io.pinkspider.global.translation.enums.SupportedLocale.DEFAULT.getCode();
        String timezone = io.pinkspider.global.translation.enums.SupportedTimezone.resolve(
            preferredTimezone, null, locale);

        SignupSessionData session = new SignupSessionData(
            null,
            userInfo.getProvider().toLowerCase(),
            userInfo.getEmail(),
            resolveSuggestedNickname(userInfo),
            locale,
            timezone
        );
        return signupTokenService.createOrRefresh(session);
    }

    /**
     * OAuth provider에서 받은 닉네임을 그대로 사용하되 중복이면 유니크 변형. (제안용으로만 사용 — 사용자 미입력 시 INSERT되지 않음)
     */
    private String resolveSuggestedNickname(OAuth2UserInfo userInfo) {
        String nickname = userInfo.getNickname();
        if (nickname == null || nickname.isBlank()) {
            return null;
        }
        if (userRepository.existsByNickname(nickname)) {
            return generateUniqueNickname(nickname);
        }
        return nickname;
    }

    /**
     * 회원가입 최종 완료 처리 (QA-108).
     * <p>signup token 검증 → users INSERT → user_terms INSERT → 이벤트 발행 → JWT 발급.
     */
    @Transactional
    public CreateJwtResponseDto completeSignup(String signupToken,
                                                CompleteSignupRequestDto request,
                                                HttpServletRequest httpRequest) {
        SignupSessionData session = signupTokenService.findByToken(signupToken);

        // 닉네임 중복 체크 (signup 진행 중 다른 사용자가 같은 닉네임을 선점할 수 있음)
        if (userRepository.existsByNickname(request.getNickname())) {
            throw new CustomException("NICKNAME_001", "error.nickname.already_in_use");
        }

        // 이메일 중복 체크 (같은 (provider, email)이 동시 진행 중 INSERT됐을 가능성 방어)
        String encryptedEmail = CryptoUtils.encryptAes(session.email());
        if (userRepository.findByEncryptedEmailAndProvider(encryptedEmail, session.provider()).isPresent()) {
            throw new CustomException(ApiStatus.INVALID_ACCESS.getResultCode(), "error.signup.already_completed");
        }

        Users newUsers = Users.builder()
            .email(session.email())
            .nickname(request.getNickname())
            .provider(session.provider())
            .nicknameSet(true)
            .preferredLocale(session.preferredLocale())
            .preferredTimezone(session.preferredTimezone())
            .build();

        Users savedUser = userRepository.save(newUsers);
        log.info("신규 사용자 가입 완료: userId={}, provider={}", savedUser.getId(), session.provider());

        // 약관 동의 저장
        if (request.getAgreedTerms() != null && !request.getAgreedTerms().isEmpty()) {
            AgreementTermsByUserRequestDto termsDto = AgreementTermsByUserRequestDto.builder()
                .AgreementTermsList(request.getAgreedTerms().stream()
                    .map(t -> AgreementTerms.builder()
                        .termVersionId(t.getTermVersionId())
                        .isAgreed(t.isAgreed())
                        .build())
                    .toList())
                .build();
            userTermsService.agreementTermsByUser(savedUser.getId(), termsDto);
        }

        // 회원가입 이벤트 발행 → 기본 칭호 부여 등 후속 처리 (UserSignedUpEventListener, @Async)
        eventPublisher.publishEvent(new UserSignedUpEvent(savedUser.getId()));

        // 환영 알림 발송
        try {
            notificationService.sendNotification(savedUser.getId(),
                NotificationType.WELCOME, null, null, savedUser.getNickname());
        } catch (Exception e) {
            log.error("환영 알림 발송 실패: userId={}, error={}", savedUser.getId(), e.getMessage(), e);
        }

        // 로그인 정보 업데이트 (IP, 국가)
        updateLoginInfo(httpRequest, savedUser);

        // signup token 삭제
        signupTokenService.delete(session);

        // JWT 발급
        return issueJwt(httpRequest, savedUser,
            request.getDeviceType() == null ? "mobile" : request.getDeviceType(),
            request.getDeviceId());
    }

    /**
     * 사용자 정보로 JWT를 발급하고 Redis에 저장 (공통 로직).
     */
    private CreateJwtResponseDto issueJwt(HttpServletRequest httpRequest, Users users,
                                          String deviceType, String deviceId) {
        if (deviceId == null || deviceId.trim().isEmpty()) {
            deviceId = deviceIdentifier.generateDeviceId(httpRequest, deviceType);
        }

        String userId = users.getId();
        String userEmail = users.getEmail();

        String accessToken = jwtUtil.generateAccessToken(userId, userEmail, deviceId);
        String refreshToken = jwtUtil.generateRefreshToken(userId, userEmail, deviceId);

        tokenService.saveTokensToRedis(userId, deviceType, deviceId, accessToken, refreshToken);

        return CreateJwtResponseDto.builder()
            .accessToken(accessToken)
            .refreshToken(refreshToken)
            .tokenType("Bearer")
            .expiresIn((int) (accessTokenExpiryMs / 1000))
            .userId(userId)
            .deviceId(deviceId)
            .nicknameSet(users.isNicknameSet())
            .build();
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
                JWTClaimsSet claims = jwtUtil.decodeIdToken(providerToken);
                yield new AppleUserInfo(claims);
            }
            default -> throw new IllegalArgumentException("Unsupported provider: " + provider);
        };
    }

    /**
     * 로그인 시 IP와 국가 정보를 업데이트합니다.
     */
    @Transactional
    protected void updateLoginInfo(HttpServletRequest request, Users users) {
        try {
            String clientIp = geoIpService.extractClientIp(request);
            GeoIpResult geoResult = geoIpService.lookupCountry(clientIp);

            users.updateLastLoginInfo(
                clientIp,
                geoResult.country(),
                geoResult.countryCode()
            );

            // 타임존이 기본값이면 GeoIP 국가코드로 추론하여 업데이트
            if ("Asia/Seoul".equals(users.getPreferredTimezone()) && geoResult.countryCode() != null) {
                String inferred = io.pinkspider.global.translation.enums.SupportedTimezone.fromCountryCode(
                    geoResult.countryCode());
                if (!inferred.equals(users.getPreferredTimezone())) {
                    users.updatePreferredTimezone(inferred);
                    log.info("GeoIP 기반 타임존 추론: userId={}, country={}, timezone={}",
                        users.getId(), geoResult.countryCode(), inferred);
                }
            }

            userRepository.save(users);

            log.info("로그인 정보 업데이트 - userId: {}, IP: {}, country: {}",
                users.getId(), clientIp, geoResult.country());
        } catch (Exception e) {
            log.warn("로그인 정보 업데이트 실패 - userId: {}, error: {}", users.getId(), e.getMessage());
            // 로그인 정보 업데이트 실패가 로그인 자체를 실패시키지 않도록 함
        }
    }
}
