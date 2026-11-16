package io.pinkspider.leveluptogethermvp.bffservice.api;

import io.pinkspider.global.annotation.CurrentUser;
import io.pinkspider.global.api.ApiResult;
import io.pinkspider.global.translation.enums.SupportedLocale;
import io.pinkspider.leveluptogethermvp.bffservice.api.dto.WidgetSummaryResponse;
import io.pinkspider.leveluptogethermvp.bffservice.application.BffWidgetService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** LUT-775: 앱 홈 화면 위젯(iOS·안드로이드, LUT-777)이 부르는 API. 비로그인 위젯은 서버를 부르지 않는다. */
@RestController
@RequestMapping("/api/v1/widget")
@RequiredArgsConstructor
public class BffWidgetController {

    private final BffWidgetService bffWidgetService;

    /**
     * 위젯 요약 — 연속 실천 일수, 오늘 완료 여부, 활성 문장 전체(Accept-Language: ko·en·ja·ar, 없거나 모르는 언어면 en).
     *
     * @param userId 인증된 사용자 ID
     * @param acceptLanguage Accept-Language 헤더
     */
    @GetMapping("/summary")
    public ResponseEntity<ApiResult<WidgetSummaryResponse>> getSummary(
            @CurrentUser String userId,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false)
                    String acceptLanguage) {
        WidgetSummaryResponse response =
                bffWidgetService.getSummary(
                        userId, SupportedLocale.extractLanguageCode(acceptLanguage));
        return ResponseEntity.ok(
                ApiResult.<WidgetSummaryResponse>builder().value(response).build());
    }
}
