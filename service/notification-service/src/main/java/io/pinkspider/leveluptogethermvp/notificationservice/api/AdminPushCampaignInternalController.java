package io.pinkspider.leveluptogethermvp.notificationservice.api;

import io.pinkspider.global.api.ApiResult;
import io.pinkspider.leveluptogethermvp.notificationservice.application.AdminPushCampaignService;
import io.pinkspider.leveluptogethermvp.notificationservice.domain.dto.AdminPushCampaignPageResponse;
import io.pinkspider.leveluptogethermvp.notificationservice.domain.dto.AdminPushCampaignRequest;
import io.pinkspider.leveluptogethermvp.notificationservice.domain.dto.AdminPushCampaignResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin 내부 API — 관리자 푸시 알림 관리 (LUT-508). 인증 불필요 (SecurityConfig /api/internal/** permitAll —
 * InternalApiKeyFilter 가 방어). admin-service 가 패스스루한다.
 */
@RestController
@RequestMapping("/api/internal/push-campaigns")
@RequiredArgsConstructor
public class AdminPushCampaignInternalController {

    private final AdminPushCampaignService adminPushCampaignService;

    /** 발송 요청 — 이력 행을 만들고 대상 수를 응답한 뒤 비동기로 발송한다 */
    @PostMapping
    public ApiResult<AdminPushCampaignResponse> create(
            @Valid @RequestBody AdminPushCampaignRequest request,
            @RequestHeader("X-Admin-Id") Long adminId) {
        return ApiResult.<AdminPushCampaignResponse>builder()
                .value(adminPushCampaignService.create(request, adminId))
                .build();
    }

    @GetMapping
    public ApiResult<AdminPushCampaignPageResponse> getCampaigns(
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size) {
        return ApiResult.<AdminPushCampaignPageResponse>builder()
                .value(adminPushCampaignService.getCampaigns(page, size))
                .build();
    }

    @GetMapping("/{campaignId}")
    public ApiResult<AdminPushCampaignResponse> getCampaign(@PathVariable Long campaignId) {
        return ApiResult.<AdminPushCampaignResponse>builder()
                .value(adminPushCampaignService.getCampaign(campaignId))
                .build();
    }
}
