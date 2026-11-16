package io.pinkspider.leveluptogethermvp.statsservice.missionstats.api;

import io.pinkspider.global.api.ApiResult;
import io.pinkspider.leveluptogethermvp.statsservice.missionstats.application.MissionStatsService;
import io.pinkspider.leveluptogethermvp.statsservice.missionstats.application.MissionStatsUserService;
import io.pinkspider.leveluptogethermvp.statsservice.missionstats.domain.dto.MissionStatsSummaryResponse;
import io.pinkspider.leveluptogethermvp.statsservice.missionstats.domain.dto.MissionStatsUsersResponse;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * LUT-790: 미션 통계 내부 API (admin-service Feign — 어드민 미션 통계 화면 LUT-791, {@code X-Internal-Api-Key}).
 *
 * <ul>
 *   <li>{@code GET /api/internal/mission-stats/summary?from&to} — 생성·완료·완료율·일별 추이·분류별 표
 *   <li>{@code GET /api/internal/mission-stats/users?from&to[&page&size&sort&keyword]} — 사용자별 완료 수
 * </ul>
 *
 * <p>날짜는 KST {@code yyyy-MM-dd}(양끝 포함, 최대 400일, from>to 는 바꿔서). 형식·허용값 오류 400 {@code 100101}, 필수 누락
 * {@code 100102}.
 */
@RestController
@RequestMapping("/api/internal/mission-stats")
@RequiredArgsConstructor
public class MissionStatsInternalController {

    private final MissionStatsService summaryService;
    private final MissionStatsUserService userService;

    @GetMapping("/summary")
    public ResponseEntity<ApiResult<MissionStatsSummaryResponse>> summary(
            @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam("to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ok(summaryService.summary(from, to));
    }

    @GetMapping("/users")
    public ResponseEntity<ApiResult<MissionStatsUsersResponse>> users(
            @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam("to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @RequestParam(value = "sort", required = false) String sort,
            @RequestParam(value = "keyword", required = false) String keyword) {
        return ok(userService.users(from, to, page, size, sort, keyword));
    }

    private static <T> ResponseEntity<ApiResult<T>> ok(T value) {
        return ResponseEntity.ok(ApiResult.<T>builder().value(value).build());
    }
}
