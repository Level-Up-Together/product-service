package io.pinkspider.leveluptogethermvp.towerservice.application;

import static io.pinkspider.leveluptogethermvp.towerservice.TowerTestFixtures.floor;
import static io.pinkspider.leveluptogethermvp.towerservice.TowerTestFixtures.instance;
import static io.pinkspider.leveluptogethermvp.towerservice.TowerTestFixtures.occupant;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.global.facade.GamificationQueryFacade;
import io.pinkspider.global.facade.MissionQueryFacade;
import io.pinkspider.global.facade.dto.InProgressMissionDto;
import io.pinkspider.leveluptogethermvp.towerservice.application.exception.TowerEntryException;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerEnterResponse;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerLeaveResponse;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerFloor;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerOccupant;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerUserState;
import io.pinkspider.leveluptogethermvp.towerservice.domain.enums.TowerOccupantKind;
import io.pinkspider.leveluptogethermvp.towerservice.infrastructure.TowerFloorRepository;
import io.pinkspider.leveluptogethermvp.towerservice.infrastructure.TowerFloorVisitRepository;
import io.pinkspider.leveluptogethermvp.towerservice.infrastructure.TowerOccupantRepository;
import io.pinkspider.leveluptogethermvp.towerservice.infrastructure.TowerUserStateRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

/** LUT-630: 입장 판정·복귀·퇴장 (배정 규칙 자체는 TowerInstanceAllocatorTest) */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TowerEntryServiceTest {

    private static final String USER = "user-1";

    @Mock private TowerFloorRepository floorRepository;
    @Mock private TowerOccupantRepository occupantRepository;
    @Mock private TowerUserStateRepository userStateRepository;
    @Mock private TowerInstanceAllocator allocator;
    @Mock private TowerPopulationService populationService;
    @Mock private GamificationQueryFacade gamificationQueryFacade;
    @Mock private MissionQueryFacade missionQueryFacade;

    @Mock
    private io.pinkspider.leveluptogethermvp.towerservice.presence.TowerPresenceService
            presenceService;

    @Mock private TowerFloorVisitRepository visitRepository;
    @Mock private org.springframework.context.ApplicationEventPublisher eventPublisher;

    @Mock
    private io.pinkspider.leveluptogethermvp.towerservice.application.queue.TowerQueueService
            queueService;

    @Mock
    private io.pinkspider.leveluptogethermvp.towerservice.application.visitor.TowerVisitorGate
            visitorGate;

    @Mock private TowerFloorGuideService guideService;

    private TowerEntryService service;

    private final TowerFloor f1 = floor(1L, 1, null, true, true);
    private final TowerFloor f2 = floor(2L, 2, 5, false, true);
    private final TowerFloor f2Unlimited = floor(2L, 2, null, false, true);

    @BeforeEach
    void setUp() {
        service =
                new TowerEntryService(
                        floorRepository,
                        occupantRepository,
                        userStateRepository,
                        allocator,
                        populationService,
                        gamificationQueryFacade,
                        missionQueryFacade,
                        presenceService,
                        visitRepository,
                        eventPublisher,
                        queueService,
                        visitorGate,
                        guideService);
        when(populationService.of(anyInt())).thenReturn(3L);
        when(missionQueryFacade.findInProgressMission(any())).thenReturn(Optional.empty());
        when(userStateRepository.findById(any())).thenReturn(Optional.empty());
        when(occupantRepository.findByUserId(any())).thenReturn(Optional.empty());
        when(occupantRepository.save(any(TowerOccupant.class)))
                .thenAnswer(
                        inv -> {
                            TowerOccupant o = inv.getArgument(0);
                            ReflectionTestUtils.setField(o, "id", 77L);
                            return o;
                        });
        when(allocator.pickForDirectEntry(any(), any())).thenReturn(instance(10L, 2));
        org.mockito.Mockito.lenient()
                .when(visitorGate.admit(any()))
                .thenReturn(
                        new io.pinkspider.leveluptogethermvp.towerservice.application.visitor
                                .TowerVisitorSession("vk-1", 4821, null, null, null, 1L));
        org.mockito.Mockito.lenient()
                .when(queueService.check(any(), anyInt(), any()))
                .thenReturn(
                        io.pinkspider.leveluptogethermvp.towerservice.application.queue
                                .TowerQueueService.Gate.passed());
    }

    private void stub(TowerFloor floor) {
        when(floorRepository.findByFloorNumber(floor.getFloorNumber()))
                .thenReturn(Optional.of(floor));
        when(floorRepository.findByFloorNumberForUpdate(floor.getFloorNumber()))
                .thenReturn(Optional.of(floor));
    }

    private static void assertCode(Runnable r, String code) {
        assertThatThrownBy(r::run)
                .isInstanceOf(TowerEntryException.class)
                .extracting("code")
                .isEqualTo(code);
    }

    @Test
    @DisplayName("없는 층·비공개 층 → 150201")
    void floorNotAvailable() {
        when(floorRepository.findByFloorNumber(9)).thenReturn(Optional.empty());
        assertCode(() -> service.enter(USER, 9), TowerEntryException.FLOOR_NOT_AVAILABLE);

        stub(floor(3L, 3, null, true, false));
        assertCode(() -> service.enter(USER, 3), TowerEntryException.FLOOR_NOT_AVAILABLE);
    }

    @Test
    @DisplayName("토큰 없는 입장: 비허용 층은 제한 없음이어도 150202, 허용 층은 방문객으로 배정되고 visitor_key 를 받는다")
    void guest() {
        stub(f2Unlimited);
        assertCode(() -> service.enter(null, 2), TowerEntryException.LOGIN_REQUIRED);
        verify(allocator, never()).pickForDirectEntry(any(), any());

        stub(f1);
        when(allocator.pickForDirectEntry(eq(f1), any())).thenReturn(instance(10L, 1));
        TowerEnterResponse res = service.enter(null, 1);

        ArgumentCaptor<TowerOccupant> captor = ArgumentCaptor.forClass(TowerOccupant.class);
        verify(occupantRepository).save(captor.capture());
        assertThat(captor.getValue().getKind()).isEqualTo(TowerOccupantKind.VISITOR);
        assertThat(captor.getValue().getVisitorKey()).isEqualTo(res.visitorKey()).isNotBlank();
        assertThat(res.instanceId()).isEqualTo(10L);
        assertThat(res.resumed()).isFalse();
        assertThat(res.population()).isEqualTo(3L);
        // LUT-631: 방문객 번호(네 자리) + presence 등록(ENTER)
        assertThat(res.visitorNo()).isBetween(1000, 9999);
        assertThat(captor.getValue().getVisitorNo()).isEqualTo(res.visitorNo());
        verify(presenceService).register(captor.getValue(), false);
        verify(allocator).relocateAutoSeatedOverflow(eq(f1), eq(10L), any());
        verify(userStateRepository, never()).save(any());
    }

    @Test
    @DisplayName("제한 없는 2층에 레벨 1 로그인 유저가 입장된다 (레벨을 보지 않는다)")
    void unlimitedFloor() {
        stub(f2Unlimited);
        TowerEnterResponse res = service.enter(USER, 2);
        assertThat(res.instanceId()).isEqualTo(10L);
        verify(gamificationQueryFacade, never()).getUserLevel(any());
    }

    @Test
    @DisplayName("2층·입장 레벨 5: 레벨 5 는 입장, 레벨 4 는 150203 — 층 번호가 아니라 등록된 입장 레벨로 판정")
    void entryLevel() {
        stub(f2);
        when(gamificationQueryFacade.getUserLevel(USER)).thenReturn(5);
        TowerEnterResponse res = service.enter(USER, 2);
        assertThat(res.floorNumber()).isEqualTo(2);
        assertThat(res.entryPointX()).isEqualTo(100);
        assertThat(res.seatY()).isEqualTo(200);

        when(gamificationQueryFacade.getUserLevel(USER)).thenReturn(4);
        assertCode(() -> service.enter(USER, 2), TowerEntryException.LEVEL_INSUFFICIENT);
    }

    @Test
    @DisplayName("레벨 조회 실패는 레벨 1 로 판정한다 (제한 있는 층은 거부, 입장은 안 막힘)")
    void levelLookupFails() {
        stub(f2);
        when(gamificationQueryFacade.getUserLevel(USER)).thenThrow(new RuntimeException("down"));
        assertCode(() -> service.enter(USER, 2), TowerEntryException.LEVEL_INSUFFICIENT);
    }

    @Test
    @DisplayName("자리 유지 중(미션 중) 유저가 같은 층에 돌아오면 배정 없이 원래 방으로 복귀한다 — 상한·레벨과 무관")
    void resumeHeldSeat() {
        stub(f2);
        when(gamificationQueryFacade.getUserLevel(USER)).thenReturn(99);
        TowerOccupant held = occupant(5L, 9L, 2, USER, TowerOccupantKind.DIRECT, true);
        when(occupantRepository.findByUserId(USER)).thenReturn(Optional.of(held));

        TowerEnterResponse res = service.enter(USER, 2);

        assertThat(res.resumed()).isTrue();
        assertThat(res.autoSeatContinued()).isFalse(); // 직접 입장 자리 유지 복귀는 자동 착석 전환이 아니다
        assertThat(res.instanceId()).isEqualTo(9L);
        assertThat(res.missionActive()).isTrue();
        verify(allocator, never()).pickForDirectEntry(any(), any());
        verify(occupantRepository, never()).save(any());
        verify(presenceService, never()).register(any(), org.mockito.ArgumentMatchers.anyBoolean());
        verify(userStateRepository).save(any(TowerUserState.class));
    }

    @Test
    @DisplayName("자동 착석한 유저가 탑에 들어오면 그 자리에서 직접 입장으로 이어진다; 그 방이 직접 입장자로 차 있으면 일반 배정")
    void autoSeatedBecomesDirect() {
        stub(f1);
        TowerOccupant auto = occupant(5L, 9L, 1, USER, TowerOccupantKind.AUTO_SEATED, true);
        when(occupantRepository.findByUserId(USER)).thenReturn(Optional.of(auto));
        when(allocator.countedOccupants(9L)).thenReturn(29L);

        TowerEnterResponse res = service.enter(USER, 1);
        assertThat(res.resumed()).isTrue();
        assertThat(res.autoSeatContinued()).isTrue(); // LUT-646
        assertThat(res.instanceId()).isEqualTo(9L);
        assertThat(auto.getKind()).isEqualTo(TowerOccupantKind.DIRECT);
        assertThat(auto.isMissionActive()).isTrue();
        verify(allocator, never()).pickForDirectEntry(any(), any());

        TowerOccupant auto2 = occupant(6L, 9L, 1, USER, TowerOccupantKind.AUTO_SEATED, true);
        when(occupantRepository.findByUserId(USER)).thenReturn(Optional.of(auto2));
        when(allocator.countedOccupants(9L)).thenReturn(30L);
        when(allocator.pickForDirectEntry(eq(f1), any())).thenReturn(instance(11L, 1));

        TowerEnterResponse res2 = service.enter(USER, 1);
        verify(allocator).remove(auto2);
        assertThat(res2.resumed()).isFalse();
        assertThat(res2.autoSeatContinued()).isFalse();
        assertThat(res2.instanceId()).isEqualTo(11L);
        assertThat(res2.missionActive()).isTrue();
    }

    @Test
    @DisplayName("다른 층으로 들어오면 자리를 옮긴다 (유저 의사) — 미션 중 상태는 가져간다")
    void moveFloors() {
        stub(f2Unlimited);
        TowerOccupant held = occupant(5L, 9L, 1, USER, TowerOccupantKind.DIRECT, true);
        when(occupantRepository.findByUserId(USER)).thenReturn(Optional.of(held));

        TowerEnterResponse res = service.enter(USER, 2);

        verify(allocator).remove(held);
        ArgumentCaptor<TowerOccupant> captor = ArgumentCaptor.forClass(TowerOccupant.class);
        verify(occupantRepository).save(captor.capture());
        assertThat(captor.getValue().getKind()).isEqualTo(TowerOccupantKind.DIRECT);
        assertThat(captor.getValue().isMissionActive()).isTrue();
        assertThat(captor.getValue().getFloorNumber()).isEqualTo(2);
        assertThat(res.resumed()).isFalse();
    }

    @Test
    @DisplayName("마지막 입장 층(tower_user_state)을 갱신한다 — 있으면 visit, 없으면 생성")
    void userState() {
        stub(f2Unlimited);
        TowerUserState state =
                TowerUserState.builder()
                        .userId(USER)
                        .lastFloorNumber(1)
                        .lastEnteredAt(LocalDateTime.of(2026, 11, 1, 0, 0))
                        .build();
        when(userStateRepository.findById(USER)).thenReturn(Optional.of(state));

        service.enter(USER, 2);

        assertThat(state.getLastFloorNumber()).isEqualTo(2);
        verify(userStateRepository, never()).save(any());
    }

    @Test
    @DisplayName("퇴장(멱등): 휴식 중 유저는 자리를 비우고, 미션 중이면 자리를 남기고, 탑에 없으면 둘 다 false")
    void leaveUser() {
        TowerOccupant resting = occupant(5L, 9L, 1, USER, TowerOccupantKind.DIRECT, false);
        when(occupantRepository.findByUserId(USER)).thenReturn(Optional.of(resting));
        assertThat(service.leave(USER, null)).isEqualTo(new TowerLeaveResponse(true, false));
        verify(allocator).remove(resting);

        TowerOccupant inMission = occupant(6L, 9L, 1, USER, TowerOccupantKind.DIRECT, true);
        when(occupantRepository.findByUserId(USER)).thenReturn(Optional.of(inMission));
        assertThat(service.leave(USER, null)).isEqualTo(new TowerLeaveResponse(false, true));
        verify(allocator, never()).remove(inMission);

        when(occupantRepository.findByUserId(USER)).thenReturn(Optional.empty());
        assertThat(service.leave(USER, null)).isEqualTo(new TowerLeaveResponse(false, false));
    }

    @Test
    @DisplayName("방문객 퇴장은 visitor_key 로 — 키가 없으면 150204, 모르는 키는 false")
    void leaveVisitor() {
        assertCode(() -> service.leave(null, null), TowerEntryException.VISITOR_KEY_REQUIRED);
        assertCode(() -> service.leave(null, " "), TowerEntryException.VISITOR_KEY_REQUIRED);

        when(occupantRepository.findByVisitorKey("k")).thenReturn(Optional.empty());
        assertThat(service.leave(null, "k")).isEqualTo(new TowerLeaveResponse(false, false));

        TowerOccupant visitor = occupant(7L, 9L, 1, "k", TowerOccupantKind.VISITOR, false);
        when(occupantRepository.findByVisitorKey("k")).thenReturn(Optional.of(visitor));
        assertThat(service.leave(null, "k")).isEqualTo(new TowerLeaveResponse(true, false));
        verify(allocator).remove(visitor);
        verify(allocator, never()).countedOccupants(anyLong());
    }

    @Test
    @DisplayName("LUT-632: 탑 밖에서 시작한 미션이 진행 중이면 입장 즉시 미션 중(자리 유지) · 조회 실패는 휴식 중")
    void enterReflectsInProgressMission() {
        stub(f2Unlimited);
        when(missionQueryFacade.findInProgressMission(USER))
                .thenReturn(
                        Optional.of(
                                new InProgressMissionDto(
                                        10L,
                                        3L,
                                        "운동",
                                        "달리기",
                                        "PUBLIC",
                                        null,
                                        LocalDateTime.now())));

        TowerEnterResponse res = service.enter(USER, 2);

        assertThat(res.missionActive()).isTrue();
        ArgumentCaptor<TowerOccupant> captor = ArgumentCaptor.forClass(TowerOccupant.class);
        verify(occupantRepository).save(captor.capture());
        assertThat(captor.getValue().isMissionActive()).isTrue();

        when(missionQueryFacade.findInProgressMission(USER))
                .thenThrow(new RuntimeException("down"));
        assertThat(service.enter(USER, 2).missionActive()).isFalse();
    }

    @Test
    @DisplayName("LUT-634: 층 첫 입장이면(insert 1행) TowerFloorFirstEnteredEvent, 두 번째(0행)·방문객은 없음")
    void firstFloorVisit() {
        stub(f1);
        when(visitRepository.insertIfAbsent(eq(USER), eq(1), any())).thenReturn(1);
        when(visitRepository.countByUserId(USER)).thenReturn(2L);
        service.enter(USER, 1);
        ArgumentCaptor<Object> events = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(events.capture());
        assertThat(events.getValue())
                .isInstanceOf(io.pinkspider.global.event.TowerFloorFirstEnteredEvent.class);
        io.pinkspider.global.event.TowerFloorFirstEnteredEvent entered =
                (io.pinkspider.global.event.TowerFloorFirstEnteredEvent) events.getValue();
        assertThat(entered.userId()).isEqualTo(USER);
        assertThat(entered.floorNumber()).isEqualTo(1);
        assertThat(entered.visitedFloorCount()).isEqualTo(2);

        org.mockito.Mockito.clearInvocations(eventPublisher);
        when(visitRepository.insertIfAbsent(eq(USER), eq(1), any())).thenReturn(0);
        service.enter(USER, 1);
        verify(eventPublisher, never()).publishEvent(any(Object.class));

        when(allocator.pickForDirectEntry(eq(f1), any())).thenReturn(instance(10L, 1));
        service.enter(null, 1);
        verify(visitRepository, never()).insertIfAbsent(isNull(), anyInt(), any());
    }

    @Test
    @DisplayName(
            "LUT-701: 상한이면 배정 없이 대기 순번 · 이미 안(직접 입장·자리 유지)이면 층 이동·복귀라 대기열을 거치지 않는다 · 자동 착석만 한 유저는 새"
                    + " 입장")
    void queueGate() {
        stub(f1);
        io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerQueueStatus waiting =
                new io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerQueueStatus(
                        "t-1", "WAITING", 2, 40, 1, 3L, null);
        when(queueService.check(USER, 1, "t-0"))
                .thenReturn(
                        io.pinkspider.leveluptogethermvp.towerservice.application.queue
                                .TowerQueueService.Gate.waitWith(waiting));
        TowerEnterResponse queued = service.enter(USER, 1, "t-0");
        assertThat(queued.queued()).isTrue();
        assertThat(queued.queue()).isEqualTo(waiting);
        assertThat(queued.instanceId()).isNull();
        assertThat(queued.population()).isEqualTo(3L);
        verify(occupantRepository, never()).save(any());

        // 직접 입장 자리(다른 층) → 층 이동은 대기열을 거치지 않는다
        TowerOccupant inside = occupant(5L, 9L, 2, USER, TowerOccupantKind.DIRECT, true);
        when(occupantRepository.findByUserId(USER)).thenReturn(Optional.of(inside));
        org.mockito.Mockito.clearInvocations(queueService);
        TowerEnterResponse moved = service.enter(USER, 1, null);
        assertThat(moved.queued()).isFalse();
        verify(queueService, never()).check(any(), anyInt(), any());

        // 자동 착석만 한 유저는 새 입장 — 대기열 판정을 받는다
        TowerOccupant auto = occupant(6L, 9L, 2, USER, TowerOccupantKind.AUTO_SEATED, true);
        when(occupantRepository.findByUserId(USER)).thenReturn(Optional.of(auto));
        assertThat(service.enter(USER, 1, "t-0").queued()).isTrue();
    }

    @Test
    @DisplayName("LUT-705: 방문객은 문(제재·IP 빈도)을 지나 세션 키·이름 번호를 받는다 · 거절은 그대로 전달 · 로그인 유저는 문을 거치지 않는다")
    void visitorGateAdmit() {
        stub(f1);
        when(allocator.pickForDirectEntry(eq(f1), any())).thenReturn(instance(10L, 1));
        io.pinkspider.leveluptogethermvp.towerservice.application.visitor.TowerVisitorContext
                context =
                        io.pinkspider.leveluptogethermvp.towerservice.application.visitor
                                .TowerVisitorContext.of("vk-1", "dev-1", "1.2.3.4", "UA");
        TowerEnterResponse res = service.enter(null, 1, null, context);
        verify(visitorGate).admit(context);
        assertThat(res.visitorKey()).isEqualTo("vk-1");
        assertThat(res.visitorNo()).isEqualTo(4821);

        when(visitorGate.admit(any()))
                .thenThrow(
                        io.pinkspider.leveluptogethermvp.towerservice.application.exception
                                .TowerVisitorException.entryBanned());
        assertThatThrownBy(() -> service.enter(null, 1, null, context))
                .hasFieldOrPropertyWithValue("code", "150501");

        org.mockito.Mockito.clearInvocations(visitorGate);
        when(gamificationQueryFacade.getUserLevel(USER)).thenReturn(1);
        service.enter(USER, 1, null, context);
        verify(visitorGate, never()).admit(any());
    }

    @Test
    @DisplayName("LUT-705: 같은 세션 방문객 — 같은 층이면 그 자리로 복귀, 다른 층이면 옮기고(키·번호 그대로) 둘 다 대기열을 거치지 않는다")
    void visitorSessionResumeAndMove() {
        stub(f1);
        TowerOccupant inside = occupant(7L, 9L, 1, "vk-1", TowerOccupantKind.VISITOR, false);
        when(occupantRepository.findByVisitorKey("vk-1")).thenReturn(Optional.of(inside));
        TowerEnterResponse resumed = service.enter(null, 1);
        assertThat(resumed.resumed()).isTrue();
        assertThat(resumed.occupantId()).isEqualTo(7L);
        assertThat(resumed.visitorKey()).isEqualTo("vk-1");
        verify(queueService, never()).check(any(), anyInt(), any());
        verify(occupantRepository, never()).save(any());

        TowerOccupant elsewhere = occupant(8L, 11L, 2, "vk-1", TowerOccupantKind.VISITOR, false);
        when(occupantRepository.findByVisitorKey("vk-1")).thenReturn(Optional.of(elsewhere));
        when(allocator.pickForDirectEntry(eq(f1), any())).thenReturn(instance(10L, 1));
        TowerEnterResponse moved = service.enter(null, 1);
        verify(allocator).remove(elsewhere);
        ArgumentCaptor<TowerOccupant> captor = ArgumentCaptor.forClass(TowerOccupant.class);
        verify(occupantRepository).save(captor.capture());
        assertThat(captor.getValue().getVisitorKey()).isEqualTo("vk-1");
        assertThat(captor.getValue().getVisitorNo()).isEqualTo(4821);
        assertThat(moved.resumed()).isFalse();
        verify(queueService, never()).check(any(), anyInt(), any());
    }

    @Test
    @DisplayName("LUT-713: 내 자리 — 점유자가 있으면 층·자동 착석·미션 중, 없으면 자리 없음")
    void mySeat() {
        TowerOccupant auto = occupant(5L, 9L, 3, USER, TowerOccupantKind.AUTO_SEATED, true);
        when(occupantRepository.findByUserId(USER)).thenReturn(Optional.of(auto));
        io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerMySeatResponse seat =
                service.mySeat(USER);
        assertThat(seat.seated()).isTrue();
        assertThat(seat.floorNumber()).isEqualTo(3);
        assertThat(seat.autoSeated()).isTrue();
        assertThat(seat.missionActive()).isTrue();

        when(occupantRepository.findByUserId(USER)).thenReturn(Optional.empty());
        assertThat(service.mySeat(USER).seated()).isFalse();
        assertThat(service.mySeat(USER).floorNumber()).isNull();
    }

    @Test
    @DisplayName("LUT-734 first_visit: 로그인 유저는 방문 행을 넣은 뒤 안내 판정 결과 그대로, 방문객은 null")
    void firstVisit() {
        stub(f2Unlimited);
        when(guideService.markSeenIfNew(eq(USER), eq(f2Unlimited), any())).thenReturn(true, false);
        assertThat(service.enter(USER, 2).firstVisit()).isTrue();
        assertThat(service.enter(USER, 2).firstVisit()).isFalse();
        org.mockito.InOrder order = org.mockito.Mockito.inOrder(visitRepository, guideService);
        order.verify(visitRepository).insertIfAbsent(eq(USER), eq(2), any());
        order.verify(guideService).markSeenIfNew(eq(USER), eq(f2Unlimited), any());

        stub(f1);
        when(allocator.pickForDirectEntry(eq(f1), any())).thenReturn(instance(10L, 1));
        assertThat(service.enter(null, 1).firstVisit()).isNull();
    }
}
