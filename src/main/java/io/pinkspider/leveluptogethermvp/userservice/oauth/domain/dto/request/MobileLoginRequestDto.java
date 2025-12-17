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
}
