package io.pinkspider.leveluptogethermvp.gamificationservice.diamond.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * LUT-401: 다이아 묶음상품 IAP 구매 건의 결제 상태.
 *
 * <p>구매 시점에는 항상 {@code PAID}로 저장된다. {@code REFUNDED}로의 전환은 이번 범위에 포함되지
 * 않은 자동 환불 감지(Apple Server Notifications V2 / Google RTDN) 후속 작업의 몫 — 컬럼/enum만
 * 미리 확보해둔다.
 */
@Getter
@RequiredArgsConstructor
public enum DiamondPurchaseStatus {

    PAID("결제완료"),
    REFUNDED("환불됨");

    private final String description;
}
