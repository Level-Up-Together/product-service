package io.pinkspider.leveluptogethermvp.userservice.oauth.api;

import static org.springframework.web.bind.annotation.RequestMethod.GET;
import static org.springframework.web.bind.annotation.RequestMethod.POST;

import io.pinkspider.global.api.ApiResult;
import io.pinkspider.leveluptogethermvp.userservice.oauth.application.Oauth2Service;
import io.pinkspider.leveluptogethermvp.userservice.oauth.domain.dto.jwt.CreateJwtResponseDto;
import io.pinkspider.leveluptogethermvp.userservice.oauth.domain.dto.jwt.OAuth2LoginUriResponseDto;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/oauth")
@RequiredArgsConstructor
@Slf4j
public class Oauth2Controller {

    private final Oauth2Service oauth2Service;

    // 주소는 프론트에서 생성해서 redirect 하는걸로 바꿨지만, 테스트를 위해 남겨둠
    @GetMapping("/uri/{provider}")
    public ApiResult<OAuth2LoginUriResponseDto> getOauth2LoginUri(@PathVariable("provider") String provider) {
        if ("apple".equals(provider)) {
            return ApiResult.<OAuth2LoginUriResponseDto>builder()
                .value(oauth2Service.getAppleOauthUri(provider))
                .build();
        } else {
            return ApiResult.<OAuth2LoginUriResponseDto>builder()
                .value(oauth2Service.getOauth2LoginUri(provider))
                .build();
        }
    }

    @RequestMapping(value = "/callback/{provider}", method = {GET, POST})
    public ApiResult<CreateJwtResponseDto> createJwt(@PathVariable("provider") String provider,
                                                     @RequestParam(name = "code", required = false) String code,
                                                     @RequestParam(name = "id_token", required = false) String idToken,
                                                     @RequestParam(name = "device_type", required = false) String deviceType,
                                                     @RequestParam(name = "device_id", required = false) String deviceId,
                                                     HttpServletRequest httpRequest
    ) throws Exception {

        log.info("provider={}", provider);
        log.info("code={}", code);
        log.info("id_token={}", idToken);

        CreateJwtResponseDto createJwtResponseDto = oauth2Service.createJwt(httpRequest, provider, code, deviceType, deviceId, idToken);

        return ApiResult.<CreateJwtResponseDto>builder()
            .value(createJwtResponseDto)
            .build();
    }
}
