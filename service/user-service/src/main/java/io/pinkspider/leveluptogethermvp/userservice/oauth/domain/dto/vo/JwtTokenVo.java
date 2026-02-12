package io.pinkspider.leveluptogethermvp.userservice.oauth.domain.dto.vo;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.Date;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@JsonNaming(SnakeCaseStrategy.class)
public class JwtTokenVo {

    private String accessToken;

    private String refreshToken;

    private Date accessTokenExpiredDate;
}
