package io.pinkspider.leveluptogethermvp.gamificationservice.subscription.api;

import io.pinkspider.global.api.ApiResult;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.application.SubscriptionPaymentHistoryAdminService;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.dto.SubscriptionPaymentHistoryPageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * LUT-486: Admin 내부 API 컨트롤러 - 구독 결제 이력 (어드민 유저 상세 '결제 이력' 탭).
 * 인증 불필요 (SecurityConfig에서 /api/internal/** permitAll + InternalApiKeyFilter)
 */
@RestController
@RequestMapping("/api/internal/subscription-payments")
@RequiredArgsConstructor
public class SubscriptionPaymentHistoryAdminInternalController {

    private final SubscriptionPaymentHistoryAdminService subscriptionPaymentHistoryAdminService;

    @GetMapping
    public ApiResult<SubscriptionPaymentHistoryPageResponse> getPaymentHistory(
            @RequestParam(name = "user_id") String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResult.<SubscriptionPaymentHistoryPageResponse>builder()
                .value(subscriptionPaymentHistoryAdminService.getPaymentHistory(userId, page, size))
                .build();
    }
}
