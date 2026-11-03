package io.pinkspider.leveluptogethermvp.towerservice.application;

import static io.pinkspider.leveluptogethermvp.towerservice.TowerTestFixtures.floor;
import static io.pinkspider.leveluptogethermvp.towerservice.TowerTestFixtures.instance;
import static io.pinkspider.leveluptogethermvp.towerservice.TowerTestFixtures.occupant;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerFloor;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerInstance;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerOccupant;
import io.pinkspider.leveluptogethermvp.towerservice.domain.enums.TowerOccupantKind;
import io.pinkspider.leveluptogethermvp.towerservice.infrastructure.InstanceLoad;
import io.pinkspider.leveluptogethermvp.towerservice.infrastructure.TowerInstanceRepository;
import io.pinkspider.leveluptogethermvp.towerservice.infrastructure.TowerOccupantRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

/** LUT-630: 방 고르기 규칙 — 직접 입장은 "남은 방 중 가장 많은 곳", 자동 착석은 "가장 적은 곳", 서버는 합치거나 옮기지 않는다 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TowerInstanceAllocatorTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 3, 12, 0);

    @Mock private TowerInstanceRepository instanceRepository;
    @Mock private TowerOccupantRepository occupantRepository;

    @Mock
    private io.pinkspider.leveluptogethermvp.towerservice.presence.TowerPresenceService
            presenceService;

    @InjectMocks private TowerInstanceAllocator allocator;

    private final TowerFloor floor = floor(1L, 1, null, true, true); // 상한 30

    @BeforeEach
    void setUp() {
        when(instanceRepository.save(any(TowerInstance.class)))
                .thenAnswer(
                        inv -> {
                            TowerInstance i = inv.getArgument(0);
                            ReflectionTestUtils.setField(i, "id", 100L);
                            return i;
                        });
    }

    /** 인스턴스 id 와 (정원 인원, 전체 인원) 쌍으로 층 상태를 꾸민다 */
    private void rooms(long[][] idCountedTotal) {
        List<TowerInstance> instances =
                java.util.Arrays.stream(idCountedTotal).map(r -> instance(r[0], 1)).toList();
        List<InstanceLoad> loads =
                java.util.Arrays.stream(idCountedTotal)
                        .map(r -> new InstanceLoad(r[0], r[1], r[2]))
                        .toList();
        when(instanceRepository.findAllByFloorNumberOrderByIdAsc(1)).thenReturn(instances);
        when(occupantRepository.loadsByFloor(1, TowerOccupantKind.AUTO_SEATED)).thenReturn(loads);
    }

    @Test
    @DisplayName("31번째 입장자는 새 방에 배정되고, 그 뒤 입장자는 자리가 남은 방 중 사람이 가장 많은 곳으로 간다")
    void directEntry() {
        rooms(new long[][] {{1, 30, 30}});
        assertThat(allocator.pickForDirectEntry(floor, NOW).getId()).isEqualTo(100L);
        verify(instanceRepository).save(any(TowerInstance.class));

        rooms(new long[][] {{1, 30, 30}, {2, 1, 1}});
        assertThat(allocator.pickForDirectEntry(floor, NOW).getId()).isEqualTo(2L);

        rooms(new long[][] {{1, 20, 20}, {2, 5, 5}});
        assertThat(allocator.pickForDirectEntry(floor, NOW).getId()).isEqualTo(1L);

        rooms(new long[][] {{1, 20, 20}, {2, 20, 20}});
        assertThat(allocator.pickForDirectEntry(floor, NOW).getId()).isEqualTo(1L); // 같으면 먼저 연 방
    }

    @Test
    @DisplayName("방이 하나도 없으면 새 방 · 자동 착석자만 있는 방도 열린 방이다(정원 0 이라 직접 입장자가 들어간다)")
    void emptyFloorAndAutoOnlyRoom() {
        rooms(new long[][] {});
        assertThat(allocator.pickForDirectEntry(floor, NOW).getId()).isEqualTo(100L);

        rooms(new long[][] {{1, 0, 2}});
        assertThat(allocator.pickForDirectEntry(floor, NOW).getId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("자동 착석: 전체 인원이 상한 미만인 방 중 사람이 가장 적은 곳, 제외 방은 건너뛰고, 전부 차면 새 방")
    void autoSeat() {
        rooms(new long[][] {{1, 10, 10}, {2, 3, 3}});
        assertThat(allocator.pickForAutoSeat(floor, null, NOW).getId()).isEqualTo(2L);
        assertThat(allocator.pickForAutoSeat(floor, 2L, NOW).getId()).isEqualTo(1L);

        rooms(new long[][] {{1, 0, 30}}); // 자동 착석자만 30명 — 전체 인원 기준이라 찼다
        assertThat(allocator.pickForAutoSeat(floor, null, NOW).getId()).isEqualTo(100L);
    }

    @Test
    @DisplayName("점유자를 지우면 방이 비었을 때만 닫는다")
    void removeAndClose() {
        TowerOccupant o = occupant(5L, 1L, 1, "u", TowerOccupantKind.DIRECT, false);
        when(occupantRepository.countByInstanceId(1L)).thenReturn(0L);
        allocator.remove(o);
        verify(presenceService).unregister(o); // LUT-631: presence·세션 정리 + LEAVE
        verify(occupantRepository).delete(o);
        verify(instanceRepository).deleteById(1L);

        when(occupantRepository.countByInstanceId(2L)).thenReturn(3L);
        allocator.closeIfEmpty(2L);
        verify(instanceRepository, never()).deleteById(2L);
    }

    @Test
    @DisplayName("직접 입장자 29명 + 자동 착석자 1명인 방에 직접 입장자가 오면 자동 착석자가 다른 방으로 옮겨진다 (없으면 새 방)")
    void relocateAutoSeatedOverflow() {
        TowerOccupant auto = occupant(5L, 1L, 1, "auto", TowerOccupantKind.AUTO_SEATED, true);
        when(occupantRepository.countByInstanceId(1L)).thenReturn(31L, 30L);
        when(occupantRepository.findAllByInstanceIdAndKindOrderByEnteredAtDesc(
                        1L, TowerOccupantKind.AUTO_SEATED))
                .thenReturn(List.of(auto));
        rooms(new long[][] {{1, 30, 31}, {2, 5, 5}});

        allocator.relocateAutoSeatedOverflow(floor, 1L, NOW);
        assertThat(auto.getInstanceId()).isEqualTo(2L);
        verify(presenceService).moveInstance(auto, 1L, 2L); // LUT-631: 옛 방 LEAVE + 새 방 ENTER

        TowerOccupant auto2 = occupant(6L, 1L, 1, "auto2", TowerOccupantKind.AUTO_SEATED, true);
        when(occupantRepository.countByInstanceId(1L)).thenReturn(31L, 30L);
        when(occupantRepository.findAllByInstanceIdAndKindOrderByEnteredAtDesc(
                        1L, TowerOccupantKind.AUTO_SEATED))
                .thenReturn(List.of(auto2));
        rooms(new long[][] {{1, 30, 31}});
        allocator.relocateAutoSeatedOverflow(floor, 1L, NOW);
        assertThat(auto2.getInstanceId()).isEqualTo(100L);
    }

    @Test
    @DisplayName("상한을 넘지 않거나 자동 착석자가 없으면 아무도 옮기지 않는다")
    void relocateNoop() {
        when(occupantRepository.countByInstanceId(1L)).thenReturn(30L);
        allocator.relocateAutoSeatedOverflow(floor, 1L, NOW);
        verify(occupantRepository, never())
                .findAllByInstanceIdAndKindOrderByEnteredAtDesc(anyLong(), any());

        when(occupantRepository.countByInstanceId(1L)).thenReturn(31L);
        when(occupantRepository.findAllByInstanceIdAndKindOrderByEnteredAtDesc(
                        eq(1L), eq(TowerOccupantKind.AUTO_SEATED)))
                .thenReturn(List.of());
        allocator.relocateAutoSeatedOverflow(floor, 1L, NOW);
        verify(instanceRepository, never()).save(any());
    }

    @Test
    @DisplayName("인원이 줄어든 방이 있어도 서버는 유저를 옮기거나 방을 합치지 않는다 — 고르기만 한다")
    void neverMergesOrMoves() {
        rooms(new long[][] {{1, 2, 2}, {2, 1, 1}});
        assertThat(allocator.pickForDirectEntry(floor, NOW).getId()).isEqualTo(1L);
        verify(instanceRepository, never()).deleteById(anyLong());
        verify(occupantRepository, never()).delete(any());
        verify(occupantRepository, never()).save(any());
    }
}
