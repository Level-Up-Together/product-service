package io.pinkspider.leveluptogethermvp.gamificationservice.diamond.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** LUT-248: 마이페이지 "현재 보유 다이아" 표기용 잔액 응답. */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class UserDiamondBalanceResponse {

    private int balance;

    public static UserDiamondBalanceResponse of(int balance) {
        return UserDiamondBalanceResponse.builder().balance(balance).build();
    }
}
