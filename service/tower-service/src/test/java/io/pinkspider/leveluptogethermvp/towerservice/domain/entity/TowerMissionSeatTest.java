package io.pinkspider.leveluptogethermvp.towerservice.domain.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TowerMissionSeatTest {

    @Test
    @DisplayName("열린 기록은 한 번만 닫힌다 (처음 닫힌 시각 유지)")
    void closeOnce() {
        TowerMissionSeat seat =
                TowerMissionSeat.open("u1", 10L, 20L, "REGULAR", 1, 9L, false, LocalDateTime.now());
        assertThat(seat.isOpen()).isTrue();
        assertThat(seat.isAutoSeated()).isFalse();
        assertThat(seat.getCheerCount()).isZero();

        LocalDateTime first = LocalDateTime.of(2026, 11, 3, 12, 0);
        seat.close(first);
        seat.close(first.plusHours(1));
        assertThat(seat.isOpen()).isFalse();
        assertThat(seat.getEndedAt()).isEqualTo(first);
    }
}
