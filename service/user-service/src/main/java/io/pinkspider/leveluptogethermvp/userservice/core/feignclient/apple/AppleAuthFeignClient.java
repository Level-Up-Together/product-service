package io.pinkspider.leveluptogethermvp.userservice.core.feignclient.apple;

import java.util.Map;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Apple ID 인증 서버 API (LUT-477).
 *
 * <p>authorization code 교환(로그인 시 refresh token 확보)과 탈퇴 시 token revoke 에 사용.
 * client_secret 은 SIWA 키(.p8)로 서명한 ES256 JWT — {@code AppleTokenService} 가 생성한다.
 */
@FeignClient(name = "apple-auth-api", url = "https://appleid.apple.com")
public interface AppleAuthFeignClient {

    @PostMapping(value = "/auth/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    Map<String, Object> exchangeToken(
        @RequestParam("client_id") String clientId,
        @RequestParam("client_secret") String clientSecret,
        @RequestParam("grant_type") String grantType,
        @RequestParam("code") String code,
        @RequestParam(value = "redirect_uri", required = false) String redirectUri);

    @PostMapping(value = "/auth/revoke", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    void revoke(
        @RequestParam("client_id") String clientId,
        @RequestParam("client_secret") String clientSecret,
        @RequestParam("token") String token,
        @RequestParam("token_type_hint") String tokenTypeHint);
}
