package io.pinkspider.leveluptogethermvp.gamificationservice.subscription.application;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * LUT-617: Google RTDN 수신·폐기·원장 백필 카운터 — {@code /showmethemoney/prometheus} 로 노출돼 Grafana 에서 "RTDN
 * 이 끊겼는지" 를 본다.
 *
 * <p>2026-10-26 dev 의 Pub/Sub push 구독이 31일 비활성 만료로 조용히 삭제돼 갱신 알림이 전부 유실됐다. 수신 자체가 0 이 되는 장애는 애플리케이션
 * 로그로는 보이지 않으므로 카운터로 남긴다. 라벨 값은 고정 집합(알림 종류·폐기 사유)만 써 시리즈 수를 묶는다(Grafana Cloud Free 1만 한도).
 *
 * <p>MeterRegistry 가 없는 컨텍스트(슬라이스 테스트)에서는 no-op.
 */
@Component
public class RtdnMetrics {

    static final String RECEIVED = "lut.rtdn.received";
    static final String DROPPED = "lut.rtdn.dropped";
    static final String ORDER_LOOKUP_FAILED = "lut.google.order.lookup.failed";
    static final String LEDGER_ROWS_WRITTEN = "lut.subscription.ledger.rows.written";

    private final MeterRegistry registry;

    public RtdnMetrics(ObjectProvider<MeterRegistry> registryProvider) {
        this.registry = registryProvider.getIfAvailable();
    }

    /** RTDN 1건 수신 — kind: test / voided / subscription-{type} / other */
    public void received(String kind) {
        increment(RECEIVED, "kind", kind, 1);
    }

    /** RTDN 을 처리하지 않고 버림 — reason: empty / unparseable / not_subscription / refetch_rejected */
    public void dropped(String reason) {
        increment(DROPPED, "reason", reason, 1);
    }

    /** orders.get 실패 — reason: transient / rejected / not_found */
    public void orderLookupFailed(String reason) {
        increment(ORDER_LOOKUP_FAILED, "reason", reason, 1);
    }

    /** 원장에 새로 쓰거나 정정한 행 수 — source: rtdn / reconcile */
    public void ledgerRowsWritten(String source, int count) {
        if (count > 0) {
            increment(LEDGER_ROWS_WRITTEN, "source", source, count);
        }
    }

    private void increment(String name, String tagKey, String tagValue, int amount) {
        if (registry == null) {
            return;
        }
        Counter.builder(name).tag(tagKey, tagValue).register(registry).increment(amount);
    }
}
