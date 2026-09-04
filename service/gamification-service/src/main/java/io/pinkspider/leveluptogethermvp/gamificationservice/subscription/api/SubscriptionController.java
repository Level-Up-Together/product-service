package io.pinkspider.leveluptogethermvp.gamificationservice.subscription.api;

import io.pinkspider.global.annotation.CurrentUser;
import io.pinkspider.global.api.ApiResult;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.application.SubscriptionGrantService;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.application.SubscriptionService;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.dto.SubscriptionEntitlementResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.dto.SubscriptionVerifyRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** LUT-450: 구독 권한(entitlement) 조회 API — 프론트 구독 상태의 단일 출처. */
@RestController
@RequestMapping("/api/v1/subscriptions")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;
    private final SubscriptionGrantService subscriptionGrantService;

    /** 내 구독 상태 조회 — 상태/플랜/만료·유예 시각/자동갱신/무료 체험 사용 여부 */
    @GetMapping("/me")
    public ResponseEntity<ApiResult<SubscriptionEntitlementResponse>> getMySubscription(
            @CurrentUser String userId) {
        return ResponseEntity.ok(
                ApiResult.<SubscriptionEntitlementResponse>builder()
                        .value(subscriptionService.getMyEntitlement(userId))
                        .build());
    }

    /** LUT-451: 구독 영수증 검증 + 권한 부여 — 최초 구매와 복원(Restore) 공용. 멱등. */
    @PostMapping("/verify")
    public ResponseEntity<ApiResult<SubscriptionEntitlementResponse>> verifySubscription(
            @CurrentUser String userId, @Valid @RequestBody SubscriptionVerifyRequest request) {
        return ResponseEntity.ok(
                ApiResult.<SubscriptionEntitlementResponse>builder()
                        .value(subscriptionGrantService.verifyAndGrant(userId, request))
                        .build());
    }
}
