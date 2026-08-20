package io.pinkspider.leveluptogethermvp.gamificationservice.diamond.api;

import io.pinkspider.global.api.ApiResult;
import io.pinkspider.leveluptogethermvp.gamificationservice.diamond.application.DiamondPaymentHistoryAdminService;
import io.pinkspider.leveluptogethermvp.gamificationservice.diamond.domain.dto.DiamondPaymentHistoryPageResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.diamond.domain.enums.DiamondPurchaseStatus;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * LUT-401: Admin 내부 API 컨트롤러 - 다이아 결제이력.
 * 인증 불필요 (SecurityConfig에서 /api/internal/** permitAll + InternalApiKeyFilter)
 */
@RestController
@RequestMapping("/api/internal/diamond-payments")
@RequiredArgsConstructor
public class DiamondPaymentHistoryAdminInternalController {

    private final DiamondPaymentHistoryAdminService diamondPaymentHistoryAdminService;

    @GetMapping
    public ApiResult<DiamondPaymentHistoryPageResponse> getPaymentHistory(
            @RequestParam(name = "start_at", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startAt,
            @RequestParam(name = "end_at", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endAt,
            @RequestParam(required = false) String nickname,
            @RequestParam(required = false) String platform,
            @RequestParam(name = "bundle_id", required = false) Long bundleId,
            @RequestParam(required = false) DiamondPurchaseStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResult.<DiamondPaymentHistoryPageResponse>builder()
            .value(diamondPaymentHistoryAdminService.getPaymentHistory(
                startAt, endAt, nickname, platform, bundleId, status, page, size))
            .build();
    }
}
