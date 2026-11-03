package io.pinkspider.leveluptogethermvp.metaservice.appsetting.api;

import io.pinkspider.global.api.ApiResult;
import io.pinkspider.leveluptogethermvp.metaservice.appsetting.application.AppSettingCacheService;
import io.pinkspider.leveluptogethermvp.metaservice.appsetting.domain.dto.StoreReviewMilestonesRequest;
import io.pinkspider.leveluptogethermvp.metaservice.appsetting.domain.dto.StoreReviewMilestonesResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * LUT-708: Admin 내부 API — 서비스 설정(app_setting). 인증 불필요(SecurityConfig 에서 /api/internal/** permitAll,
 * 내부 키 필터). admin-service 는 /api/admin/attendance-reward-configs/store-review-milestones 로
 * 패스스루한다(권한 attendance-config:*).
 */
@RestController
@RequestMapping("/api/internal/app-settings")
@RequiredArgsConstructor
public class AppSettingInternalController {

    private final AppSettingCacheService appSettingCacheService;

    @GetMapping("/store-review-milestones")
    public ApiResult<StoreReviewMilestonesResponse> getStoreReviewMilestones() {
        return ApiResult.<StoreReviewMilestonesResponse>builder()
                .value(
                        new StoreReviewMilestonesResponse(
                                appSettingCacheService.getStoreReviewMilestoneDays()))
                .build();
    }

    @PutMapping("/store-review-milestones")
    public ApiResult<StoreReviewMilestonesResponse> updateStoreReviewMilestones(
            @Valid @RequestBody StoreReviewMilestonesRequest request) {
        return ApiResult.<StoreReviewMilestonesResponse>builder()
                .value(
                        new StoreReviewMilestonesResponse(
                                appSettingCacheService.updateStoreReviewMilestoneDays(
                                        request.milestoneDays())))
                .build();
    }
}
