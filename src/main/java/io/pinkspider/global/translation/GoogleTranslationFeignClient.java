package io.pinkspider.global.translation;

import io.pinkspider.global.translation.dto.GoogleTranslationRequest;
import io.pinkspider.global.translation.dto.GoogleTranslationResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Google Cloud Translation API v2 Feign Client
 * https://cloud.google.com/translate/docs/reference/rest/v2/translate
 */
@FeignClient(
    name = "google-translation-client",
    url = "${google.translation.api.url:https://translation.googleapis.com}",
    configuration = GoogleTranslationConfig.class
)
public interface GoogleTranslationFeignClient {

    /**
     * 텍스트 번역 요청
     *
     * @param apiKey Google Cloud API Key
     * @param request 번역 요청 (q: 텍스트, target: 대상언어, source: 원본언어(선택))
     * @return 번역 결과
     */
    @PostMapping("/language/translate/v2")
    GoogleTranslationResponse translate(
        @RequestParam("key") String apiKey,
        @RequestBody GoogleTranslationRequest request
    );
}
