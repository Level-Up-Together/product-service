package io.pinkspider.leveluptogethermvp.userservice.oauth.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 신규 사용자 회원가입 진행을 위한 임시 세션 데이터 (QA-108)
 *
 * <p>OAuth provider에서 받은 사용자 정보 + 클라이언트가 전달한 locale/timezone을
 * Redis에 임시 저장하여, 닉네임 설정/약관 동의 완료 시점에 INSERT한다.
 *
 * <p>providerUserId(LUT-476)는 소셜 공급자의 사용자 고유 ID — 배포 전 발급된 구 세션은
 * null 로 역직렬화되며, 그 유저는 다음 로그인에서 백필된다.
 */
public record SignupSessionData(
    String signupToken,
    String provider,
    String email,
    String suggestedNickname,
    String preferredLocale,
    String preferredTimezone,
    String providerUserId
) {

    @JsonCreator
    public SignupSessionData(
        @JsonProperty("signupToken") String signupToken,
        @JsonProperty("provider") String provider,
        @JsonProperty("email") String email,
        @JsonProperty("suggestedNickname") String suggestedNickname,
        @JsonProperty("preferredLocale") String preferredLocale,
        @JsonProperty("preferredTimezone") String preferredTimezone,
        @JsonProperty("providerUserId") String providerUserId
    ) {
        this.signupToken = signupToken;
        this.provider = provider;
        this.email = email;
        this.suggestedNickname = suggestedNickname;
        this.preferredLocale = preferredLocale;
        this.preferredTimezone = preferredTimezone;
        this.providerUserId = providerUserId;
    }

    public SignupSessionData withToken(String newToken) {
        return new SignupSessionData(newToken, provider, email, suggestedNickname,
            preferredLocale, preferredTimezone, providerUserId);
    }
}
