package io.pinkspider.leveluptogethermvp.towerservice.presence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TowerJumpRateLimiterTest {

    @Test
    @DisplayName("LUT-869: 직전에 받아들인 점프로부터 최소 간격 안이면 버린다 · 간격이 지나면 다시 · 버린 요청은 기준 시각을 밀지 않는다")
    void allow() {
        AtomicLong now = new AtomicLong(10_000);
        TowerJumpRateLimiter limiter = new TowerJumpRateLimiter(400, now::get);
        assertThat(limiter.allow("s1")).isTrue();
        assertThat(limiter.allow("s1")).isFalse(); // 같은 밀리초의 두 번째 요청
        now.addAndGet(399);
        assertThat(limiter.allow("s1")).isFalse();
        now.addAndGet(1); // 받아들인 점프(10,000)로부터 400ms — 399ms 때 버린 요청이 기준이 아니다
        assertThat(limiter.allow("s1")).isTrue();
        assertThat(limiter.allow("s1")).isFalse();
    }

    @Test
    @DisplayName("LUT-869: 세션마다 따로 센다 · 세션 ID 없거나 간격 0 이하면 제한 없음 · 끊기면 지운다")
    void perSession() {
        AtomicLong now = new AtomicLong(10_000);
        TowerJumpRateLimiter limiter = new TowerJumpRateLimiter(400, now::get);
        assertThat(limiter.allow("s1")).isTrue();
        assertThat(limiter.allow("s2")).isTrue();
        assertThat(limiter.allow(null)).isTrue();
        assertThat(limiter.allow(null)).isTrue();
        assertThat(limiter.tracked()).isEqualTo(2);
        limiter.forget("s1");
        limiter.forget(null);
        assertThat(limiter.tracked()).isEqualTo(1);
        assertThat(limiter.allow("s1")).isTrue(); // 지운 뒤 다시 붙은 세션은 처음부터
        TowerJumpRateLimiter off = new TowerJumpRateLimiter(0, now::get);
        assertThat(off.allow("s3")).isTrue();
        assertThat(off.allow("s3")).isTrue();
        assertThat(new TowerJumpRateLimiter(400).allow("s4")).isTrue();
    }
}
