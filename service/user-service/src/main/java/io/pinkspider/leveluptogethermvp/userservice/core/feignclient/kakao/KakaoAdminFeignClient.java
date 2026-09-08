package io.pinkspider.leveluptogethermvp.userservice.core.feignclient.kakao;

import java.util.Map;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 카카오 어드민 키 API (LUT-476).
 *
 * <p>탈퇴 시 연결 해제(unlink)에 사용 — Authorization 은 {@code KakaoAK {adminKey}} 형식.
 */
@FeignClient(name = "kakao-admin-api", url = "https://kapi.kakao.com")
public interface KakaoAdminFeignClient {

    @PostMapping(value = "/v1/user/unlink",
        consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    Map<String, Object> unlink(
        @RequestHeader("Authorization") String authorization,
        @RequestParam("target_id_type") String targetIdType,
        @RequestParam("target_id") Long targetId);
}
