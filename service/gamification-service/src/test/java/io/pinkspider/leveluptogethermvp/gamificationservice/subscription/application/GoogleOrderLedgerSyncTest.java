package io.pinkspider.leveluptogethermvp.gamificationservice.subscription.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.global.exception.CustomException;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.dto.GoogleOrder;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.dto.ResolvedPrice;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.entity.SubscriptionPaymentHistory;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.entity.UserSubscription;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.enums.PriceSource;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.enums.SubscriptionPaymentEventType;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.enums.SubscriptionPlan;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.infrastructure.SubscriptionPaymentHistoryRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("GoogleOrderLedgerSync 테스트 (LUT-617)")
class GoogleOrderLedgerSyncTest {

    private static final String BASE = "GPA.3329-9857-6705-37595";
    private static final LocalDateTime T0 = LocalDateTime.of(2026, 10, 26, 3, 12);

    @Mock private SubscriptionPaymentHistoryRepository repository;
    @Mock private SubscriptionPaymentHistoryRecorder recorder;
    @Mock private SubscriptionVerificationService verificationService;
    @Mock private SubscriptionPriceResolver priceResolver;
    @Mock private RtdnMetrics metrics;

    @InjectMocks private GoogleOrderLedgerSync sync;

    private UserSubscription sub;

    @BeforeEach
    void setUp() {
        sub =
                UserSubscription.builder()
                        .userId("user-1")
                        .platform("android")
                        .productId("membership")
                        .basePlanId("1m")
                        .plan(SubscriptionPlan.MONTHLY)
                        .startedAt(T0)
                        .expiresAt(T0.plusMinutes(5))
                        .autoRenew(true)
                        .trialUsed(false)
                        .purchaseToken("token-001")
                        .build();
        // 스토어 금액이 있으면 STORE, 없으면 정가 LIST — 실제 리졸버 규칙(LUT-600)과 같은 모양의 스텁
        when(priceResolver.resolve(any(), any(), anyBoolean(), any(), any(), any()))
                .thenAnswer(
                        inv -> {
                            BigDecimal amount = inv.getArgument(0);
                            String currency = inv.getArgument(1);
                            return amount != null
                                    ? new ResolvedPrice(amount, currency, PriceSource.STORE)
                                    : new ResolvedPrice(
                                            new BigDecimal("4900.00"), "KRW", PriceSource.LIST);
                        });
        when(repository.findByUserIdAndEventTypeAndTransactionId(any(), any(), any()))
                .thenReturn(List.of());
        when(repository.findByUserIdAndEventTypeAndExpiresAt(any(), any(), any()))
                .thenReturn(Optional.empty());
    }

    /** n 번째 갱신 주문 — 기간 5분, 생성 시각 = 기간 시작 */
    private GoogleOrder order(int n) {
        LocalDateTime start = T0.plusMinutes(5L * n);
        return new GoogleOrder(
                BASE + ".." + n,
                "PROCESSED",
                start,
                new BigDecimal("4900.00"),
                "KRW",
                start,
                start.plusMinutes(5));
    }

    private SubscriptionPaymentHistory row(
            String orderId, LocalDateTime expiresAt, LocalDateTime occurredAt) {
        return SubscriptionPaymentHistory.builder()
                .userId("user-1")
                .platform("android")
                .productId("membership")
                .basePlanId("1m")
                .plan(SubscriptionPlan.MONTHLY)
                .eventType(SubscriptionPaymentEventType.RENEWAL)
                .trial(false)
                .priceAmount(new BigDecimal("4900.00"))
                .priceCurrency("KRW")
                .priceSource(PriceSource.LIST)
                .transactionId(orderId)
                .expiresAt(expiresAt)
                .occurredAt(occurredAt)
                .build();
    }

    @Nested
    @DisplayName("되짚을 갱신이 없는 경우")
    class NoBackfill {

        @Test
        @DisplayName("주문 ID 가 없으면 예전 방식 그대로 1건 기록한다")
        void nullOrderIdRecordsOnce() {
            LocalDateTime expires = T0.plusMinutes(10);

            int written = sync.syncRenewals(sub, null, true, expires, T0.plusMinutes(6), false);

            assertThat(written).isEqualTo(1);
            verify(recorder)
                    .record(
                            eq(sub),
                            eq(SubscriptionPaymentEventType.RENEWAL),
                            eq(true),
                            eq(null),
                            eq(null),
                            eq(null),
                            eq(expires),
                            eq(T0.plusMinutes(6)));
            verify(verificationService, never()).fetchGoogleOrder(anyString());
        }

        @Test
        @DisplayName("최초 주문(연번 없음)은 갱신이 아니라 되짚지 않는다 — 폴백 시각 null 이면 now")
        void initialOrderRecordsOnce() {
            int written = sync.syncRenewals(sub, BASE, false, T0.plusMinutes(5), null, false);

            assertThat(written).isEqualTo(1);
            ArgumentCaptor<LocalDateTime> occurred = ArgumentCaptor.forClass(LocalDateTime.class);
            verify(recorder)
                    .record(
                            eq(sub),
                            eq(SubscriptionPaymentEventType.RENEWAL),
                            eq(false),
                            eq(null),
                            eq(null),
                            eq(BASE),
                            eq(T0.plusMinutes(5)),
                            occurred.capture());
            assertThat(occurred.getValue()).isAfter(LocalDateTime.now().minusMinutes(1));
            verify(verificationService, never()).fetchGoogleOrder(anyString());
        }
    }

    @Nested
    @DisplayName("RTDN 경로 (full=false) — 원장의 마지막 연번 다음부터")
    class Incremental {

        @Test
        @DisplayName("..1 까지 있고 Play 최신이 ..5 면 ..2~..5 를 주문의 실제 시각·금액(STORE)으로 채운다")
        void backfillsMissingOrders() {
            when(repository.findByUserIdAndTransactionIdStartingWith("user-1", BASE))
                    .thenReturn(
                            List.of(
                                    row(BASE, T0.plusMinutes(5), T0),
                                    row(BASE + "..1", T0.plusMinutes(10), T0.plusMinutes(5))));
            for (int n = 2; n <= 5; n++) {
                when(verificationService.fetchGoogleOrder(BASE + ".." + n))
                        .thenReturn(Optional.of(order(n)));
            }

            int written =
                    sync.syncRenewals(
                            sub, BASE + "..5", false, T0.plusMinutes(30), T0.plusHours(1), false);

            assertThat(written).isEqualTo(4);
            ArgumentCaptor<SubscriptionPaymentHistory> saved =
                    ArgumentCaptor.forClass(SubscriptionPaymentHistory.class);
            verify(repository, times(4)).save(saved.capture());
            List<SubscriptionPaymentHistory> rows = saved.getAllValues();
            assertThat(rows)
                    .extracting(SubscriptionPaymentHistory::getTransactionId)
                    .containsExactly(BASE + "..2", BASE + "..3", BASE + "..4", BASE + "..5");
            // 결제 시각 = 주문 createTime, 만료 = servicePeriodEnd, 금액 = 주문 total (STORE)
            assertThat(rows.get(0).getOccurredAt()).isEqualTo(T0.plusMinutes(10));
            assertThat(rows.get(0).getExpiresAt()).isEqualTo(T0.plusMinutes(15));
            assertThat(rows.get(0).getPriceSource()).isEqualTo(PriceSource.STORE);
            assertThat(rows.get(0).getPriceAmount()).isEqualByComparingTo("4900.00");
            assertThat(rows.get(0).getTrial()).isFalse();
            // LUT-666: 실결제 구독이면 백필 행도 테스트 결제 아님
            assertThat(rows)
                    .extracting(SubscriptionPaymentHistory::getTestPurchase)
                    .containsOnly(false);
            verify(verificationService, never()).fetchGoogleOrder(BASE + "..1");
            verify(recorder, never())
                    .record(any(), any(), anyBoolean(), any(), any(), any(), any(), any());
            verify(metrics).ledgerRowsWritten("rtdn", 4);
        }

        @Test
        @DisplayName("LUT-666: 라이선스 테스터 구독이면 백필하는 행도 테스트 결제로 저장한다")
        void backfillCopiesTestPurchase() {
            sub.setTestPurchase(true);
            when(repository.findByUserIdAndTransactionIdStartingWith("user-1", BASE))
                    .thenReturn(List.of(row(BASE, T0.plusMinutes(5), T0)));
            when(verificationService.fetchGoogleOrder(BASE + "..1"))
                    .thenReturn(Optional.of(order(1)));

            int written =
                    sync.syncRenewals(
                            sub, BASE + "..1", false, T0.plusMinutes(10), T0.plusHours(1), false);

            assertThat(written).isEqualTo(1);
            ArgumentCaptor<SubscriptionPaymentHistory> saved =
                    ArgumentCaptor.forClass(SubscriptionPaymentHistory.class);
            verify(repository).save(saved.capture());
            assertThat(saved.getValue().getTestPurchase()).isTrue();
        }

        @Test
        @DisplayName("중간 주문 조회가 일시 실패하면 건너뛰고(다음 동기화 때 재시도), 최신 주문은 조회 실패 시 폴백 시각으로라도 기록한다")
        void transientFailureSkipsMiddleAndFallsBackForLatest() {
            when(repository.findByUserIdAndTransactionIdStartingWith("user-1", BASE))
                    .thenReturn(List.of(row(BASE + "..1", T0.plusMinutes(10), T0.plusMinutes(5))));
            when(verificationService.fetchGoogleOrder(BASE + "..2"))
                    .thenThrow(new StoreTransientException("503", null));
            when(verificationService.fetchGoogleOrder(BASE + "..3"))
                    .thenThrow(new StoreTransientException("503", null));
            LocalDateTime eventTime = T0.plusMinutes(16);

            int written =
                    sync.syncRenewals(
                            sub, BASE + "..3", false, T0.plusMinutes(20), eventTime, false);

            assertThat(written).isEqualTo(1);
            verify(repository, never()).save(any());
            verify(recorder)
                    .record(
                            eq(sub),
                            eq(SubscriptionPaymentEventType.RENEWAL),
                            eq(false),
                            eq(null),
                            eq(null),
                            eq(BASE + "..3"),
                            eq(T0.plusMinutes(20)),
                            eq(eventTime));
            verify(metrics, times(2)).orderLookupFailed("transient");
        }

        @Test
        @DisplayName("주문이 404 면 중간은 건너뛰고 최신은 폴백 기록, 거부(4xx)도 건너뛴다")
        void notFoundAndRejectedAreCounted() {
            when(repository.findByUserIdAndTransactionIdStartingWith("user-1", BASE))
                    .thenReturn(List.of());
            when(verificationService.fetchGoogleOrder(BASE + "..1")).thenReturn(Optional.empty());
            when(verificationService.fetchGoogleOrder(BASE + "..2"))
                    .thenThrow(new CustomException("120702", "error.iap.verification_failed"));
            when(verificationService.fetchGoogleOrder(BASE + "..3")).thenReturn(Optional.empty());

            int written =
                    sync.syncRenewals(sub, BASE + "..3", false, T0.plusMinutes(20), null, false);

            assertThat(written).isEqualTo(1);
            verify(recorder)
                    .record(
                            any(),
                            eq(SubscriptionPaymentEventType.RENEWAL),
                            eq(false),
                            eq(null),
                            eq(null),
                            eq(BASE + "..3"),
                            eq(T0.plusMinutes(20)),
                            any());
            verify(metrics, times(2)).orderLookupFailed("not_found");
            verify(metrics).orderLookupFailed("rejected");
        }

        @Test
        @DisplayName("최신 주문이 이미 기록돼 있으면 아무것도 쓰지 않는다 (verify 와 RTDN 이중 도착)")
        void latestAlreadyRecordedIsNoop() {
            when(repository.findByUserIdAndTransactionIdStartingWith("user-1", BASE))
                    .thenReturn(List.of(row(BASE + "..2", T0.plusMinutes(15), T0.plusMinutes(10))));

            int written =
                    sync.syncRenewals(sub, BASE + "..2", false, T0.plusMinutes(15), null, false);

            assertThat(written).isZero();
            verify(verificationService, never()).fetchGoogleOrder(anyString());
            verify(recorder, never())
                    .record(any(), any(), anyBoolean(), any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("되짚는 범위는 최대 MAX_BACKFILL 건으로 제한한다")
        void backfillWindowIsBounded() {
            when(repository.findByUserIdAndTransactionIdStartingWith("user-1", BASE))
                    .thenReturn(List.of());
            when(verificationService.fetchGoogleOrder(anyString()))
                    .thenAnswer(
                            inv ->
                                    Optional.of(
                                            order(
                                                    GoogleOrderLedgerSync.suffixOf(
                                                            inv.getArgument(0)))));

            sync.syncRenewals(sub, BASE + "..100", false, T0.plusMinutes(505), null, false);

            verify(verificationService, times(GoogleOrderLedgerSync.MAX_BACKFILL))
                    .fetchGoogleOrder(anyString());
            verify(verificationService, never()).fetchGoogleOrder(BASE + "..40");
            verify(verificationService).fetchGoogleOrder(BASE + "..41");
        }

        @Test
        @DisplayName("주문에 이용기간 종료가 없고 최신도 아니면 건너뛴다")
        void orderWithoutPeriodEndSkipped() {
            when(repository.findByUserIdAndTransactionIdStartingWith("user-1", BASE))
                    .thenReturn(List.of());
            when(verificationService.fetchGoogleOrder(BASE + "..1"))
                    .thenReturn(
                            Optional.of(
                                    new GoogleOrder(
                                            BASE + "..1",
                                            "PROCESSED",
                                            T0,
                                            null,
                                            null,
                                            null,
                                            null)));
            when(verificationService.fetchGoogleOrder(BASE + "..2"))
                    .thenReturn(Optional.of(order(2)));

            int written =
                    sync.syncRenewals(sub, BASE + "..2", false, T0.plusMinutes(15), null, false);

            assertThat(written).isEqualTo(1);
            verify(repository, times(1)).save(any());
        }
    }

    @Nested
    @DisplayName("수동 정합 (full=true) — 1번부터 전부 대조·정정")
    class Full {

        @Test
        @DisplayName("같은 주문 ID 행은 시각·만료·금액을 주문 기준으로 정정한다")
        void correctsRowWithSameOrderId() {
            // 자가 치유가 36분 늦게 기록한 ..5: occurred_at 이 처리 시점, 만료는 맞음
            SubscriptionPaymentHistory stale =
                    row(BASE + "..5", T0.plusMinutes(30), T0.plusMinutes(71));
            when(repository.findByUserIdAndTransactionIdStartingWith("user-1", BASE))
                    .thenReturn(List.of(stale));
            when(repository.findByUserIdAndEventTypeAndTransactionId(
                            "user-1", SubscriptionPaymentEventType.RENEWAL, BASE + "..5"))
                    .thenReturn(List.of(stale));
            for (int n = 1; n <= 5; n++) {
                when(verificationService.fetchGoogleOrder(BASE + ".." + n))
                        .thenReturn(Optional.of(order(n)));
            }

            int written =
                    sync.syncRenewals(sub, BASE + "..5", false, T0.plusMinutes(30), null, true);

            // ..1~..4 삽입 + ..5 정정
            assertThat(written).isEqualTo(5);
            verify(repository, times(4)).save(any());
            assertThat(stale.getOccurredAt()).isEqualTo(T0.plusMinutes(25));
            assertThat(stale.getPriceSource()).isEqualTo(PriceSource.STORE);
            verify(metrics).ledgerRowsWritten("reconcile", 5);
        }

        @Test
        @DisplayName("같은 주문 ID 행이 이미 주문과 같으면 바꾸지 않는다")
        void identicalRowNotTouched() {
            SubscriptionPaymentHistory exact =
                    SubscriptionPaymentHistory.builder()
                            .userId("user-1")
                            .platform("android")
                            .productId("membership")
                            .plan(SubscriptionPlan.MONTHLY)
                            .eventType(SubscriptionPaymentEventType.RENEWAL)
                            .trial(false)
                            .priceAmount(new BigDecimal("4900.00"))
                            .priceCurrency("KRW")
                            .priceSource(PriceSource.STORE)
                            .transactionId(BASE + "..1")
                            .expiresAt(T0.plusMinutes(10))
                            .occurredAt(T0.plusMinutes(5))
                            .build();
            when(repository.findByUserIdAndTransactionIdStartingWith("user-1", BASE))
                    .thenReturn(List.of(exact));
            when(repository.findByUserIdAndEventTypeAndTransactionId(
                            "user-1", SubscriptionPaymentEventType.RENEWAL, BASE + "..1"))
                    .thenReturn(List.of(exact));
            when(verificationService.fetchGoogleOrder(BASE + "..1"))
                    .thenReturn(Optional.of(order(1)));

            int written =
                    sync.syncRenewals(sub, BASE + "..1", false, T0.plusMinutes(10), null, true);

            assertThat(written).isZero();
            verify(repository, never()).save(any());
            verify(repository, never()).flush();
        }

        @Test
        @DisplayName("만료를 옮기면 다른 행과 uk 가 겹칠 때는 만료는 두고 시각·금액만 정정한다")
        void expiryMoveAvoidsUniqueConflict() {
            SubscriptionPaymentHistory mislabeled =
                    row(BASE + "..1", T0.plusMinutes(15), T0.plusMinutes(12));
            SubscriptionPaymentHistory other =
                    row(BASE + "..2", T0.plusMinutes(10), T0.plusMinutes(5));
            when(repository.findByUserIdAndTransactionIdStartingWith("user-1", BASE))
                    .thenReturn(List.of(mislabeled, other));
            when(repository.findByUserIdAndEventTypeAndTransactionId(
                            "user-1", SubscriptionPaymentEventType.RENEWAL, BASE + "..1"))
                    .thenReturn(List.of(mislabeled));
            // ..1 의 진짜 만료(T0+10)에는 이미 다른 행이 있다
            when(repository.findByUserIdAndEventTypeAndExpiresAt(
                            "user-1", SubscriptionPaymentEventType.RENEWAL, T0.plusMinutes(10)))
                    .thenReturn(Optional.of(other));
            when(verificationService.fetchGoogleOrder(BASE + "..1"))
                    .thenReturn(Optional.of(order(1)));

            sync.syncRenewals(sub, BASE + "..1", false, T0.plusMinutes(15), null, true);

            assertThat(mislabeled.getExpiresAt()).isEqualTo(T0.plusMinutes(15));
            assertThat(mislabeled.getOccurredAt()).isEqualTo(T0.plusMinutes(5));
        }

        @Test
        @DisplayName("같은 만료의 행이 다른 주문 ID 로 있으면 그 행을 이 주문으로 바로잡는다 (최신 ID 가 잘못 붙은 경우)")
        void relabelsRowWithSameExpiry() {
            // 자가 치유가 ..5 를 붙여 둔 행인데 실제 이 기간의 주문은 ..3
            SubscriptionPaymentHistory wrongId =
                    row(BASE + "..5", T0.plusMinutes(20), T0.plusMinutes(40));
            when(repository.findByUserIdAndTransactionIdStartingWith("user-1", BASE))
                    .thenReturn(List.of(wrongId));
            when(repository.findByUserIdAndEventTypeAndExpiresAt(
                            "user-1", SubscriptionPaymentEventType.RENEWAL, T0.plusMinutes(20)))
                    .thenReturn(Optional.of(wrongId));
            when(verificationService.fetchGoogleOrder(BASE + "..3"))
                    .thenReturn(Optional.of(order(3)));
            when(verificationService.fetchGoogleOrder(BASE + "..1")).thenReturn(Optional.empty());
            when(verificationService.fetchGoogleOrder(BASE + "..2")).thenReturn(Optional.empty());

            int written =
                    sync.syncRenewals(sub, BASE + "..3", false, T0.plusMinutes(20), null, true);

            assertThat(written).isEqualTo(1);
            assertThat(wrongId.getTransactionId()).isEqualTo(BASE + "..3");
            assertThat(wrongId.getOccurredAt()).isEqualTo(T0.plusMinutes(15));
            verify(repository, never()).save(any());
        }
    }

    @Test
    @DisplayName("suffixOf: GPA.xxx..7 → 7, GPA.xxx → 0, 형식 외 → null")
    void suffixOf() {
        assertThat(GoogleOrderLedgerSync.suffixOf(BASE + "..7")).isEqualTo(7);
        assertThat(GoogleOrderLedgerSync.suffixOf(BASE)).isZero();
        assertThat(GoogleOrderLedgerSync.suffixOf("")).isNull();
    }
}
