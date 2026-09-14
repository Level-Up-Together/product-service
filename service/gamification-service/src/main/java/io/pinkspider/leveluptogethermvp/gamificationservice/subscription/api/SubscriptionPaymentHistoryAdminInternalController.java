package io.pinkspider.leveluptogethermvp.gamificationservice.subscription.api;

import io.pinkspider.global.api.ApiResult;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.application.SubscriptionPaymentHistoryAdminService;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.dto.SubscriptionPaymentHistoryPageResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.enums.SubscriptionPaymentEventType;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.enums.SubscriptionPlan;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * LUT-486/488: Admin 내부 API 컨트롤러 - 구독 결제 이력.
 * 유저 상세 '결제 이력' 탭(user_id 지정)과 결제이력 통합 페이지(필터 목록)가 공유한다.
 * 인증 불필요 (SecurityConfig에서 /api/internal/** permitAll + InternalApiKeyFilter)
 */
@RestController
@RequestMapping("/api/internal/subscription-payments")
@RequiredArgsConstructor
public class SubscriptionPaymentHistoryAdminInternalController {

    private final SubscriptionPaymentHistoryAdminService subscriptionPaymentHistoryAdminService;

    @GetMapping
    public ApiResult<SubscriptionPaymentHistoryPageResponse> getPaymentHistory(
            @RequestParam(name = "start_at", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startAt,
            @RequestParam(name = "end_at", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endAt,
            @RequestParam(required = false) String nickname,
            @RequestParam(name = "user_id", required = false) String userId,
            @RequestParam(required = false) String platform,
            @RequestParam(required = false) SubscriptionPlan plan,
            @RequestParam(name = "event_type", required = false)
                    SubscriptionPaymentEventType eventType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResult.<SubscriptionPaymentHistoryPageResponse>builder()
                .value(subscriptionPaymentHistoryAdminService.getPaymentHistory(
                        startAt, endAt, nickname, userId, platform, plan, eventType, page, size))
                .build();
    }
}
