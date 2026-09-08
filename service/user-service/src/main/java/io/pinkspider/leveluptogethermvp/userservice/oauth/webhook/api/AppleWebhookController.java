package io.pinkspider.leveluptogethermvp.userservice.oauth.webhook.api;

import com.fasterxml.jackson.databind.JsonNode;
import io.pinkspider.leveluptogethermvp.userservice.oauth.webhook.application.AppleWebhookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sign in with Apple Server-to-Server Notification 엔드포인트 (LUT-476).
 *
 * <p>ASC > App ID > Sign in with Apple 설정에 이 URL 을 등록한다 (dev/prod 각각).
 * 인증 없음(permitAll) — 페이로드 자체가 Apple 서명 JWS 라 서비스에서 검증한다.
 */
@RestController
@RequestMapping("/api/v1/oauth/apple/webhook")
@RequiredArgsConstructor
@Slf4j
public class AppleWebhookController {

    private final AppleWebhookService appleWebhookService;

    @PostMapping
    public ResponseEntity<Void> handleNotification(@RequestBody JsonNode body) {
        // Apple 공식 문서상 본문 키는 "payload" — 방어적으로 "signedPayload" 도 허용
        String signedPayload = firstNonBlank(
            body.path("payload").asText(null),
            body.path("signedPayload").asText(null));
        if (signedPayload == null) {
            log.warn("Apple 웹훅 - payload 없는 요청 수신");
            return ResponseEntity.badRequest().build();
        }
        appleWebhookService.handleNotification(signedPayload);
        return ResponseEntity.ok().build();
    }

    private String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        return (b != null && !b.isBlank()) ? b : null;
    }
}
