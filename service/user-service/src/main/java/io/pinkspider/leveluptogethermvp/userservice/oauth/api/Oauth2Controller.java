package io.pinkspider.leveluptogethermvp.userservice.oauth.api;

import static org.springframework.web.bind.annotation.RequestMethod.GET;
import static org.springframework.web.bind.annotation.RequestMethod.POST;

import io.pinkspider.global.api.ApiResult;
import io.pinkspider.leveluptogethermvp.userservice.oauth.application.Oauth2Service;
import io.pinkspider.leveluptogethermvp.userservice.oauth.application.SignupTokenService;
import io.pinkspider.leveluptogethermvp.userservice.oauth.domain.dto.jwt.CreateJwtResponseDto;
import io.pinkspider.leveluptogethermvp.userservice.oauth.domain.dto.jwt.OAuth2LoginUriResponseDto;
import io.pinkspider.leveluptogethermvp.userservice.oauth.domain.dto.jwt.SocialLoginResponseDto;
import io.pinkspider.leveluptogethermvp.userservice.oauth.domain.dto.request.CompleteSignupRequestDto;
import io.pinkspider.leveluptogethermvp.userservice.oauth.domain.dto.request.MobileLoginRequestDto;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/oauth")
@RequiredArgsConstructor
@Slf4j
public class Oauth2Controller {

    private final Oauth2Service oauth2Service;
    private final SignupTokenService signupTokenService;

    // 주소는 프론트에서 생성해서 redirect 하는걸로 바꿨지만, 테스트를 위해 남겨둠
    @GetMapping("/uri/{provider}")
    public ApiResult<OAuth2LoginUriResponseDto> getOauth2LoginUri(
        @PathVariable("provider") String provider,
        HttpServletRequest request) {
        if ("apple".equals(provider)) {
            return ApiResult.<OAuth2LoginUriResponseDto>builder()
                .value(oauth2Service.getAppleOauthUri(provider, request))
                .build();
        } else {
            return ApiResult.<OAuth2LoginUriResponseDto>builder()
                .value(oauth2Service.getOauth2LoginUri(provider, request))
                .build();
        }
    }

    @RequestMapping(value = "/callback/{provider}", method = {GET, POST})
    public ApiResult<SocialLoginResponseDto> createJwt(@PathVariable("provider") String provider,
                                                       @RequestParam(name = "code", required = false) String code,
                                                       @RequestParam(name = "id_token", required = false) String idToken,
                                                       @RequestParam(name = "device_type", required = false) String deviceType,
                                                       @RequestParam(name = "device_id", required = false) String deviceId,
                                                       HttpServletRequest httpRequest
    ) throws Exception {

        log.info("provider={}", provider);
        log.info("code={}", code);
        log.info("id_token={}", idToken);

        SocialLoginResponseDto response = oauth2Service.createJwt(httpRequest, provider, code, deviceType, deviceId, idToken);

        return ApiResult.<SocialLoginResponseDto>builder()
            .value(response)
            .build();
    }

    /**
     * 모바일 앱용 소셜 로그인 API (QA-108)
     * 네이티브 SDK에서 받은 access_token/id_token으로 처리. 기존 사용자는 JWT, 신규 사용자는 signup token 발급.
     */
    @PostMapping("/mobile/login")
    public ApiResult<SocialLoginResponseDto> mobileLogin(
        @Valid @RequestBody MobileLoginRequestDto request,
        HttpServletRequest httpRequest
    ) {
        log.info("Mobile login request - provider: {}, deviceType: {}",
            request.getProvider(), request.getDeviceType());

        SocialLoginResponseDto response = oauth2Service.createJwtFromMobileToken(
            httpRequest,
            request.getProvider(),
            request.getAccessToken(),
            request.getDeviceType(),
            request.getDeviceId(),
            request.getPreferredLocale(),
            request.getPreferredTimezone()
        );

        return ApiResult.<SocialLoginResponseDto>builder()
            .value(response)
            .build();
    }

    /**
     * 회원가입 최종 완료 (QA-108)
     * signup token + 닉네임 + 약관 동의 정보를 받아 users INSERT, JWT 발급.
     */
    @PostMapping("/complete-signup")
    public ApiResult<CreateJwtResponseDto> completeSignup(
        @RequestHeader("X-Signup-Token") String signupToken,
        @Valid @RequestBody CompleteSignupRequestDto request,
        HttpServletRequest httpRequest
    ) {
        log.info("Complete signup request - token: {}***, nickname: {}",
            signupToken.length() > 8 ? signupToken.substring(0, 8) : signupToken, request.getNickname());

        CreateJwtResponseDto response = oauth2Service.completeSignup(signupToken, request, httpRequest);

        return ApiResult.<CreateJwtResponseDto>builder()
            .value(response)
            .build();
    }

    /**
     * signup token 유효성 확인 (QA-108)
     * 프론트가 새로고침/재진입 시 토큰이 살아있는지 확인.
     */
    @GetMapping("/signup-session/check")
    public ApiResult<Map<String, Boolean>> checkSignupSession(
        @RequestHeader("X-Signup-Token") String signupToken
    ) {
        boolean valid = signupTokenService.isValid(signupToken);
        return ApiResult.<Map<String, Boolean>>builder()
            .value(Map.of("valid", valid))
            .build();
    }
}
