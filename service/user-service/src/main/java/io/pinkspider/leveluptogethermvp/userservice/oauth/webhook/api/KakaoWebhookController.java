package io.pinkspider.leveluptogethermvp.userservice.oauth.webhook.api;

import io.pinkspider.leveluptogethermvp.userservice.oauth.webhook.application.KakaoWebhookService;
import io.pinkspider.leveluptogethermvp.userservice.oauth.webhook.domain.dto.KakaoUnlinkWebhookRequest;
import io.pinkspider.leveluptogethermvp.userservice.oauth.webhook.domain.dto.SetValidationErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 카카오 로그인 웹훅 API 컨트롤러
 *
 * 카카오에서 제공하는 두 가지 웹훅을 처리합니다:
 * 1. 연결 해제 웹훅 (Unlink Webhook) - 레거시 방식
 * 2. 계정 상태 변경 웹훅 (Account Status Change Webhook) - SSF 기반
 *
 * @see <a href="https://developers.kakao.com/docs/latest/ko/kakaologin/callback">Kakao Webhook Documentation</a>
 */
@RestController
@RequestMapping("/api/v1/oauth/kakao/webhook")
@RequiredArgsConstructor
@Slf4j
public class KakaoWebhookController {

    private final KakaoWebhookService kakaoWebhookService;

    /**
     * 연결 해제 웹훅 (Unlink Webhook)
     *
     * 사용자가 카카오 계정 설정에서 앱 연결을 끊었을 때 호출됩니다.
     * GET/POST 메서드 모두 지원하며, Authorization 헤더로 어드민 키를 검증합니다.
     *
     * @param authorization KakaoAK ${PRIMARY_ADMIN_KEY} 형식
     * @param appId 연결 해제를 요청한 앱 ID
     * @param userId 사용자의 카카오 회원번호
     * @param referrerType 연결 해제 경로 (ACCOUNT_DELETE, UNLINK_FROM_APPS 등)
     * @param groupUserToken 그룹 앱인 경우만 제공 (선택)
     * @return 200 OK (3초 내 응답 필수)
     */
    @RequestMapping(value = "/unlink", method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<Void> handleUnlinkWebhook(
        @RequestHeader("Authorization") String authorization,
        @RequestParam("app_id") String appId,
        @RequestParam("user_id") String userId,
        @RequestParam("referrer_type") String referrerType,
        @RequestParam(value = "group_user_token", required = false) String groupUserToken
    ) {
        log.info("카카오 연결 해제 웹훅 수신 - appId: {}, userId: {}, referrerType: {}",
            appId, userId, referrerType);

        KakaoUnlinkWebhookRequest request = KakaoUnlinkWebhookRequest.builder()
            .appId(appId)
            .userId(userId)
            .referrerType(referrerType)
            .groupUserToken(groupUserToken)
            .build();

        kakaoWebhookService.handleUnlinkWebhook(authorization, request);

        return ResponseEntity.ok().build();
    }

    /**
     * 계정 상태 변경 웹훅 (Account Status Change Webhook)
     *
     * 사용자의 카카오 계정 상태가 변경되었을 때 호출됩니다.
     * SSF(Shared Signals and Events Framework) 규격 기반으로 SET(Security Event Token) JWT를 사용합니다.
     *
     * 지원 이벤트:
     * - tokens-revoked: 토큰 만료
     * - user-linked: 사용자 앱 연결
     * - user-unlinked: 사용자 앱 연결 해제
     * - user-scope-consent: 동의항목 동의
     * - user-scope-withdraw: 동의항목 철회
     * - account-disabled: 계정 비활성화
     * - account-enabled: 계정 활성화
     * - credential-compromise: 자격증명 손상
     * - sessions-revoked: 모든 세션 만료
     * - assurance-level-change: 인증 보안 수준 변경
     * - credential-change: 비밀번호 변경
     * - user-profile-changed: 프로필 정보 변경
     *
     * @param setToken SET(Security Event Token) JWT
     * @return 202 Accepted (검증 성공) 또는 400 Bad Request (검증 실패)
     */
    @PostMapping(value = "/status", consumes = "application/secevent+jwt")
    public ResponseEntity<?> handleAccountStatusWebhook(
        @RequestBody String setToken,
        HttpServletRequest httpRequest
    ) {
        log.info("카카오 계정 상태 변경 웹훅 수신");

        try {
            kakaoWebhookService.handleAccountStatusWebhook(setToken);
            return ResponseEntity.status(HttpStatus.ACCEPTED).build();
        } catch (KakaoWebhookService.SetValidationException e) {
            log.warn("SET 검증 실패 - error: {}, description: {}", e.getErrorCode(), e.getErrorDescription());
            SetValidationErrorResponse errorResponse = new SetValidationErrorResponse(
                e.getErrorCode(),
                e.getErrorDescription()
            );
            return ResponseEntity.badRequest()
                .contentType(MediaType.APPLICATION_JSON)
                .body(errorResponse);
        }
    }
}
