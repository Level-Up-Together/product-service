package io.pinkspider.leveluptogethermvp.userservice.oauth.domain.dto.request;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonNaming(SnakeCaseStrategy.class)
public class MobileLoginRequestDto {

    @NotBlank(message = "provider is required")
    private String provider;  // google, kakao, apple

    @NotBlank(message = "accessToken is required")
    private String accessToken;  // Google/Kakao: access_token, Apple: id_token

    private String deviceType;  // android, ios

    private String deviceId;

    private String preferredLocale;  // ko, en, ar, ja

    private String preferredTimezone;  // IANA timezone ID (e.g., Asia/Seoul, Asia/Tokyo)

    /**
     * LUT-477: Apple 전용 — authorization code. 서버가 refresh token 교환 후 저장해 탈퇴 시 revoke 한다.
     * 구 클라이언트는 미전송(null) — refresh token 없이 로그인만 진행된다.
     */
    private String authorizationCode;

    /**
     * LUT-477: code 발급 시 사용한 redirect_uri (Android 웹 기반 SIWA 전용 — 교환 요청과 동일 값 필요).
     * iOS 네이티브 code 는 redirect_uri 없이 교환되므로 미전송.
     */
    private String authorizationCodeRedirectUri;
}
