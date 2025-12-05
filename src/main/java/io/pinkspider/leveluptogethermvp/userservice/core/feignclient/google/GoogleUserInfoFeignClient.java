package io.pinkspider.leveluptogethermvp.userservice.core.feignclient.google;

import io.pinkspider.leveluptogethermvp.userservice.core.config.GoogleFeignConfig;
import java.util.Map;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "google-userinfo-client",
    url = "https://www.googleapis.com/oauth2/v3",
    configuration = GoogleFeignConfig.class)
public interface GoogleUserInfoFeignClient {

    @GetMapping("/userinfo")
    Map<String, Object> getUserInfo(@RequestHeader("Authorization") String accessToken);
}
