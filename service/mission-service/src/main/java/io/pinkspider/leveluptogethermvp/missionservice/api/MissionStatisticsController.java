package io.pinkspider.leveluptogethermvp.missionservice.api;

import io.pinkspider.global.annotation.CurrentUser;
import io.pinkspider.global.api.ApiResult;
import io.pinkspider.leveluptogethermvp.missionservice.application.MissionStatisticsService;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MonthlyStatisticsResponse;
import java.time.YearMonth;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * LUT-454: 미션 기록 통계 API — 월간 리포트.
 *
 * <p>전체 기간 요약은 gamificationservice 의 {@code GET /api/v1/statistics/summary} 가 담당.
 * 무료 유저는 최근 30일 범위의 월(실질 당월·전월)만 조회 가능 — 과거 월은 구독 필요(050301).
 */
@RestController
@RequestMapping("/api/v1/missions/statistics")
@RequiredArgsConstructor
public class MissionStatisticsController {

    private final MissionStatisticsService missionStatisticsService;

    /** 월간 기록 리포트 — 달성률/스트릭/카테고리·요일·시간대 분포. 생략 시 당월 */
    @GetMapping("/monthly")
    public ResponseEntity<ApiResult<MonthlyStatisticsResponse>> getMonthlyStatistics(
            @CurrentUser String userId,
            @RequestParam(value = "year_month", required = false)
                    @DateTimeFormat(pattern = "yyyy-MM")
                    YearMonth yearMonth,
            @RequestHeader(value = "X-Timezone", required = false) String timezone) {
        return ResponseEntity.ok(
                ApiResult.<MonthlyStatisticsResponse>builder()
                        .value(
                                missionStatisticsService.getMonthlyStatistics(
                                        userId, yearMonth, timezone))
                        .build());
    }
}
