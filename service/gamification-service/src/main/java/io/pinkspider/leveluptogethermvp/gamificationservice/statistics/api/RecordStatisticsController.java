package io.pinkspider.leveluptogethermvp.gamificationservice.statistics.api;

import io.pinkspider.global.annotation.CurrentUser;
import io.pinkspider.global.api.ApiResult;
import io.pinkspider.leveluptogethermvp.gamificationservice.statistics.application.RecordStatisticsService;
import io.pinkspider.leveluptogethermvp.gamificationservice.statistics.domain.dto.RecordSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * LUT-454: 기록 통계 — 전체 기간 요약 (누적 달성·최장 스트릭·등급 도달 이력).
 *
 * <p>무료/구독 게이팅은 프론트가 담당한다 (요약은 범위 제한 개념이 없어 서버 제한 없음 —
 * 월간 리포트의 과거 월 조회만 서버에서 구독을 요구한다).
 */
@RestController
@RequestMapping("/api/v1/statistics")
@RequiredArgsConstructor
public class RecordStatisticsController {

    private final RecordStatisticsService recordStatisticsService;

    /** 내 전체 기간 기록 요약 */
    @GetMapping("/summary")
    public ResponseEntity<ApiResult<RecordSummaryResponse>> getRecordSummary(
            @CurrentUser String userId) {
        return ResponseEntity.ok(
                ApiResult.<RecordSummaryResponse>builder()
                        .value(recordStatisticsService.getRecordSummary(userId))
                        .build());
    }
}
