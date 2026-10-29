package io.pinkspider.leveluptogethermvp.gamificationservice.subscription.application;

import io.pinkspider.global.exception.CustomException;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.dto.GoogleOrder;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.dto.ResolvedPrice;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.entity.SubscriptionPaymentHistory;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.entity.UserSubscription;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.enums.SubscriptionPaymentEventType;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.infrastructure.SubscriptionPaymentHistoryRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * LUT-617: Android 구독 갱신 원장을 Play 주문 연번 기준으로 맞춘다.
 *
 * <p>예전엔 RTDN·자가 치유·verify 가 모두 "처리 시점의 {@code latestOrderId} 1건" 만 기록했다. RTDN 이 갱신마다 들어오면 충분하지만,
 * 전달 경로가 끊기면(2026-10-26 dev Pub/Sub 구독 만료) 중간 갱신 {@code ..2~..4} 가 영구 누락됐다. 갱신 주문 ID 는 {@code
 * GPA.xxxx..N} 연번이므로 원장의 마지막 N 과 Play 의 최신 N 사이를 Orders API({@code orders.get})로 채우면 RTDN 유실과 무관하게
 * 원장이 완결된다.
 *
 * <ul>
 *   <li>주문 조회는 <b>best-effort</b>: 실패해도 권한 동기화는 막지 않는다. 최신 주문만은 조회 실패 시 예전 방식(이벤트 시각·가격 null)으로라도
 *       기록하고, 중간 주문은 다음 호출 때 다시 시도된다(원장의 마지막 N 이 그대로라 같은 구간을 다시 본다).
 *   <li>주문이 주는 {@code createTime}·{@code total}·{@code servicePeriodEnd} 가 원장의
 *       occurred_at·금액(STORE)·expires_at 이 된다. 같은 만료의 행이 다른 주문 ID 로 이미 있으면(자가 치유가 늦게 기록한 경우) 주문
 *       기준으로 <b>정정</b>한다.
 *   <li>{@code full=true}(수동 정합)는 1번부터 전부 대조하고, {@code full=false}(RTDN·자가 치유)는 마지막 N 다음부터만 본다.
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GoogleOrderLedgerSync {

    /** GPA.3329-9857-6705-37595 / GPA.3329-9857-6705-37595..5 → base + 연번 */
    static final Pattern ORDER_ID = Pattern.compile("^(.+?)(?:\\.\\.(\\d+))?$");

    /** 한 번에 되짚는 최대 주문 수 — 폭주 방지(월 구독 5년치) */
    static final int MAX_BACKFILL = 60;

    private final SubscriptionPaymentHistoryRepository repository;
    private final SubscriptionPaymentHistoryRecorder recorder;
    private final SubscriptionVerificationService verificationService;
    private final SubscriptionPriceResolver priceResolver;
    private final RtdnMetrics metrics;

    /**
     * 최신 주문까지의 갱신 결제 이력을 맞춘다.
     *
     * @param subscription 구독 행 (userId·platform·plan 출처)
     * @param latestOrderId Play {@code subscriptionsv2.latestOrderId} — null 이면 주문 ID 없이 1건 기록
     * @param trial 최신 결제가 무료 체험인지 (주문 ID 가 없거나 최초 주문일 때만 의미 있음 — 갱신은 항상 유료)
     * @param latestExpiresAt 최신 만료(구독 행에 반영된 값) — 최신 주문의 expires_at 폴백
     * @param fallbackOccurredAt 주문 조회가 안 될 때 쓸 결제 시각(RTDN eventTime) — null 이면 now
     * @param full true 면 1번 주문부터 전부 대조(수동 정합), false 면 마지막 기록 다음부터
     * @return 새로 쓰거나 정정한 행 수
     */
    @Transactional(transactionManager = "gamificationTransactionManager")
    public int syncRenewals(
            UserSubscription subscription,
            String latestOrderId,
            boolean trial,
            LocalDateTime latestExpiresAt,
            LocalDateTime fallbackOccurredAt,
            boolean full) {
        LocalDateTime fallback =
                fallbackOccurredAt != null ? fallbackOccurredAt : LocalDateTime.now();
        Matcher matcher = latestOrderId == null ? null : ORDER_ID.matcher(latestOrderId);
        if (matcher == null || !matcher.matches() || matcher.group(2) == null) {
            // 주문 ID 가 없거나 최초 주문(연번 없음) — 되짚을 갱신이 없다. 예전 방식 그대로 1건
            recorder.record(
                    subscription,
                    SubscriptionPaymentEventType.RENEWAL,
                    trial,
                    null,
                    null,
                    latestOrderId,
                    latestExpiresAt,
                    fallback);
            return 1;
        }

        String base = matcher.group(1);
        int latestN = Integer.parseInt(matcher.group(2));
        List<SubscriptionPaymentHistory> siblings =
                repository.findByUserIdAndTransactionIdStartingWith(subscription.getUserId(), base);
        Set<String> recordedIds =
                siblings.stream()
                        .map(SubscriptionPaymentHistory::getTransactionId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());
        int maxRecorded =
                recordedIds.stream()
                        .map(GoogleOrderLedgerSync::suffixOf)
                        .filter(Objects::nonNull)
                        .mapToInt(Integer::intValue)
                        .max()
                        .orElse(0);

        int from = full ? 1 : maxRecorded + 1;
        from = Math.max(from, latestN - MAX_BACKFILL + 1);
        from = Math.max(from, 1);

        int written = 0;
        for (int n = from; n <= latestN; n++) {
            String orderId = base + ".." + n;
            boolean isLatest = n == latestN;
            if (!full && recordedIds.contains(orderId)) {
                continue;
            }
            Optional<GoogleOrder> order = lookup(orderId);
            if (order != null && order.isPresent()) {
                written +=
                        upsertFromOrder(
                                subscription,
                                order.get(),
                                orderId,
                                isLatest ? latestExpiresAt : null);
            } else if (isLatest && !recordedIds.contains(orderId)) {
                // 최신 주문은 조회가 안 돼도 비워 두지 않는다 — 예전 방식(이벤트 시각, 가격은 정가 폴백)
                recorder.record(
                        subscription,
                        SubscriptionPaymentEventType.RENEWAL,
                        false,
                        null,
                        null,
                        orderId,
                        latestExpiresAt,
                        fallback);
                written++;
            } else if (order != null) {
                log.info(
                        "Google 주문 없음 — 건너뜀: userId={}, orderId={}",
                        subscription.getUserId(),
                        orderId);
            }
        }
        metrics.ledgerRowsWritten(full ? "reconcile" : "rtdn", written);
        return written;
    }

    /** orders.get — 일시 실패·거부는 삼키고 null (호출자가 폴백), 404 는 Optional.empty */
    private Optional<GoogleOrder> lookup(String orderId) {
        try {
            Optional<GoogleOrder> order = verificationService.fetchGoogleOrder(orderId);
            if (order.isEmpty()) {
                metrics.orderLookupFailed("not_found");
            }
            return order;
        } catch (StoreTransientException e) {
            metrics.orderLookupFailed("transient");
            log.warn(
                    "Google 주문 조회 일시 실패 — 다음 동기화 때 재시도: orderId={}, error={}",
                    orderId,
                    e.getMessage());
            return null;
        } catch (CustomException e) {
            metrics.orderLookupFailed("rejected");
            log.warn("Google 주문 조회 거부 — 건너뜀: orderId={}, code={}", orderId, e.getCode());
            return null;
        }
    }

    /**
     * 주문 1건을 원장에 반영한다 — 거래 ID 가 같은 행이 있으면 시각·금액·만료를 주문 기준으로 정정, 같은 만료의 다른 행이 있으면 그 행을 이 주문으로 정정,
     * 없으면 삽입.
     *
     * @return 변경(삽입·정정)된 행 수 0 또는 1
     */
    private int upsertFromOrder(
            UserSubscription subscription,
            GoogleOrder order,
            String orderId,
            LocalDateTime fallbackExpiresAt) {
        LocalDateTime expiresAt =
                order.servicePeriodEnd() != null ? order.servicePeriodEnd() : fallbackExpiresAt;
        if (expiresAt == null) {
            log.warn("Google 주문에 이용기간 종료 없음 — 건너뜀: orderId={}", orderId);
            return 0;
        }
        LocalDateTime occurredAt =
                order.createTime() != null
                        ? order.createTime()
                        : (order.servicePeriodStart() != null
                                ? order.servicePeriodStart()
                                : LocalDateTime.now());
        ResolvedPrice price =
                priceResolver.resolve(
                        order.totalAmount(),
                        order.totalCurrency(),
                        false,
                        subscription.getPlan(),
                        subscription.getPlatform(),
                        occurredAt.toLocalDate());
        String userId = subscription.getUserId();

        Optional<SubscriptionPaymentHistory> byTransaction =
                repository
                        .findByUserIdAndEventTypeAndTransactionId(
                                userId, SubscriptionPaymentEventType.RENEWAL, orderId)
                        .stream()
                        .findFirst();
        if (byTransaction.isPresent()) {
            SubscriptionPaymentHistory row = byTransaction.get();
            // 만료를 옮길 때 다른 행과 uk 가 겹치면 만료는 두고 시각·금액만 맞춘다
            LocalDateTime newExpiresAt =
                    expiresAt.equals(row.getExpiresAt())
                                    || repository
                                            .findByUserIdAndEventTypeAndExpiresAt(
                                                    userId,
                                                    SubscriptionPaymentEventType.RENEWAL,
                                                    expiresAt)
                                            .isPresent()
                            ? null
                            : expiresAt;
            boolean changed =
                    row.correctFromStoreOrder(
                            orderId,
                            occurredAt,
                            newExpiresAt,
                            price.amount(),
                            price.currency(),
                            price.source());
            if (changed) {
                repository.flush();
                log.info(
                        "구독 결제 이력 정정(주문 기준): userId={}, orderId={}, occurredAt={}, expiresAt={}",
                        userId,
                        orderId,
                        occurredAt,
                        row.getExpiresAt());
                return 1;
            }
            return 0;
        }

        Optional<SubscriptionPaymentHistory> byExpiry =
                repository.findByUserIdAndEventTypeAndExpiresAt(
                        userId, SubscriptionPaymentEventType.RENEWAL, expiresAt);
        if (byExpiry.isPresent()) {
            // 자가 치유가 늦게 기록하며 다른(최신) 주문 ID 를 붙여 둔 행 — 이 기간의 진짜 주문으로 바로잡는다
            SubscriptionPaymentHistory row = byExpiry.get();
            String previousId = row.getTransactionId();
            row.correctFromStoreOrder(
                    orderId, occurredAt, null, price.amount(), price.currency(), price.source());
            repository.flush();
            log.info(
                    "구독 결제 이력 거래 ID 정정: userId={}, expiresAt={}, {} → {}",
                    userId,
                    expiresAt,
                    previousId,
                    orderId);
            return 1;
        }

        repository.save(
                SubscriptionPaymentHistory.builder()
                        .userId(userId)
                        .platform(subscription.getPlatform())
                        .productId(subscription.getProductId())
                        .basePlanId(subscription.getBasePlanId())
                        .plan(subscription.getPlan())
                        .eventType(SubscriptionPaymentEventType.RENEWAL)
                        .trial(false)
                        .priceAmount(price.amount())
                        .priceCurrency(price.currency())
                        .priceSource(price.source())
                        .testPurchase(Boolean.TRUE.equals(subscription.getTestPurchase()))
                        .transactionId(orderId)
                        .expiresAt(expiresAt)
                        .occurredAt(occurredAt)
                        .build());
        repository.flush();
        log.info(
                "구독 결제 이력 백필: userId={}, orderId={}, occurredAt={}, expiresAt={}, price={} {} ({})",
                userId,
                orderId,
                occurredAt,
                expiresAt,
                price.amount(),
                price.currency(),
                price.source());
        return 1;
    }

    /** GPA.xxxx..7 → 7, GPA.xxxx → 0, 형식 외 → null */
    static Integer suffixOf(String orderId) {
        Matcher m = ORDER_ID.matcher(orderId);
        if (!m.matches()) {
            return null;
        }
        return m.group(2) == null ? 0 : Integer.parseInt(m.group(2));
    }
}
