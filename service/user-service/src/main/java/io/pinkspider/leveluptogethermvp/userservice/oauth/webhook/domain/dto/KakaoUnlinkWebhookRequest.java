package io.pinkspider.leveluptogethermvp.userservice.oauth.webhook.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 카카오 연결 해제 웹훅 요청 DTO
 *
 * 카카오에서 연결 해제 시 전달하는 파라미터를 담습니다.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KakaoUnlinkWebhookRequest {

    /**
     * 연결 해제를 요청한 앱 ID
     */
    private String appId;

    /**
     * 사용자의 카카오 회원번호
     */
    private String userId;

    /**
     * 연결 해제 경로
     * - ACCOUNT_DELETE: 카카오 계정 삭제
     * - UNLINK_FROM_APPS: 카카오 계정 설정 > 연결된 앱 관리에서 연결 끊기
     * - FORCED_UNLINK_BY_ADMIN: 관리자에 의한 강제 연결 끊기
     */
    private String referrerType;

    /**
     * 그룹 앱인 경우만 제공되는 토큰
     */
    private String groupUserToken;
}
