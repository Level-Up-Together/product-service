package io.pinkspider.leveluptogethermvp.statsservice.activity.api;

import io.pinkspider.global.api.ApiResult;
import io.pinkspider.leveluptogethermvp.statsservice.activity.application.UserActivityFlushService;
import io.pinkspider.leveluptogethermvp.statsservice.activity.domain.dto.UserActivityFlushResponse;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * LUT-598: 활동 적재 내부 API (admin-service · 운영 점검용, {@code X-Internal-Api-Key}).
 *
 * <p>{@code POST /api/internal/stats/activity/flush?date=YYYY-MM-DD} — 그 KST 날짜의 Redis 집합을 지금 적재한다.
 * 스케줄러(00:20)를 기다리지 않고 배포 검증·누락 보정에 쓴다. 멱등.
 */
@RestController
@RequestMapping("/api/internal/stats/activity")
@RequiredArgsConstructor
public class UserActivityInternalController {

    private final UserActivityFlushService flushService;

    @PostMapping("/flush")
    public ResponseEntity<ApiResult<UserActivityFlushResponse>> flush(
            @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        int flushed = flushService.flush(date);
        long active = flushService.countActiveUsers(date);
        return ResponseEntity.ok(
                ApiResult.<UserActivityFlushResponse>builder()
                        .value(new UserActivityFlushResponse(date, flushed, active))
                        .build());
    }
}
