package io.pinkspider.leveluptogethermvp.gamificationservice.subscription.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.pinkspider.global.exception.CustomException;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.dto.AppleSubscriptionNotification;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.dto.GoogleSubscriptionState;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * LUT-452: 스토어 구독 웹훅 수신 처리 — ASSN V2 / Google RTDN.
 *
 * <p><b>응답 규약</b>: 영구 실패(서명 불량·파싱 불가·미매칭)는 여기서 삼키고 정상 반환한다 — 스토어가
 * 재전송해도 결과가 같아 재시도가 무의미하기 때문. DB 오류 등 일시 실패만 예외를 전파해 5xx 로 재전송을
 * 받는다. 상태 적용은 전부 수렴형이라 at-least-once 멱등.
 *
 * <p><b>인증</b>: JWT 없이 열린 경로(permitAll) — Apple 은 signedPayload JWS 서명 검증이 인증이고,
 * Google 은 페이로드를 신뢰하지 않고 purchaseToken 으로 Play API 를 재조회해 위조를 무력화한다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionWebhookService {

    // Google RTDN subscriptionNotification.notificationType
    static final int GOOGLE_TYPE_PRICE_CHANGE_CONFIRMED = 8;
    static final int GOOGLE_TYPE_REVOKED = 12;

    private final SubscriptionVerificationService verificationService;
    private final SubscriptionWebhookTxService webhookTxService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** ASSN V2 — signedPayload 서명 검증 후 이벤트 적용 */
    public void handleAppleNotification(String signedPayload) {
        if (signedPayload == null || signedPayload.isBlank()) {
            log.warn("ASSN 빈 payload — 스킵");
            return;
        }
        AppleSubscriptionNotification notification;
        try {
            notification = verificationService.decodeAppleNotification(signedPayload);
        } catch (CustomException e) {
            // 서명 불량 — 재전송해도 같으니 정상 응답으로 재시도를 멈춘다
            log.error("ASSN 서명 검증 실패 — 폐기");
            return;
        }
        if ("TEST".equals(notification.notificationType())) {
            log.info("ASSN TEST 알림 수신 확인");
            return;
        }
        webhookTxService.applyAppleNotification(notification);
    }

    /** Google RTDN — base64 DeveloperNotification 파싱 후 Play API 재조회로 상태 적용 */
    public void handleGoogleNotification(String base64Data) {
        JsonNode notification = parseGoogleNotification(base64Data);
        if (notification == null) {
            return;
        }
        if (notification.has("testNotification")) {
            log.info("RTDN 테스트 알림 수신 확인");
            return;
        }

        // 환불(취소) 알림 — 별도 페이로드로 온다
        JsonNode voided = notification.path("voidedPurchaseNotification");
        if (voided.hasNonNull("purchaseToken")) {
            webhookTxService.revokeByPurchaseToken(voided.path("purchaseToken").asText());
            return;
        }

        JsonNode sub = notification.path("subscriptionNotification");
        if (!sub.hasNonNull("purchaseToken")) {
            log.info("RTDN 구독 알림 아님 — 스킵");
            return;
        }
        String purchaseToken = sub.path("purchaseToken").asText();
        int type = sub.path("notificationType").asInt(-1);

        if (type == GOOGLE_TYPE_REVOKED) {
            webhookTxService.revokeByPurchaseToken(purchaseToken);
            return;
        }
        if (type == GOOGLE_TYPE_PRICE_CHANGE_CONFIRMED) {
            log.info("RTDN 가격 변경 동의 수신");
            // 상태 변화는 없지만 최신 상태로 동기화해 둔다 (아래 공통 경로)
        }

        // RTDN 은 트리거 — 진실은 Play API 재조회 (페이로드 위조 방어 겸용)
        GoogleSubscriptionState state;
        try {
            state = verificationService.fetchGoogleSubscription(purchaseToken);
        } catch (CustomException e) {
            // 잘못된 토큰 등 영구 실패로 보고 폐기 — 재전송해도 같은 결과
            log.error("RTDN 상태 재조회 실패 — 폐기: type={}", type);
            return;
        }
        webhookTxService.applyGoogleState(purchaseToken, state);
    }

    private JsonNode parseGoogleNotification(String base64Data) {
        if (base64Data == null || base64Data.isBlank()) {
            log.warn("RTDN 빈 data — 스킵");
            return null;
        }
        try {
            String json = new String(Base64.getDecoder().decode(base64Data), StandardCharsets.UTF_8);
            return objectMapper.readTree(json);
        } catch (Exception e) {
            log.error("RTDN 페이로드 파싱 실패 — 폐기: {}", e.getMessage());
            return null;
        }
    }
}
