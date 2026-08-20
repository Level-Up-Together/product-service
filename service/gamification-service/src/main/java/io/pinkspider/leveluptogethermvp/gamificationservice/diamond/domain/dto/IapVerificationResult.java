package io.pinkspider.leveluptogethermvp.gamificationservice.diamond.domain.dto;

import java.math.BigDecimal;

/**
 * LUT-401: IAP 영수증/구매 검증 결과 — 내부 서비스 간 전달용(API 응답 아님).
 *
 * <p>{@code priceAmount}/{@code priceCurrency}는 검증 응답에서 확보한 실제 결제 금액/통화다. 검증이
 * 비활성화(dev)이거나 가격을 확보하지 못한 경우 둘 다 {@code null}일 수 있다 — 결제이력 화면은 이
 * 값이 없으면 공란으로 표시한다.
 */
public record IapVerificationResult(
    String transactionId,
    BigDecimal priceAmount,
    String priceCurrency
) {

    public static IapVerificationResult withoutPrice(String transactionId) {
        return new IapVerificationResult(transactionId, null, null);
    }
}
