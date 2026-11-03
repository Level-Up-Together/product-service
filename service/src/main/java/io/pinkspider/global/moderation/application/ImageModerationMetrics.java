package io.pinkspider.global.moderation.application;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * LUT-650: 이미지 검수 결과 카운터 — {@code /showmethemoney/prometheus} 로 노출돼 "검수가 조용히 무력화됐는지" 를 본다.
 *
 * <p>검수 실패 시 통과(fail-open)는 로그만으로는 보이지 않으므로 {@code lut_image_moderation_fail_open_total} 이 0 이 아니면
 * 제공자 상태(모델 파일·포맷·API)를 점검한다. 라벨 값은 고정 집합(제공자·결과·사유)만 써 시리즈 수를 묶는다(Grafana Cloud Free 1만 한도).
 *
 * <p>MeterRegistry 가 없는 컨텍스트(슬라이스 테스트)에서는 no-op.
 */
@Component
public class ImageModerationMetrics {

    static final String CHECKED = "lut.image.moderation.checked";
    static final String FAIL_OPEN = "lut.image.moderation.fail_open";

    public static final String RESULT_SAFE = "safe";
    public static final String RESULT_BLOCKED = "blocked";

    private final MeterRegistry registry;

    public ImageModerationMetrics(ObjectProvider<MeterRegistry> registryProvider) {
        this.registry = registryProvider.getIfAvailable();
    }

    /** 판정이 난 검수 1건 — result: safe / blocked */
    public void checked(String provider, String result) {
        increment(CHECKED, provider, "result", result);
    }

    /**
     * 검수 실패 1건 — reason: unreadable / read_error / inference_error / exception (통과·차단 정책과 무관하게 센다)
     */
    public void failOpen(String provider, String reason) {
        increment(FAIL_OPEN, provider, "reason", reason);
    }

    private void increment(String name, String provider, String tagKey, String tagValue) {
        if (registry == null) {
            return;
        }
        Counter.builder(name)
                .tag("provider", provider == null ? "unknown" : provider)
                .tag(tagKey, tagValue == null ? "unknown" : tagValue)
                .register(registry)
                .increment();
    }
}
