package io.pinkspider.leveluptogethermvp.userservice.oauth.domain.dto.jwt;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 소셜 로그인 응답 (QA-108)
 *
 * <p>기존 사용자: JWT(access/refresh) 발급 → {@code isNewUser=false}
 * <p>신규 사용자: signup token 발급 (DB INSERT 안 함) → {@code isNewUser=true}
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonNaming(SnakeCaseStrategy.class)
public class SocialLoginResponseDto {

    /**
     * 신규 사용자 여부. true면 signupToken으로 닉네임/약관 입력 후 complete-signup 호출 필요.
     */
    private boolean isNewUser;

    // === 신규 사용자 전용 ===
    private String signupToken;
    private String suggestedNickname;

    // === 기존 사용자 전용 ===
    private String accessToken;
    private String refreshToken;
    @Builder.Default
    private String tokenType = "Bearer";
    private long expiresIn;
    private String userId;
    private String deviceId;
    private boolean nicknameSet;

    public static SocialLoginResponseDto newUser(String signupToken, String suggestedNickname) {
        return SocialLoginResponseDto.builder()
            .isNewUser(true)
            .signupToken(signupToken)
            .suggestedNickname(suggestedNickname)
            .build();
    }

    public static SocialLoginResponseDto existingUser(CreateJwtResponseDto jwt) {
        return SocialLoginResponseDto.builder()
            .isNewUser(false)
            .accessToken(jwt.getAccessToken())
            .refreshToken(jwt.getRefreshToken())
            .tokenType(jwt.getTokenType())
            .expiresIn(jwt.getExpiresIn())
            .userId(jwt.getUserId())
            .deviceId(jwt.getDeviceId())
            .nicknameSet(jwt.isNicknameSet())
            .build();
    }
}
