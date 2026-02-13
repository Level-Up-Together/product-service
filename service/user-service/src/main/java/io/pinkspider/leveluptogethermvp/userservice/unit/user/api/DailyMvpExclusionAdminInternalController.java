package io.pinkspider.leveluptogethermvp.userservice.unit.user.api;

import io.pinkspider.global.api.ApiResult;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.application.DailyMvpExclusionAdminInternalService;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.dto.admin.DailyMvpExclusionAdminRequest;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.dto.admin.DailyMvpExclusionAdminResponse;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/internal/daily-mvp-exclusions")
@RequiredArgsConstructor
public class DailyMvpExclusionAdminInternalController {

    private final DailyMvpExclusionAdminInternalService dailyMvpExclusionAdminInternalService;

    @GetMapping("/{date}")
    public ApiResult<List<DailyMvpExclusionAdminResponse>> getExclusionsByDate(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ApiResult.<List<DailyMvpExclusionAdminResponse>>builder()
            .value(dailyMvpExclusionAdminInternalService.getExclusionsByDate(date))
            .build();
    }

    @PostMapping
    public ApiResult<DailyMvpExclusionAdminResponse> addExclusion(@RequestBody DailyMvpExclusionAdminRequest request) {
        return ApiResult.<DailyMvpExclusionAdminResponse>builder()
            .value(dailyMvpExclusionAdminInternalService.addExclusion(request))
            .build();
    }

    @DeleteMapping("/{date}/{userId}")
    public ApiResult<Void> removeExclusion(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @PathVariable String userId) {
        dailyMvpExclusionAdminInternalService.removeExclusion(date, userId);
        return ApiResult.<Void>builder().build();
    }
}
