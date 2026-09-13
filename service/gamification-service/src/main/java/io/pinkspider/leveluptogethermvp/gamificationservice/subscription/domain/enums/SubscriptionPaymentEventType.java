package io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.enums;

/**
 * 구독 결제 이력 이벤트 타입 (LUT-486)
 *
 * <ul>
 *   <li>{@code PURCHASE} — 최초 구매/복원으로 구독 행이 새로 생긴 결제 (무료 체험 시작 포함, trial 플래그로 구분)
 *   <li>{@code RENEWAL} — 기존 구독의 만료가 연장된 결제 (자동갱신·플랜 변경·verify 재검증 경유 포함)
 *   <li>{@code REFUND} — 스토어 환불/회수로 권한이 즉시 종료된 이벤트
 * </ul>
 */
public enum SubscriptionPaymentEventType {
    PURCHASE,
    RENEWAL,
    REFUND
}
