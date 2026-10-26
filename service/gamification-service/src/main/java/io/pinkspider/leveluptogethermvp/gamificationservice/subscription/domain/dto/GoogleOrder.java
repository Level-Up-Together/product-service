package io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * LUT-617: Play Developer API {@code orders.get} 응답의 주문 1건.
 *
 * <p>{@code subscriptionsv2} 는 최신 주문 ID 하나만 주지만 Orders API 는 주문 ID 로 실제 결제 시각·금액·이용 기간을 돌려준다. 갱신 주문
 * ID 는 {@code GPA.xxxx..N} 연번이라, 원장에 빠진 N 을 이 API 로 채운다(백필).
 *
 * @param orderId 주문 ID (GPA.xxxx 또는 GPA.xxxx..N)
 * @param state 주문 상태 원문 (PROCESSED / CANCELED / REFUNDED / PARTIALLY_REFUNDED …) — 없으면 null
 * @param createTime 주문 생성(결제) 시각 — 없으면 null
 * @param totalAmount 구매자가 실제 지불한 금액 — 없으면 null
 * @param totalCurrency 금액 통화 (ISO 4217) — 없으면 null
 * @param servicePeriodStart 이 주문이 덮는 이용 기간 시작 — 구독 주문이 아니거나 없으면 null
 * @param servicePeriodEnd 이 주문이 덮는 이용 기간 종료 — 원장의 expires_at 에 해당. 없으면 null
 */
public record GoogleOrder(
        String orderId,
        String state,
        LocalDateTime createTime,
        BigDecimal totalAmount,
        String totalCurrency,
        LocalDateTime servicePeriodStart,
        LocalDateTime servicePeriodEnd) {}
