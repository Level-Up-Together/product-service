package io.pinkspider.leveluptogethermvp.gamificationservice.subscription.api;

import io.pinkspider.global.api.ApiResult;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.application.SubscriptionLedgerReconcileService;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.application.SubscriptionPaymentHistoryAdminService;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.dto.SubscriptionLedgerReconcileResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.dto.SubscriptionPaymentHistoryPageResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.enums.SubscriptionPaymentEventType;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.enums.SubscriptionPlan;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * LUT-486/488: Admin 내부 API 컨트롤러 - 구독 결제 이력. 유저 상세 '결제 이력' 탭(user_id 지정)과 결제이력 통합 페이지(필터 목록)가 공유한다.
 * 인증 불필요 (SecurityConfig에서 /api/internal/** permitAll + InternalApiKeyFilter)
 */
@RestController
@RequestMapping("/api/internal/subscription-payments")
@RequiredArgsConstructor
public class SubscriptionPaymentHistoryAdminInternalController {

    private final SubscriptionPaymentHistoryAdminService subscriptionPaymentHistoryAdminService;
    private final SubscriptionLedgerReconcileService subscriptionLedgerReconcileService;

    /**
     * LUT-617: Android 구독 원장을 Play 주문 연번과 전부 대조해 누락 갱신을 백필하고 어긋난 행을 정정한다. 구독 권한 상태는 바꾸지 않는다.
     * Android 가 아니면 120804, 구독 행이 없으면 120803.
     */
    @PostMapping("/reconcile")
    public ApiResult<SubscriptionLedgerReconcileResponse> reconcile(
            @RequestParam(name = "user_id") String userId) {
        return ApiResult.<SubscriptionLedgerReconcileResponse>builder()
                .value(subscriptionLedgerReconcileService.reconcileGoogle(userId))
                .build();
    }

    @GetMapping
    public ApiResult<SubscriptionPaymentHistoryPageResponse> getPaymentHistory(
            @RequestParam(name = "start_at", required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    LocalDateTime startAt,
            @RequestParam(name = "end_at", required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    LocalDateTime endAt,
            @RequestParam(required = false) String nickname,
            @RequestParam(name = "user_id", required = false) String userId,
            @RequestParam(required = false) String platform,
            @RequestParam(required = false) SubscriptionPlan plan,
            @RequestParam(name = "event_type", required = false)
                    SubscriptionPaymentEventType eventType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResult.<SubscriptionPaymentHistoryPageResponse>builder()
                .value(
                        subscriptionPaymentHistoryAdminService.getPaymentHistory(
                                startAt, endAt, nickname, userId, platform, plan, eventType, page,
                                size))
                .build();
    }
}
