package io.pinkspider.leveluptogethermvp.towerservice.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerTraceView;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerMissionSeat;
import io.pinkspider.leveluptogethermvp.towerservice.infrastructure.TowerMissionSeatRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

class TowerTraceServiceTest {

    private final TowerMissionSeatRepository repository = mock(TowerMissionSeatRepository.class);
    private final TowerTraceService service = new TowerTraceService(repository);

    private static TowerMissionSeat completedAt(int x, int y) {
        TowerMissionSeat seat =
                TowerMissionSeat.open("u", 1L, 1L, "REGULAR", 2, 9L, false, LocalDateTime.now());
        seat.complete(LocalDateTime.now(), x, y);
        return seat;
    }

    @Test
    @DisplayName("KST 하루 = UTC [전날 15:00, 당일 15:00) 로 사람 수와 자리(같은 자리는 한 번)를 센다")
    void on() {
        LocalDateTime from = LocalDateTime.of(2026, 11, 3, 15, 0);
        LocalDateTime to = LocalDateTime.of(2026, 11, 4, 15, 0);
        when(repository.countCompletedUsers(2, from, to)).thenReturn(3L);
        when(repository.findCompletedWithSeat(eq(2), eq(from), eq(to), any(Pageable.class)))
                .thenReturn(List.of(completedAt(1, 2), completedAt(1, 2), completedAt(3, 4)));

        TowerTraceView view = service.on(2, LocalDate.of(2026, 11, 4));

        assertThat(view.completedToday()).isEqualTo(3);
        assertThat(view.seats())
                .containsExactly(new TowerTraceView.Point(1, 2), new TowerTraceView.Point(3, 4));
    }

    @Test
    @DisplayName("오늘 기준 조회 · 실패해도 빈 흔적(입장 스냅샷은 내려간다)")
    void todayAndFailure() {
        when(repository.countCompletedUsers(eq(1), any(), any())).thenReturn(0L);
        assertThat(service.today(1).completedToday()).isZero();

        when(repository.countCompletedUsers(anyInt(), any(), any()))
                .thenThrow(new IllegalStateException("db"));
        assertThat(service.today(1)).isEqualTo(TowerTraceView.EMPTY);
    }
}
