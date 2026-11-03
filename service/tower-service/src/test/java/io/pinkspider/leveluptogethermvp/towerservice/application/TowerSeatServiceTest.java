package io.pinkspider.leveluptogethermvp.towerservice.application;

import static io.pinkspider.leveluptogethermvp.towerservice.TowerTestFixtures.floor;
import static io.pinkspider.leveluptogethermvp.towerservice.TowerTestFixtures.instance;
import static io.pinkspider.leveluptogethermvp.towerservice.TowerTestFixtures.occupant;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.global.facade.GamificationQueryFacade;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerSeatResult;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerFloor;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerOccupant;
import io.pinkspider.leveluptogethermvp.towerservice.domain.enums.TowerOccupantKind;
import io.pinkspider.leveluptogethermvp.towerservice.infrastructure.TowerFloorRepository;
import io.pinkspider.leveluptogethermvp.towerservice.infrastructure.TowerOccupantRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

/** LUT-630: 자동 착석 — 입장 가능한 층 중 사람이 가장 적은 층(같으면 낮은 층), 그 층의 가장 적은 방 */
class TowerSeatServiceTest {

    private static final String USER = "user-1";

    private final TowerFloorRepository floorRepository = mock(TowerFloorRepository.class);
    private final TowerOccupantRepository occupantRepository = mock(TowerOccupantRepository.class);
    private final TowerInstanceAllocator allocator = mock(TowerInstanceAllocator.class);
    private final TowerPopulationService populationService = mock(TowerPopulationService.class);
    private final GamificationQueryFacade facade = mock(GamificationQueryFacade.class);

    private TowerSeatService service;

    private final TowerFloor f1 = floor(1L, 1, null, true, true);
    private final TowerFloor f2 = floor(2L, 2, 5, false, true);
    private final TowerFloor f3 = floor(3L, 3, 10, false, true);

    @BeforeEach
    void setUp() {
        service =
                new TowerSeatService(
                        floorRepository, occupantRepository, allocator, populationService, facade);
        when(occupantRepository.findByUserId(USER)).thenReturn(Optional.empty());
        when(floorRepository.findAllByIsPublicTrueOrderByFloorNumberAsc())
                .thenReturn(List.of(f1, f2, f3));
        for (TowerFloor f : List.of(f1, f2, f3)) {
            when(floorRepository.findByFloorNumberForUpdate(f.getFloorNumber()))
                    .thenReturn(Optional.of(f));
        }
        when(allocator.pickForAutoSeat(any(), isNull(), any()))
                .thenAnswer(
                        inv -> instance(50L, ((TowerFloor) inv.getArgument(0)).getFloorNumber()));
        when(occupantRepository.save(any(TowerOccupant.class)))
                .thenAnswer(
                        inv -> {
                            TowerOccupant o = inv.getArgument(0);
                            ReflectionTestUtils.setField(o, "id", 7L);
                            return o;
                        });
        when(populationService.byFloorFresh()).thenReturn(Map.of());
    }

    @Test
    @DisplayName("레벨 10 유저(1·2·3층 가능), 1층 5명·2층 3명·3층 0명이면 3층에 앉는다 — 자리는 입장 지점, 미션 중, AUTO_SEATED")
    void seatsOnEmptiestFloor() {
        when(facade.getUserLevel(USER)).thenReturn(10);
        when(populationService.byFloorFresh()).thenReturn(Map.of(1, 5L, 2, 3L));

        Optional<TowerSeatResult> result = service.onMissionStarted(USER);

        assertThat(result).contains(new TowerSeatResult(3, 50L, true));
        verify(floorRepository).findByFloorNumberForUpdate(3);
        ArgumentCaptor<TowerOccupant> captor = ArgumentCaptor.forClass(TowerOccupant.class);
        verify(occupantRepository).save(captor.capture());
        TowerOccupant seated = captor.getValue();
        assertThat(seated.getKind()).isEqualTo(TowerOccupantKind.AUTO_SEATED);
        assertThat(seated.isMissionActive()).isTrue();
        assertThat(seated.getSeatX()).isEqualTo(100);
        assertThat(seated.getSeatY()).isEqualTo(200);
        assertThat(seated.getFloorNumber()).isEqualTo(3);
    }

    @Test
    @DisplayName("입장 가능한 층의 인원이 모두 같으면 가장 낮은 층")
    void tieGoesToLowestFloor() {
        when(facade.getUserLevel(USER)).thenReturn(10);
        when(populationService.byFloorFresh()).thenReturn(Map.of(1, 2L, 2, 2L, 3, 2L));
        assertThat(service.onMissionStarted(USER).map(TowerSeatResult::floorNumber)).contains(1);
    }

    @Test
    @DisplayName("입장 레벨이 부족한 층에는 비어 있어도 앉히지 않는다 (레벨 4 → 2·3층 제외)")
    void skipsLockedFloorsEvenIfEmpty() {
        when(facade.getUserLevel(USER)).thenReturn(4);
        when(populationService.byFloorFresh()).thenReturn(Map.of(1, 5L));
        assertThat(service.onMissionStarted(USER).map(TowerSeatResult::floorNumber)).contains(1);
        verify(floorRepository, never()).findByFloorNumberForUpdate(2);
        verify(floorRepository, never()).findByFloorNumberForUpdate(3);
    }

    @Test
    @DisplayName("앉힐 공개 층이 없으면 empty")
    void noFloors() {
        when(floorRepository.findAllByIsPublicTrueOrderByFloorNumberAsc()).thenReturn(List.of());
        assertThat(service.onMissionStarted(USER)).isEmpty();
        verify(floorRepository, never()).findByFloorNumberForUpdate(anyInt());
    }

    @Test
    @DisplayName("이미 탑에 있는 유저는 그 자리를 미션 중으로 바꾼다 (자동 착석 아님)")
    void alreadyInTower() {
        TowerOccupant inTower = occupant(5L, 9L, 2, USER, TowerOccupantKind.DIRECT, false);
        when(occupantRepository.findByUserId(USER)).thenReturn(Optional.of(inTower));

        assertThat(service.onMissionStarted(USER)).contains(new TowerSeatResult(2, 9L, false));
        assertThat(inTower.isMissionActive()).isTrue();
        verify(allocator, never()).pickForAutoSeat(any(), any(), any());
    }

    @Test
    @DisplayName("미션 종료: 자동 착석자는 퇴장, 직접 입장자는 연결돼 있으면 휴식 중·끊겨 있으면 퇴장, 탑에 없으면 false")
    void missionEnded() {
        TowerOccupant auto = occupant(5L, 9L, 1, USER, TowerOccupantKind.AUTO_SEATED, true);
        when(occupantRepository.findByUserId(USER)).thenReturn(Optional.of(auto));
        assertThat(service.onMissionEnded(USER, true)).isTrue();
        verify(allocator).remove(auto);

        TowerOccupant direct = occupant(6L, 9L, 1, USER, TowerOccupantKind.DIRECT, true);
        when(occupantRepository.findByUserId(USER)).thenReturn(Optional.of(direct));
        assertThat(service.onMissionEnded(USER, true)).isFalse();
        assertThat(direct.isMissionActive()).isFalse();
        verify(allocator, never()).remove(direct);

        TowerOccupant gone = occupant(8L, 9L, 1, USER, TowerOccupantKind.DIRECT, true);
        when(occupantRepository.findByUserId(USER)).thenReturn(Optional.of(gone));
        assertThat(service.onMissionEnded(USER, false)).isTrue();
        verify(allocator).remove(gone);

        when(occupantRepository.findByUserId(USER)).thenReturn(Optional.empty());
        assertThat(service.onMissionEnded(USER, true)).isFalse();
    }

    @Test
    @DisplayName("레벨 조회가 실패하면 레벨 1 로 — 제한 없는 1층에만 앉는다")
    void levelLookupFails() {
        when(facade.getUserLevel(USER)).thenThrow(new RuntimeException("down"));
        when(populationService.byFloorFresh()).thenReturn(Map.of(1, 9L));
        assertThat(service.onMissionStarted(USER).map(TowerSeatResult::floorNumber)).contains(1);
        verify(allocator).pickForAutoSeat(eq(f1), isNull(), any());
    }
}
