package io.pinkspider.leveluptogethermvp.towerservice.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.pinkspider.global.facade.dto.TowerMissionSeatDto;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerMissionSeat;
import io.pinkspider.leveluptogethermvp.towerservice.infrastructure.TowerFloorVisitRepository;
import io.pinkspider.leveluptogethermvp.towerservice.infrastructure.TowerMissionSeatRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class TowerQueryFacadeServiceTest {

    private final TowerMissionSeatRepository repository = mock(TowerMissionSeatRepository.class);
    private final TowerFloorVisitRepository visitRepository = mock(TowerFloorVisitRepository.class);
    private final TowerQueryFacadeService facade =
            new TowerQueryFacadeService(repository, visitRepository);

    @Test
    @DisplayName("수행 한 건의 자리 기록을 층·자동 착석·응원 수로 돌려준다 · 없거나 키가 비면 empty")
    void findMissionSeat() {
        TowerMissionSeat seat =
                TowerMissionSeat.open("u1", 10L, 20L, "REGULAR", 2, 9L, true, LocalDateTime.now());
        ReflectionTestUtils.setField(seat, "cheerCount", 3);
        when(repository.findFirstByUserIdAndExecutionKindAndExecutionIdOrderBySeatedAtDesc(
                        "u1", "REGULAR", 20L))
                .thenReturn(Optional.of(seat));

        assertThat(facade.findMissionSeat("u1", 10L, 20L, "REGULAR"))
                .contains(new TowerMissionSeatDto(2, true, 3));
        assertThat(facade.findMissionSeat("u1", 10L, 21L, "REGULAR")).isEmpty();
        assertThat(facade.findMissionSeat(null, 10L, 20L, "REGULAR")).isEmpty();
        assertThat(facade.findMissionSeat("u1", 10L, null, "REGULAR")).isEmpty();
        assertThat(facade.findMissionSeat("u1", 10L, 20L, null)).isEmpty();
    }

    @Test
    @DisplayName("LUT-634: 첫 입장 층 수·가장 높은 층 — userId 없으면 0")
    void visitedFloors() {
        when(visitRepository.countByUserId("u1")).thenReturn(3L);
        when(visitRepository.findHighestFloorNumber("u1")).thenReturn(5);
        assertThat(facade.countVisitedFloors("u1")).isEqualTo(3);
        assertThat(facade.highestVisitedFloor("u1")).isEqualTo(5);
        assertThat(facade.countVisitedFloors(null)).isZero();
        assertThat(facade.highestVisitedFloor(null)).isZero();
    }
}
