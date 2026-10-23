package io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.enums;

/**
 * LUT-600: 구독 결제 이력 금액의 출처.
 *
 * <ul>
 *   <li>{@code STORE} — 스토어가 준 실결제 금액 (iOS App Store Server API JWS). 정산 기준에 가장 가깝다
 *   <li>{@code LIST} — 스토어가 금액을 주지 않아(Android subscriptionsv2) 플랜 정가({@code plan_list_price})로 채운 값
 *   <li>{@code TRIAL} — 무료 체험 시작 결제. 금액 0
 * </ul>
 *
 * <p>null = 금액 출처 미상 (LUT-600 이전 행 중 백필 안 된 것, 또는 정가도 없는 경우).
 */
public enum PriceSource {
    STORE,
    LIST,
    TRIAL
}
