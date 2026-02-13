package io.pinkspider.leveluptogethermvp.gamificationservice.mvphistory.api;

import io.pinkspider.global.api.ApiResult;
import io.pinkspider.leveluptogethermvp.gamificationservice.mvphistory.application.MvpHistoryAdminInternalService;
import io.pinkspider.leveluptogethermvp.gamificationservice.mvphistory.domain.dto.MvpHistoryAdminPageResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.mvphistory.domain.dto.MvpHistoryAdminResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.mvphistory.domain.dto.MvpStatsAdminResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.mvphistory.domain.dto.UserCategoryActivityAdminResponse;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin 내부 API 컨트롤러 - MVP History
 * 인증 불필요 (SecurityConfig에서 /api/internal/** permitAll)
 */
@RestController
@RequestMapping("/api/internal/mvp-history")
@RequiredArgsConstructor
public class MvpHistoryAdminInternalController {

    private final MvpHistoryAdminInternalService mvpHistoryAdminService;

    @GetMapping("/date")
    public ApiResult<List<MvpHistoryAdminResponse>> getMvpHistoryByDate(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ApiResult.<List<MvpHistoryAdminResponse>>builder()
            .value(mvpHistoryAdminService.getMvpHistoryByDate(date))
            .build();
    }

    @GetMapping("/period")
    public ApiResult<MvpHistoryAdminPageResponse> getMvpHistoryByPeriod(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size) {
        return ApiResult.<MvpHistoryAdminPageResponse>builder()
            .value(mvpHistoryAdminService.getMvpHistoryByPeriod(startDate, endDate, page, size))
            .build();
    }

    @GetMapping("/user/{userId}")
    public ApiResult<MvpHistoryAdminPageResponse> getMvpHistoryByUser(
            @PathVariable String userId,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size) {
        return ApiResult.<MvpHistoryAdminPageResponse>builder()
            .value(mvpHistoryAdminService.getMvpHistoryByUser(userId, page, size))
            .build();
    }

    @GetMapping("/stats")
    public ApiResult<MvpStatsAdminResponse> getMvpStats(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false, defaultValue = "10") int topUserLimit) {
        return ApiResult.<MvpStatsAdminResponse>builder()
            .value(mvpHistoryAdminService.getMvpStats(startDate, endDate, topUserLimit))
            .build();
    }

    @GetMapping("/user/{userId}/category-activity")
    public ApiResult<List<UserCategoryActivityAdminResponse>> getUserCategoryActivity(
            @PathVariable String userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ApiResult.<List<UserCategoryActivityAdminResponse>>builder()
            .value(mvpHistoryAdminService.getUserCategoryActivity(userId, startDate, endDate))
            .build();
    }
}
