package io.pinkspider.leveluptogethermvp.towerservice.application;

import static io.pinkspider.leveluptogethermvp.towerservice.TowerTestFixtures.floor;
import static io.pinkspider.leveluptogethermvp.towerservice.TowerTestFixtures.instance;
import static io.pinkspider.leveluptogethermvp.towerservice.TowerTestFixtures.occupant;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.global.facade.GamificationQueryFacade;
import io.pinkspider.global.facade.MissionQueryFacade;
import io.pinkspider.global.facade.UserQueryFacade;
import io.pinkspider.global.facade.dto.InProgressMissionDto;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerInstanceEvent;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerMissionInfo;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerSeatResult;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerFloor;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerMissionSeat;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerOccupant;
import io.pinkspider.leveluptogethermvp.towerservice.domain.enums.TowerOccupantKind;
import io.pinkspider.leveluptogethermvp.towerservice.infrastructure.TowerFloorRepository;
import io.pinkspider.leveluptogethermvp.towerservice.infrastructure.TowerMissionSeatRepository;
import io.pinkspider.leveluptogethermvp.towerservice.infrastructure.TowerOccupantRepository;
import io.pinkspider.leveluptogethermvp.towerservice.realtime.TowerRealtimePublisher;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;

/** LUT-630/632: 자동 착석·미션 시작/종료에 따른 자리·브로드캐스트·자리 기록 */
class TowerSeatServiceTest {

    private static final String USER = "user-1";
    private static final TowerMissionInfo PUBLIC_MISSION =
            new TowerMissionInfo(10L, 20L, "REGULAR", "달리기", 3L, "PUBLIC");
    private static final TowerMissionInfo PRIVATE_MISSION =
            new TowerMissionInfo(11L, 21L, "PINNED", "비밀 미션", 4L, "PRIVATE");

    private final TowerFloorRepository floorRepository = mock(TowerFloorRepository.class);
    private final TowerOccupantRepository occupantRepository = mock(TowerOccupantRepository.class);
    private final TowerMissionSeatRepository seatRepository =
            mock(TowerMissionSeatRepository.class);
    private final TowerInstanceAllocator allocator = mock(TowerInstanceAllocator.class);
    private final TowerPopulationService populationService = mock(TowerPopulationService.class);
    private final TowerRealtimePublisher publisher = mock(TowerRealtimePublisher.class);
    private final TowerPresenceGateway presence = mock(TowerPresenceGateway.class);
    private final io.pinkspider.leveluptogethermvp.towerservice.presence.TowerPresenceService
            presenceService =
                    mock(
                            io.pinkspider.leveluptogethermvp.towerservice.presence
                                    .TowerPresenceService.class);
    private final GamificationQueryFacade gamificationFacade = mock(GamificationQueryFacade.class);
    private final UserQueryFacade userFacade = mock(UserQueryFacade.class);
    private final MissionQueryFacade missionFacade = mock(MissionQueryFacade.class);

    private TowerSeatService service;

    private final TowerFloor f1 = floor(1L, 1, null, true, true);
    private final TowerFloor f2 = floor(2L, 2, 5, false, true);
    private final TowerFloor f3 = floor(3L, 3, 10, false, true);

    @BeforeEach
    void setUp() {
        service =
                new TowerSeatService(
                        floorRepository,
                        occupantRepository,
                        seatRepository,
                        allocator,
                        populationService,
                        publisher,
                        presence,
                        presenceService,
                        gamificationFacade,
                        userFacade,
                        missionFacade);
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
        when(seatRepository.save(any(TowerMissionSeat.class)))
                .thenAnswer(
                        inv -> {
                            TowerMissionSeat s = inv.getArgument(0);
                            ReflectionTestUtils.setField(s, "id", 9L);
                            return s;
                        });
        when(seatRepository.findFirstByUserIdAndExecutionKindAndExecutionIdOrderBySeatedAtDesc(
                        any(), any(), any()))
                .thenReturn(Optional.empty());
        when(seatRepository.findAllByUserIdAndEndedAtIsNull(any())).thenReturn(List.of());
        when(populationService.byFloorFresh()).thenReturn(Map.of());
        when(userFacade.isTowerAutoSeatEnabled(any())).thenReturn(true);
        when(presence.isConnected(any())).thenReturn(true);
    }

    private TowerInstanceEvent lastEvent(Long instanceId) {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(publisher).publishToInstance(eq(instanceId), captor.capture());
        return (TowerInstanceEvent) captor.getValue();
    }

    @Test
    @DisplayName(
            "레벨 10 유저(1·2·3층 가능), 1층 5명·2층 3명·3층 0명이면 3층에 자동 착석 — 자리는 입장 지점, AUTO_SEATED, 자리 기록"
                    + " auto_seated")
    void seatsOnEmptiestFloor() {
        when(gamificationFacade.getUserLevel(USER)).thenReturn(10);
        when(populationService.byFloorFresh()).thenReturn(Map.of(1, 5L, 2, 3L));

        Optional<TowerSeatResult> result = service.onMissionStarted(USER, PUBLIC_MISSION);

        assertThat(result).contains(new TowerSeatResult(3, 50L, true));
        verify(floorRepository).findByFloorNumberForUpdate(3);
        ArgumentCaptor<TowerOccupant> captor = ArgumentCaptor.forClass(TowerOccupant.class);
        verify(occupantRepository).save(captor.capture());
        assertThat(captor.getValue().getKind()).isEqualTo(TowerOccupantKind.AUTO_SEATED);
        assertThat(captor.getValue().isMissionActive()).isTrue();
        assertThat(captor.getValue().getSeatX()).isEqualTo(100);
        ArgumentCaptor<TowerMissionSeat> seat = ArgumentCaptor.forClass(TowerMissionSeat.class);
        verify(seatRepository).save(seat.capture());
        assertThat(seat.getValue().isAutoSeated()).isTrue();
        assertThat(seat.getValue().getFloorNumber()).isEqualTo(3);
        assertThat(seat.getValue().getExecutionId()).isEqualTo(20L);
        assertThat(seat.getValue().getExecutionKind()).isEqualTo("REGULAR");
        // 입장(ENTER) 브로드캐스트는 LUT-631 — 여기서는 상태 이벤트 없음
        verify(publisher, never()).publishToInstance(any(), any());
        verify(presenceService).register(captor.getValue(), false); // LUT-631: ENTER + 아바타 데이터
    }

    @Test
    @DisplayName("인원이 모두 같으면 가장 낮은 층 · 입장 레벨이 부족한 층은 비어 있어도 제외 · 공개 층이 없으면 empty")
    void floorChoice() {
        when(gamificationFacade.getUserLevel(USER)).thenReturn(10);
        when(populationService.byFloorFresh()).thenReturn(Map.of(1, 2L, 2, 2L, 3, 2L));
        assertThat(service.onMissionStarted(USER, null).map(TowerSeatResult::floorNumber))
                .contains(1);

        when(gamificationFacade.getUserLevel(USER)).thenReturn(4);
        when(populationService.byFloorFresh()).thenReturn(Map.of(1, 5L));
        assertThat(service.onMissionStarted(USER, null).map(TowerSeatResult::floorNumber))
                .contains(1);
        verify(floorRepository, never()).findByFloorNumberForUpdate(2);
        verify(floorRepository, never()).findByFloorNumberForUpdate(3);

        when(floorRepository.findAllByIsPublicTrueOrderByFloorNumberAsc()).thenReturn(List.of());
        assertThat(service.onMissionStarted(USER, null)).isEmpty();
    }

    @Test
    @DisplayName("자동 착석을 끈 유저는 앉히지 않는다 · 설정 조회 실패는 켬으로 본다")
    void autoSeatDisabled() {
        when(userFacade.isTowerAutoSeatEnabled(USER)).thenReturn(false);
        assertThat(service.onMissionStarted(USER, PUBLIC_MISSION)).isEmpty();
        verify(occupantRepository, never()).save(any());
        verify(seatRepository, never()).save(any());

        when(userFacade.isTowerAutoSeatEnabled(USER)).thenThrow(new RuntimeException("down"));
        when(gamificationFacade.getUserLevel(USER)).thenReturn(1);
        assertThat(service.onMissionStarted(USER, PUBLIC_MISSION)).isPresent();
    }

    @Test
    @DisplayName(
            "이미 탑에 있는 유저는 그 자리를 미션 중으로 바꾸고 같은 방에 USER_STATE(MISSION)를 보낸다 — 공개 미션만 미션명·카테고리 포함")
    void alreadyInTower() {
        TowerOccupant inTower = occupant(5L, 9L, 2, USER, TowerOccupantKind.DIRECT, false);
        when(occupantRepository.findByUserId(USER)).thenReturn(Optional.of(inTower));

        assertThat(service.onMissionStarted(USER, PUBLIC_MISSION))
                .contains(new TowerSeatResult(2, 9L, false));
        assertThat(inTower.isMissionActive()).isTrue();
        verify(allocator, never()).pickForAutoSeat(any(), any(), any());
        verify(presenceService).updateState(inTower, TowerInstanceEvent.STATE_MISSION); // LUT-631
        TowerInstanceEvent event = lastEvent(9L);
        assertThat(event.type()).isEqualTo(TowerInstanceEvent.TYPE_USER_STATE);
        assertThat(event.state()).isEqualTo(TowerInstanceEvent.STATE_MISSION);
        assertThat(event.mission()).isEqualTo(new TowerInstanceEvent.MissionInfo("달리기", 3L));
        ArgumentCaptor<TowerMissionSeat> seat = ArgumentCaptor.forClass(TowerMissionSeat.class);
        verify(seatRepository).save(seat.capture());
        assertThat(seat.getValue().isAutoSeated()).isFalse();
        assertThat(seat.getValue().getInstanceId()).isEqualTo(9L);
    }

    @Test
    @DisplayName("비공개 미션은 '미션 중'만 — 페이로드에 미션명이 없다")
    void privateMissionHidesTitle() {
        TowerOccupant inTower = occupant(5L, 9L, 2, USER, TowerOccupantKind.DIRECT, false);
        when(occupantRepository.findByUserId(USER)).thenReturn(Optional.of(inTower));

        service.onMissionStarted(USER, PRIVATE_MISSION);

        TowerInstanceEvent event = lastEvent(9L);
        assertThat(event.state()).isEqualTo(TowerInstanceEvent.STATE_MISSION);
        assertThat(event.mission()).isNull();
    }

    @Test
    @DisplayName("같은 수행의 자리 기록이 이미 열려 있으면 다시 만들지 않는다 (이벤트·입장 중복) · 미션 정보가 없으면 기록 없음")
    void seatRecordIdempotent() {
        TowerOccupant inTower = occupant(5L, 9L, 2, USER, TowerOccupantKind.DIRECT, false);
        when(occupantRepository.findByUserId(USER)).thenReturn(Optional.of(inTower));
        when(seatRepository.findFirstByUserIdAndExecutionKindAndExecutionIdOrderBySeatedAtDesc(
                        USER, "REGULAR", 20L))
                .thenReturn(
                        Optional.of(
                                TowerMissionSeat.open(
                                        USER,
                                        10L,
                                        20L,
                                        "REGULAR",
                                        2,
                                        9L,
                                        false,
                                        LocalDateTime.now())));

        service.onMissionStarted(USER, PUBLIC_MISSION);
        service.onMissionStarted(USER, null);

        verify(seatRepository, never()).save(any());
    }

    @Test
    @DisplayName("미션 완료: 완료 연출(10초 응원 창)을 먼저 보내고, 연결된 직접 입장자는 휴식 중(REST)으로 남는다 · 자리 기록은 닫힌다")
    void endedCompletedConnected() {
        TowerOccupant direct = occupant(6L, 9L, 1, USER, TowerOccupantKind.DIRECT, true);
        when(occupantRepository.findByUserId(USER)).thenReturn(Optional.of(direct));
        TowerMissionSeat open =
                TowerMissionSeat.open(USER, 10L, 20L, "REGULAR", 1, 9L, false, LocalDateTime.now());
        when(seatRepository.findAllByUserIdAndEndedAtIsNull(USER)).thenReturn(List.of(open));

        assertThat(service.onMissionEnded(USER, true)).isFalse();

        assertThat(direct.isMissionActive()).isFalse();
        assertThat(open.isOpen()).isFalse();
        verify(allocator, never()).remove(direct);
        verify(presenceService).updateState(direct, TowerInstanceEvent.STATE_REST); // LUT-631
        ArgumentCaptor<Object> events = ArgumentCaptor.forClass(Object.class);
        verify(publisher, times(2)).publishToInstance(eq(9L), events.capture());
        TowerInstanceEvent first = (TowerInstanceEvent) events.getAllValues().get(0);
        TowerInstanceEvent second = (TowerInstanceEvent) events.getAllValues().get(1);
        assertThat(first.type()).isEqualTo(TowerInstanceEvent.TYPE_MISSION_COMPLETED);
        assertThat(first.cheerWindowSeconds()).isEqualTo(10);
        assertThat(second.state()).isEqualTo(TowerInstanceEvent.STATE_REST);
    }

    @Test
    @DisplayName("미션 종료: 연결이 끊긴 직접 입장자는 퇴장(완료 연출 뒤), 자동 착석자는 연결과 무관하게 퇴장, 취소면 완료 연출 없음")
    void endedRemoves() {
        TowerOccupant gone = occupant(8L, 9L, 1, USER, TowerOccupantKind.DIRECT, true);
        when(occupantRepository.findByUserId(USER)).thenReturn(Optional.of(gone));
        when(presence.isConnected(USER)).thenReturn(false);
        assertThat(service.onMissionEnded(USER, true)).isTrue();
        InOrder order = inOrder(publisher, allocator);
        order.verify(publisher).publishToInstance(eq(9L), any());
        order.verify(allocator).remove(gone);

        TowerOccupant auto = occupant(5L, 9L, 1, USER, TowerOccupantKind.AUTO_SEATED, true);
        when(occupantRepository.findByUserId(USER)).thenReturn(Optional.of(auto));
        when(presence.isConnected(USER)).thenReturn(true);
        assertThat(service.onMissionEnded(USER, false)).isTrue();
        verify(allocator).remove(auto);
        // 취소(완료 아님)에는 완료 연출이 없다 — 위 완료 1회만
        verify(publisher, times(1)).publishToInstance(eq(9L), any());

        when(occupantRepository.findByUserId(USER)).thenReturn(Optional.empty());
        assertThat(service.onMissionEnded(USER, true)).isFalse();
    }

    @Test
    @DisplayName("자리 직접 비우기: 미션 중이어도(자동 착석 포함) 자리를 비우고 기록을 닫는다 · 탑에 없으면 false")
    void vacate() {
        TowerOccupant auto = occupant(5L, 9L, 1, USER, TowerOccupantKind.AUTO_SEATED, true);
        when(occupantRepository.findByUserId(USER)).thenReturn(Optional.of(auto));
        TowerMissionSeat open =
                TowerMissionSeat.open(USER, 10L, 20L, "REGULAR", 1, 9L, true, LocalDateTime.now());
        when(seatRepository.findAllByUserIdAndEndedAtIsNull(USER)).thenReturn(List.of(open));

        assertThat(service.vacate(USER)).isTrue();
        verify(allocator).remove(auto);
        assertThat(open.isOpen()).isFalse();

        when(occupantRepository.findByUserId(USER)).thenReturn(Optional.empty());
        assertThat(service.vacate(USER)).isFalse();
    }

    @Test
    @DisplayName("응원: 열린 자리 기록이 있으면 DB 에서 +1, 없으면 -1")
    void addCheer() {
        TowerMissionSeat open =
                TowerMissionSeat.open(USER, 10L, 20L, "REGULAR", 1, 9L, true, LocalDateTime.now());
        ReflectionTestUtils.setField(open, "id", 9L);
        ReflectionTestUtils.setField(open, "cheerCount", 4);
        when(seatRepository.findFirstByUserIdAndEndedAtIsNullOrderBySeatedAtDesc(USER))
                .thenReturn(Optional.of(open));
        assertThat(service.addCheer(USER)).isEqualTo(5);
        verify(seatRepository).incrementCheer(9L);

        when(seatRepository.findFirstByUserIdAndEndedAtIsNullOrderBySeatedAtDesc("x"))
                .thenReturn(Optional.empty());
        assertThat(service.addCheer("x")).isEqualTo(-1);
    }

    @Test
    @DisplayName("주기 대조: 미션 중으로 기록됐지만 실제 진행 중 미션이 없는 유저만 정리한다 · 조회 실패면 건너뛴다")
    void reconcile() {
        TowerOccupant u1 = occupant(1L, 9L, 1, "u1", TowerOccupantKind.DIRECT, true);
        TowerOccupant u2 = occupant(2L, 9L, 1, "u2", TowerOccupantKind.AUTO_SEATED, true);
        when(occupantRepository.findAllByMissionActiveTrue()).thenReturn(List.of(u1, u2));
        when(occupantRepository.findByUserId("u2")).thenReturn(Optional.of(u2));
        when(missionFacade.findInProgressMissions(anyList()))
                .thenReturn(
                        Map.of(
                                "u1",
                                new InProgressMissionDto(
                                        10L,
                                        3L,
                                        "운동",
                                        "달리기",
                                        "PUBLIC",
                                        null,
                                        LocalDateTime.now())));

        assertThat(service.reconcile()).isEqualTo(1);
        verify(allocator).remove(u2);
        verify(allocator, never()).remove(u1);

        when(missionFacade.findInProgressMissions(anyList()))
                .thenThrow(new RuntimeException("down"));
        assertThat(service.reconcile()).isZero();

        when(occupantRepository.findAllByMissionActiveTrue()).thenReturn(List.of());
        assertThat(service.reconcile()).isZero();
    }

    @Test
    @DisplayName("레벨 조회가 실패하면 레벨 1 로 — 제한 없는 1층에만 앉는다")
    void levelLookupFails() {
        when(gamificationFacade.getUserLevel(USER)).thenThrow(new RuntimeException("down"));
        when(populationService.byFloorFresh()).thenReturn(Map.of(1, 9L));
        assertThat(service.onMissionStarted(USER, null).map(TowerSeatResult::floorNumber))
                .contains(1);
        verify(allocator).pickForAutoSeat(eq(f1), isNull(), any());
    }
}
