package io.pinkspider.leveluptogethermvp.gamificationservice.diamond.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * LUT-248: 마이페이지 "현재 보유 다이아" 표기용 잔액 응답.
 * LUT-356: 핑크다이아(결제 재화) 분리 — balance는 블루+핑크 합계로 하위호환 유지.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class UserDiamondBalanceResponse {

    /** 총 보유 다이아 (블루 + 핑크) */
    private int balance;

    /** 블루 다이아 — 게임 내 획득 재화 */
    private int blueBalance;

    /** 핑크다이아 — 결제 구매 재화 */
    private int pinkBalance;

    public static UserDiamondBalanceResponse of(int blueBalance, int pinkBalance) {
        return UserDiamondBalanceResponse.builder()
            .balance(blueBalance + pinkBalance)
            .blueBalance(blueBalance)
            .pinkBalance(pinkBalance)
            .build();
    }
}
