package io.pinkspider.leveluptogethermvp.gamificationservice.experience.api;

import io.pinkspider.global.api.ApiResult;
import io.pinkspider.leveluptogethermvp.gamificationservice.experience.application.ExperienceHistoryAdminService;
import io.pinkspider.leveluptogethermvp.gamificationservice.experience.domain.dto.CategoryMissionStatsAdminResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.experience.domain.dto.TopExpGainerAdminResponse;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin 내부 API 컨트롤러 - ExperienceHistory
 * 인증 불필요 (SecurityConfig에서 /api/internal/** permitAll)
 */
@RestController
@RequestMapping("/api/internal/experience-history")
@RequiredArgsConstructor
public class ExperienceHistoryAdminInternalController {

    private final ExperienceHistoryAdminService experienceHistoryAdminService;

    @GetMapping("/top-gainers")
    public ApiResult<List<TopExpGainerAdminResponse>> getTopExpGainers(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(required = false, defaultValue = "10") int limit) {
        return ApiResult.<List<TopExpGainerAdminResponse>>builder()
            .value(experienceHistoryAdminService.getTopExpGainersByPeriod(startDate, endDate, limit))
            .build();
    }

    @GetMapping("/top-gainers-excluding")
    public ApiResult<List<TopExpGainerAdminResponse>> getTopExpGainersExcluding(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam List<String> excludedUserIds,
            @RequestParam(required = false, defaultValue = "10") int limit) {
        return ApiResult.<List<TopExpGainerAdminResponse>>builder()
            .value(experienceHistoryAdminService.getTopExpGainersByPeriodExcluding(
                startDate, endDate, excludedUserIds, limit))
            .build();
    }

    @GetMapping("/category-mission-stats")
    public ApiResult<List<CategoryMissionStatsAdminResponse>> getCategoryMissionStats(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        return ApiResult.<List<CategoryMissionStatsAdminResponse>>builder()
            .value(experienceHistoryAdminService.getCategoryMissionStatsByPeriod(startDate, endDate))
            .build();
    }
}
