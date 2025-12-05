package io.pinkspider.leveluptogethermvp.userservice.core.feignclient.kakao;

import java.util.Map;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "kakao-user-api", url = "https://kapi.kakao.com")
public interface KakaoUserInfoFeignClient {

    @GetMapping(value = "/v2/user/me", consumes = MediaType.APPLICATION_JSON_VALUE)
    Map<String, Object> getUserInfo(@RequestHeader("Authorization") String accessToken);
}

