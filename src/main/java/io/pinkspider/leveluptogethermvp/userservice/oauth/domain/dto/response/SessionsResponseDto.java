package io.pinkspider.leveluptogethermvp.userservice.oauth.domain.dto.response;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.math.BigInteger;
import java.util.List;
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
public class SessionsResponseDto {

    private List<Session> sessionList;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonNaming(SnakeCaseStrategy.class)
    public static class Session {

        private String deviceType;
        private BigInteger refreshTokenRemaining;
        private boolean accessTokenValid;
        private String loginTime;
        private BigInteger accessTokenRemaining;
        private boolean shouldRenew;
        private String accessToken;
        private String deviceId;
        private boolean refreshTokenValid;
        private String memberId;
        private String refreshToken;
    }
}
