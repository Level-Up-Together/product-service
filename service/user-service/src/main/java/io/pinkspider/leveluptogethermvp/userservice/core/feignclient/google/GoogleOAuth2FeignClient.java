package io.pinkspider.leveluptogethermvp.userservice.core.feignclient.google;

import io.pinkspider.leveluptogethermvp.userservice.core.config.GoogleFeignConfig;
import java.util.Map;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "google-oauth2-client",
    url = "${app.oauth2.google-token-url}",
    configuration = GoogleFeignConfig.class)
public interface GoogleOAuth2FeignClient {

    @PostMapping(value = "/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    Map<String, String> getAccessToken(
        @RequestParam("grant_type") String grantType,
        @RequestParam("client_id") String clientId,
        @RequestParam("client_secret") String clientSecret,
        @RequestParam("redirect_uri") String redirectUri,
        @RequestParam("code") String code
    );

    @GetMapping("/oauth2/v3/userinfo")
    Map<String, Object> getUserInfo(@RequestHeader("Authorization") String accessToken);
}
