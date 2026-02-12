package io.pinkspider.leveluptogethermvp.userservice.oauth.webhook.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 카카오 계정 상태 변경 이벤트 타입
 *
 * SSF(Shared Signals and Events Framework) 규격에 따른 이벤트 타입입니다.
 *
 * @see <a href="https://developers.kakao.com/docs/latest/ko/kakaologin/callback">Kakao SSF Events</a>
 */
@Getter
@RequiredArgsConstructor
public enum KakaoAccountEventType {

    // OAUTH 카테고리
    /**
     * 토큰 만료
     */
    TOKENS_REVOKED("https://schemas.kakao.com/events/oauth/tokens-revoked", "OAUTH", "토큰 만료"),

    /**
     * 사용자 앱 연결
     */
    USER_LINKED("https://schemas.kakao.com/events/oauth/user-linked", "OAUTH", "사용자 앱 연결"),

    /**
     * 사용자 앱 연결 해제
     */
    USER_UNLINKED("https://schemas.kakao.com/events/oauth/user-unlinked", "OAUTH", "사용자 앱 연결 해제"),

    /**
     * 동의항목 동의
     */
    USER_SCOPE_CONSENT("https://schemas.kakao.com/events/oauth/user-scope-consent", "OAUTH", "동의항목 동의"),

    /**
     * 동의항목 철회
     */
    USER_SCOPE_WITHDRAW("https://schemas.kakao.com/events/oauth/user-scope-withdraw", "OAUTH", "동의항목 철회"),

    // RISC 카테고리 (보안)
    /**
     * 계정 비활성화
     */
    ACCOUNT_DISABLED("https://schemas.openid.net/secevent/risc/event-type/account-disabled", "RISC", "계정 비활성화"),

    /**
     * 계정 활성화
     */
    ACCOUNT_ENABLED("https://schemas.openid.net/secevent/risc/event-type/account-enabled", "RISC", "계정 활성화"),

    /**
     * 자격증명 손상 (도용 의심)
     */
    CREDENTIAL_COMPROMISE("https://schemas.openid.net/secevent/risc/event-type/credential-compromise", "RISC", "자격증명 손상"),

    /**
     * 모든 세션 만료
     */
    SESSIONS_REVOKED("https://schemas.openid.net/secevent/risc/event-type/sessions-revoked", "RISC", "모든 세션 만료"),

    // CAEP 카테고리 (자격증명)
    /**
     * 인증 보안 수준 변경
     */
    ASSURANCE_LEVEL_CHANGE("https://schemas.openid.net/secevent/caep/event-type/assurance-level-change", "CAEP", "인증 보안 수준 변경"),

    /**
     * 비밀번호 변경
     */
    CREDENTIAL_CHANGE("https://schemas.openid.net/secevent/caep/event-type/credential-change", "CAEP", "비밀번호 변경"),

    // KAKAO 카테고리
    /**
     * 프로필 정보 변경
     */
    USER_PROFILE_CHANGED("https://schemas.kakao.com/events/kakao/user-profile-changed", "KAKAO", "프로필 정보 변경");

    private final String uri;
    private final String category;
    private final String description;

    /**
     * URI로 이벤트 타입 조회
     *
     * @param uri 이벤트 URI
     * @return 이벤트 타입, 없으면 null
     */
    public static KakaoAccountEventType fromUri(String uri) {
        if (uri == null) {
            return null;
        }
        for (KakaoAccountEventType type : values()) {
            if (type.uri.equals(uri)) {
                return type;
            }
        }
        return null;
    }
}
