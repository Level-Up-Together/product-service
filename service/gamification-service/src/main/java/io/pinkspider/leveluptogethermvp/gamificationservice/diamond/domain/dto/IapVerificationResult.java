package io.pinkspider.leveluptogethermvp.gamificationservice.diamond.domain.dto;

import java.math.BigDecimal;

/**
 * LUT-401: IAP 영수증/구매 검증 결과 — 내부 서비스 간 전달용(API 응답 아님).
 *
 * <p>{@code priceAmount}/{@code priceCurrency}는 검증 응답에서 확보한 실제 결제 금액/통화다. 검증이 비활성화(dev)이거나 가격을 확보하지
 * 못한 경우 둘 다 {@code null}일 수 있다 — 결제이력 화면은 이 값이 없으면 공란으로 표시한다.
 *
 * <p>{@code testPurchase}(LUT-666)는 스토어 테스트 결제(iOS Sandbox·TestFlight / Google 테스트 구매) 여부 — KPI
 * 매출에서 제외한다.
 */
public record IapVerificationResult(
        String transactionId, BigDecimal priceAmount, String priceCurrency, boolean testPurchase) {

    /** LUT-666 이전 시그니처 유지 — 테스트 결제 판정이 없는 경로는 실결제로 본다 */
    public IapVerificationResult(
            String transactionId, BigDecimal priceAmount, String priceCurrency) {
        this(transactionId, priceAmount, priceCurrency, false);
    }

    public static IapVerificationResult withoutPrice(String transactionId) {
        return new IapVerificationResult(transactionId, null, null, false);
    }

    /** LUT-666: 가격은 못 얻었지만 테스트 결제 여부는 아는 경우(샌드박스 영수증 등) */
    public static IapVerificationResult withoutPrice(String transactionId, boolean testPurchase) {
        return new IapVerificationResult(transactionId, null, null, testPurchase);
    }
}
