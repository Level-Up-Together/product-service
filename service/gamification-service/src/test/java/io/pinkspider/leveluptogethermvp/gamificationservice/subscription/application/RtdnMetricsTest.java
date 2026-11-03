package io.pinkspider.leveluptogethermvp.gamificationservice.subscription.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

@DisplayName("RtdnMetrics 테스트 (LUT-617)")
class RtdnMetricsTest {

    @SuppressWarnings("unchecked")
    private static RtdnMetrics with(MeterRegistry registry) {
        ObjectProvider<MeterRegistry> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(registry);
        return new RtdnMetrics(provider);
    }

    @Test
    @DisplayName("수신·폐기·주문 조회 실패·원장 기록 카운터를 라벨별로 센다")
    void countersIncrement() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        RtdnMetrics metrics = with(registry);

        metrics.received("subscription-2");
        metrics.received("subscription-2");
        metrics.received("test");
        metrics.dropped("empty");
        metrics.orderLookupFailed("transient");
        metrics.ledgerRowsWritten("rtdn", 3);
        metrics.ledgerRowsWritten("rtdn", 0); // 0 은 기록하지 않음

        assertThat(
                        registry.get(RtdnMetrics.RECEIVED)
                                .tag("kind", "subscription-2")
                                .counter()
                                .count())
                .isEqualTo(2.0);
        assertThat(registry.get(RtdnMetrics.RECEIVED).tag("kind", "test").counter().count())
                .isEqualTo(1.0);
        assertThat(registry.get(RtdnMetrics.DROPPED).tag("reason", "empty").counter().count())
                .isEqualTo(1.0);
        assertThat(
                        registry.get(RtdnMetrics.ORDER_LOOKUP_FAILED)
                                .tag("reason", "transient")
                                .counter()
                                .count())
                .isEqualTo(1.0);
        assertThat(
                        registry.get(RtdnMetrics.LEDGER_ROWS_WRITTEN)
                                .tag("source", "rtdn")
                                .counter()
                                .count())
                .isEqualTo(3.0);
    }

    @Test
    @DisplayName("MeterRegistry 가 없으면 아무것도 하지 않는다 (슬라이스 테스트 컨텍스트)")
    void noRegistryIsNoop() {
        RtdnMetrics metrics = with(null);

        assertThatCode(
                        () -> {
                            metrics.received("test");
                            metrics.dropped("empty");
                            metrics.orderLookupFailed("not_found");
                            metrics.ledgerRowsWritten("reconcile", 2);
                        })
                .doesNotThrowAnyException();
    }
}
