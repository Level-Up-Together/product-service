package io.pinkspider.leveluptogethermvp.statsservice.firstday.api;

import io.pinkspider.global.api.ApiResult;
import io.pinkspider.leveluptogethermvp.statsservice.firstday.application.FirstDayRetentionQueryService;
import io.pinkspider.leveluptogethermvp.statsservice.firstday.application.FirstDayRetentionService;
import io.pinkspider.leveluptogethermvp.statsservice.firstday.domain.dto.FirstDayRetentionResponse;
import io.pinkspider.leveluptogethermvp.statsservice.firstday.domain.dto.FirstDayRetentionRunResponse;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.KpiDates;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * LUT-779: 첫날 행동별 리텐션 내부 API (admin-service Feign — 어드민 리텐션 화면 LUT-780, {@code
 * X-Internal-Api-Key}).
 *
 * <ul>
 *   <li>{@code GET
 *       /api/internal/kpi/first-day-retention?range=4w|8w|12w&platform=ALL|IOS|ANDROID|WEB}
 *   <li>{@code POST /api/internal/kpi/first-day-retention/rebuild} — 표를 비우고 접속 기록 시작일부터 다시 만든다(정의
 *       변경 때)
 * </ul>
 */
@RestController
@RequestMapping("/api/internal/kpi/first-day-retention")
@RequiredArgsConstructor
public class FirstDayRetentionInternalController {

    private final FirstDayRetentionQueryService queryService;
    private final FirstDayRetentionService retentionService;

    @GetMapping
    public ResponseEntity<ApiResult<FirstDayRetentionResponse>> summary(
            @RequestParam(value = "range", defaultValue = "4w") String range,
            @RequestParam(value = "platform", defaultValue = "ALL") String platform) {
        return ok(queryService.summary(range, platform, KpiDates.today()));
    }

    @PostMapping("/rebuild")
    public ResponseEntity<ApiResult<FirstDayRetentionRunResponse>> rebuild() {
        return ok(retentionService.rebuildAll(KpiDates.today(), LocalDateTime.now(ZoneOffset.UTC)));
    }

    private static <T> ResponseEntity<ApiResult<T>> ok(T value) {
        return ResponseEntity.ok(ApiResult.<T>builder().value(value).build());
    }
}
