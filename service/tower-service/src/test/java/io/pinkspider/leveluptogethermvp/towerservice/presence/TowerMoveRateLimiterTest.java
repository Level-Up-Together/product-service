package io.pinkspider.leveluptogethermvp.towerservice.presence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TowerMoveRateLimiterTest {

    @Test
    @DisplayName("LUT-705: 1초 창마다 상한까지 · 다음 초엔 다시 · 세션 ID 없거나 상한 0 이면 제한 없음 · 끊기면 지운다")
    void allow() {
        AtomicLong now = new AtomicLong(10_000);
        TowerMoveRateLimiter limiter = new TowerMoveRateLimiter(2, now::get);
        assertThat(limiter.allow("s1")).isTrue();
        assertThat(limiter.allow("s1")).isTrue();
        assertThat(limiter.allow("s1")).isFalse();
        now.addAndGet(1000);
        assertThat(limiter.allow("s1")).isTrue();
        assertThat(limiter.allow(null)).isTrue();
        assertThat(limiter.tracked()).isEqualTo(1);
        limiter.forget("s1");
        limiter.forget(null);
        assertThat(limiter.tracked()).isZero();
        assertThat(new TowerMoveRateLimiter(0, now::get).allow("s1")).isTrue();
        assertThat(new TowerMoveRateLimiter(5).allow("s2")).isTrue();
    }
}
