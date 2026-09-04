package io.pinkspider.leveluptogethermvp.gamificationservice.subscription.api;

import io.pinkspider.global.api.ApiResult;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.application.SubscriptionWebhookService;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.dto.AppleWebhookRequest;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.dto.GooglePubSubPushRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * LUT-452: 스토어 구독 웹훅 수신 엔드포인트 — 스토어 서버가 호출한다 (비로그인 permitAll,
 * SecurityConfig + SecurityConfigPublicEndpointTest 짝 등록).
 *
 * <p>등록처: App Store Connect > 앱 > App Store Server Notifications V2 URL / Google Play Console >
 * 수익 창출 설정 > RTDN(Pub/Sub push 구독 엔드포인트).
 */
@RestController
@RequestMapping("/api/v1/webhooks/subscriptions")
@RequiredArgsConstructor
public class SubscriptionWebhookController {

    private final SubscriptionWebhookService webhookService;

    /** App Store Server Notifications V2 */
    @PostMapping("/apple")
    public ResponseEntity<ApiResult<Void>> appleNotification(
            @RequestBody AppleWebhookRequest request) {
        webhookService.handleAppleNotification(request.signedPayload());
        return ResponseEntity.ok(ApiResult.<Void>builder().build());
    }

    /** Google Real-time Developer Notifications (Pub/Sub push) */
    @PostMapping("/google")
    public ResponseEntity<ApiResult<Void>> googleNotification(
            @RequestBody GooglePubSubPushRequest request) {
        webhookService.handleGoogleNotification(
                request.message() != null ? request.message().data() : null);
        return ResponseEntity.ok(ApiResult.<Void>builder().build());
    }
}
