package io.pinkspider.leveluptogethermvp.towerservice.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.leveluptogethermvp.towerservice.infrastructure.FloorPopulation;
import io.pinkspider.leveluptogethermvp.towerservice.infrastructure.TowerOccupantRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** LUT-630: 층 전체 인원 — 모든 인스턴스 합(종류 무관), 30초 메모 */
class TowerPopulationServiceTest {

    private final TowerOccupantRepository repository = mock(TowerOccupantRepository.class);

    @Test
    @DisplayName("층별 합계를 돌려주고 없는 층은 0 · 메모 창 안에서는 DB 를 다시 읽지 않는다")
    void memo() {
        when(repository.populationByFloor())
                .thenReturn(List.of(new FloorPopulation(1, 5L), new FloorPopulation(2, 3L)));
        TowerPopulationService service = new TowerPopulationService(repository, 30);

        assertThat(service.of(1)).isEqualTo(5L);
        assertThat(service.of(2)).isEqualTo(3L);
        assertThat(service.of(3)).isZero();
        assertThat(service.byFloor()).containsEntry(1, 5L);
        verify(repository, times(1)).populationByFloor();

        service.byFloorFresh();
        verify(repository, times(2)).populationByFloor();
    }

    @Test
    @DisplayName("메모 0초면 매번 읽는다")
    void noMemo() {
        when(repository.populationByFloor()).thenReturn(List.of());
        TowerPopulationService service = new TowerPopulationService(repository, 0);
        service.of(1);
        service.of(1);
        verify(repository, times(2)).populationByFloor();
    }
}
