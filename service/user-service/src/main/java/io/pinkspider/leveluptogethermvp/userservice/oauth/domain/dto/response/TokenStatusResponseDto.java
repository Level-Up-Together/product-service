package io.pinkspider.leveluptogethermvp.userservice.oauth.domain.dto.response;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.math.BigInteger;
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
public class TokenStatusResponseDto {

    private BigInteger refreshTokenRemaining;
    private boolean accessTokenValid;
    private BigInteger loginTime;
    private BigInteger accessTokenRemaining;
    private boolean canRenewRefreshToken;
    private String lastRefreshTime;
    private boolean shouldRenewRefreshToken;
    private boolean refreshTokenValid;
}
