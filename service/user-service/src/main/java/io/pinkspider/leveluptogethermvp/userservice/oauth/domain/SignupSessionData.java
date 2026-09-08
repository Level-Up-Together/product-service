package io.pinkspider.leveluptogethermvp.userservice.oauth.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 신규 사용자 회원가입 진행을 위한 임시 세션 데이터 (QA-108)
 *
 * <p>OAuth provider에서 받은 사용자 정보 + 클라이언트가 전달한 locale/timezone을
 * Redis에 임시 저장하여, 닉네임 설정/약관 동의 완료 시점에 INSERT한다.
 *
 * <p>providerUserId(LUT-476)는 소셜 공급자의 사용자 고유 ID.
 * appleRefreshTokenEnc/appleClientId(LUT-477)는 apple 가입 플로우에서 code 교환으로 확보한
 * refresh token(AES 암호화 상태로 보관 — Redis 에 평문을 두지 않는다)과 발급 client_id.
 * 배포 전 발급된 구 세션은 신규 필드가 null 로 역직렬화된다.
 */
public record SignupSessionData(
    String signupToken,
    String provider,
    String email,
    String suggestedNickname,
    String preferredLocale,
    String preferredTimezone,
    String providerUserId,
    String appleRefreshTokenEnc,
    String appleClientId
) {

    @JsonCreator
    public SignupSessionData(
        @JsonProperty("signupToken") String signupToken,
        @JsonProperty("provider") String provider,
        @JsonProperty("email") String email,
        @JsonProperty("suggestedNickname") String suggestedNickname,
        @JsonProperty("preferredLocale") String preferredLocale,
        @JsonProperty("preferredTimezone") String preferredTimezone,
        @JsonProperty("providerUserId") String providerUserId,
        @JsonProperty("appleRefreshTokenEnc") String appleRefreshTokenEnc,
        @JsonProperty("appleClientId") String appleClientId
    ) {
        this.signupToken = signupToken;
        this.provider = provider;
        this.email = email;
        this.suggestedNickname = suggestedNickname;
        this.preferredLocale = preferredLocale;
        this.preferredTimezone = preferredTimezone;
        this.providerUserId = providerUserId;
        this.appleRefreshTokenEnc = appleRefreshTokenEnc;
        this.appleClientId = appleClientId;
    }

    /** LUT-476 이전 호환 — apple 토큰 없는 생성 (테스트·비 apple 플로우) */
    public SignupSessionData(
        String signupToken, String provider, String email, String suggestedNickname,
        String preferredLocale, String preferredTimezone, String providerUserId) {
        this(signupToken, provider, email, suggestedNickname, preferredLocale, preferredTimezone,
            providerUserId, null, null);
    }

    public SignupSessionData withToken(String newToken) {
        return new SignupSessionData(newToken, provider, email, suggestedNickname,
            preferredLocale, preferredTimezone, providerUserId, appleRefreshTokenEnc, appleClientId);
    }
}
