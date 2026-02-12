package io.pinkspider.leveluptogethermvp.userservice.core.feignclient.kakao;

import feign.Headers;
import java.util.Map;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "kakao-oauth2-client", url = "${app.oauth2.kakao-token-url}")
public interface KakaoOAuth2FeignClient {

    @PostMapping(consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    @Headers("Content-Type: application/x-www-form-urlencoded")
    Map<String, String> getAccessToken(
        @RequestParam("grant_type") String grantType,
        @RequestParam("client_id") String clientId,
        @RequestParam("client_secret") String clientSecret, // 추가
        @RequestParam("redirect_uri") String redirectUri,
        @RequestParam("code") String code
    );
}



