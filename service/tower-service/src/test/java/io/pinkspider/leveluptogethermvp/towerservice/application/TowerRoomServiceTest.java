package io.pinkspider.leveluptogethermvp.towerservice.application;

import static io.pinkspider.leveluptogethermvp.towerservice.TowerTestFixtures.floor;
import static io.pinkspider.leveluptogethermvp.towerservice.TowerTestFixtures.occupant;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.leveluptogethermvp.towerservice.application.exception.TowerEntryException;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerEnterResponse;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerRoomListResponse;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerRoomView;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerFloor;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerInstance;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerOccupant;
import io.pinkspider.leveluptogethermvp.towerservice.domain.enums.TowerOccupantKind;
import io.pinkspider.leveluptogethermvp.towerservice.infrastructure.InstanceLoad;
import io.pinkspider.leveluptogethermvp.towerservice.infrastructure.TowerFloorRepository;
import io.pinkspider.leveluptogethermvp.towerservice.infrastructure.TowerInstanceRepository;
import io.pinkspider.leveluptogethermvp.towerservice.infrastructure.TowerOccupantRepository;
import io.pinkspider.leveluptogethermvp.towerservice.presence.TowerPresenceService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TowerRoomServiceTest {

    private static final String USER = "u1";

    @Mock private TowerFloorRepository floorRepository;
    @Mock private TowerInstanceRepository instanceRepository;
    @Mock private TowerOccupantRepository occupantRepository;
    @Mock private TowerInstanceAllocator allocator;
    @Mock private TowerPresenceService presenceService;
    @Mock private TowerPopulationService populationService;
    @Mock private TowerRoomMoveLimiter moveLimiter;

    @InjectMocks private TowerRoomService service;

    private final TowerFloor f1 = floor(1L, 1, null, true, true); // 정원 30

    private static TowerInstance room(long id, int floorNumber, Integer no) {
        return TowerInstance.builder().id(id).floorNumber(floorNumber).instanceNo(no).build();
    }

    @BeforeEach
    void setUp() {
        when(floorRepository.findByFloorNumber(1)).thenReturn(Optional.of(f1));
        when(floorRepository.findByFloorNumberForUpdate(1)).thenReturn(Optional.of(f1));
        when(populationService.of(1)).thenReturn(40L);
    }

    @Test
    @DisplayName("방 목록: 방 번호 순 · 정원 인원(자동 착석 제외)·전체·가득 참·내 방 · 층 전체 인원 = 모든 종류 합")
    void list() {
        TowerOccupant me = occupant(7L, 11L, 1, USER, TowerOccupantKind.DIRECT, true);
        when(occupantRepository.findByUserId(USER)).thenReturn(Optional.of(me));
        when(instanceRepository.findAllByFloorNumberOrderByIdAsc(1))
                .thenReturn(List.of(room(10L, 1, 2), room(11L, 1, 1), room(12L, 1, null)));
        when(occupantRepository.loadsByFloor(1, TowerOccupantKind.AUTO_SEATED))
                .thenReturn(
                        List.of(new InstanceLoad(10L, 30L, 32L), new InstanceLoad(11L, 8L, 9L)));

        TowerRoomListResponse res = service.list(1, USER, null);

        assertThat(res.floorNumber()).isEqualTo(1);
        assertThat(res.population()).isEqualTo(41L);
        assertThat(res.capacity()).isEqualTo(30);
        assertThat(res.instances()).extracting(TowerRoomView::instanceNo).containsExactly(1, 2, 3);
        TowerRoomView mine = res.instances().get(0);
        assertThat(mine.instanceId()).isEqualTo(11L);
        assertThat(mine.current()).isTrue();
        assertThat(mine.full()).isFalse();
        assertThat(mine.population()).isEqualTo(8L);
        assertThat(mine.totalPopulation()).isEqualTo(9L);
        TowerRoomView full = res.instances().get(1);
        assertThat(full.full()).isTrue();
        assertThat(full.current()).isFalse();
        assertThat(res.instances().get(2).population()).isZero(); // 아직 아무도 없는 방(번호 없는 옛 방은 빈 번호 3)
    }

    @Test
    @DisplayName("방 목록 거절: 다른 층에 있음·없는 사람·자동 착석만·키 없는 방문객 → 403 150205 · 방문객은 visitor_key 로")
    void listRejected() {
        when(occupantRepository.findByUserId(USER))
                .thenReturn(
                        Optional.of(occupant(7L, 21L, 2, USER, TowerOccupantKind.DIRECT, false)));
        assertCode(() -> service.list(1, USER, null), TowerEntryException.NOT_IN_INSTANCE);
        when(occupantRepository.findByUserId("ghost")).thenReturn(Optional.empty());
        assertCode(() -> service.list(1, "ghost", null), TowerEntryException.NOT_IN_INSTANCE);
        when(occupantRepository.findByUserId("auto"))
                .thenReturn(
                        Optional.of(
                                occupant(8L, 11L, 1, "auto", TowerOccupantKind.AUTO_SEATED, true)));
        assertCode(() -> service.list(1, "auto", null), TowerEntryException.NOT_IN_INSTANCE);
        assertCode(() -> service.list(1, null, " "), TowerEntryException.NOT_IN_INSTANCE);
        assertCode(() -> service.list(1, null, null), TowerEntryException.NOT_IN_INSTANCE);

        when(occupantRepository.findByVisitorKey("vk"))
                .thenReturn(
                        Optional.of(occupant(9L, 11L, 1, "vk", TowerOccupantKind.VISITOR, false)));
        when(instanceRepository.findAllByFloorNumberOrderByIdAsc(1))
                .thenReturn(List.of(room(11L, 1, 1)));
        when(occupantRepository.loadsByFloor(1, TowerOccupantKind.AUTO_SEATED))
                .thenReturn(List.of());
        assertThat(service.list(1, null, "vk").instances().get(0).current()).isTrue();

        when(floorRepository.findByFloorNumber(1)).thenReturn(Optional.empty());
        assertCode(() -> service.list(1, null, "vk"), TowerEntryException.FLOOR_NOT_AVAILABLE);
    }

    @Test
    @DisplayName(
            "방 이동: 같은 층 다른 방 — 점유자 방 바꿈·LEAVE/ENTER(presence)·빈 방 닫기·자동 착석 넘침 정리·10초 표시, 미션 중 그대로 ·"
                    + " 응답은 입장 모양")
    void moveIn() {
        TowerOccupant me = occupant(7L, 11L, 1, USER, TowerOccupantKind.DIRECT, true);
        when(occupantRepository.findByUserId(USER)).thenReturn(Optional.of(me));
        when(instanceRepository.findById(12L)).thenReturn(Optional.of(room(12L, 1, 2)));
        when(moveLimiter.remainingSeconds("u:u1")).thenReturn(0L);
        when(allocator.countedOccupants(12L)).thenReturn(29L);

        TowerEnterResponse res = service.moveIn(12L, USER, null);

        assertThat(res.instanceId()).isEqualTo(12L);
        assertThat(res.occupantId()).isEqualTo(7L);
        assertThat(res.missionActive()).isTrue();
        assertThat(res.resumed()).isTrue();
        assertThat(res.population()).isEqualTo(40L);
        assertThat(res.seatX()).isEqualTo(100);
        assertThat(me.getInstanceId()).isEqualTo(12L);
        verify(presenceService).moveInstance(me, 11L, 12L);
        verify(allocator).closeIfEmpty(11L);
        verify(allocator).relocateAutoSeatedOverflow(f1, 12L, me.getLastActiveAt());
        verify(moveLimiter).markMoved("u:u1");
    }

    @Test
    @DisplayName("같은 방이면 아무것도 하지 않는다(10초 제한도 세지 않음) · 방문객은 v:키 로 제한")
    void sameRoomAndVisitor() {
        TowerOccupant me = occupant(7L, 11L, 1, USER, TowerOccupantKind.DIRECT, false);
        when(occupantRepository.findByUserId(USER)).thenReturn(Optional.of(me));
        when(instanceRepository.findById(11L)).thenReturn(Optional.of(room(11L, 1, 1)));
        assertThat(service.moveIn(11L, USER, null).instanceId()).isEqualTo(11L);
        verify(presenceService, never()).moveInstance(any(), any(), any());
        verify(moveLimiter, never()).markMoved(anyString());

        TowerOccupant visitor = occupant(9L, 11L, 1, "vk", TowerOccupantKind.VISITOR, false);
        when(occupantRepository.findByVisitorKey("vk")).thenReturn(Optional.of(visitor));
        when(instanceRepository.findById(12L)).thenReturn(Optional.of(room(12L, 1, 2)));
        when(allocator.countedOccupants(12L)).thenReturn(0L);
        TowerEnterResponse res = service.moveIn(12L, null, "vk");
        assertThat(res.visitorKey()).isEqualTo("vk");
        verify(moveLimiter).markMoved("v:vk");
    }

    @Test
    @DisplayName(
            "방 이동 거절: 없는 방 404 150210 · 다른 층 400 150209 · 10초 안 429 150208(남은 초) · 가득 참 409 150207"
                    + " — 다른 방으로 대신 보내지 않는다")
    void moveRejected() {
        TowerOccupant me = occupant(7L, 11L, 1, USER, TowerOccupantKind.DIRECT, false);
        when(occupantRepository.findByUserId(USER)).thenReturn(Optional.of(me));
        when(instanceRepository.findById(99L)).thenReturn(Optional.empty());
        assertCode(() -> service.moveIn(99L, USER, null), TowerEntryException.ROOM_NOT_FOUND);
        when(instanceRepository.findById(21L)).thenReturn(Optional.of(room(21L, 2, 1)));
        assertCode(() -> service.moveIn(21L, USER, null), TowerEntryException.ROOM_OTHER_FLOOR);

        when(instanceRepository.findById(12L)).thenReturn(Optional.of(room(12L, 1, 2)));
        when(moveLimiter.remainingSeconds("u:u1")).thenReturn(6L);
        assertThatThrownBy(() -> service.moveIn(12L, USER, null))
                .isInstanceOf(TowerEntryException.class)
                .satisfies(
                        e -> {
                            TowerEntryException te = (TowerEntryException) e;
                            assertThat(te.getCode())
                                    .isEqualTo(TowerEntryException.ROOM_MOVE_TOO_SOON);
                            assertThat(te.getMessageArgs()).containsExactly(6L);
                        });

        when(moveLimiter.remainingSeconds("u:u1")).thenReturn(0L);
        when(allocator.countedOccupants(12L)).thenReturn(30L);
        assertCode(() -> service.moveIn(12L, USER, null), TowerEntryException.ROOM_FULL);
        assertThat(me.getInstanceId()).isEqualTo(11L);
        verify(presenceService, never()).moveInstance(any(), any(), any());

        when(floorRepository.findByFloorNumberForUpdate(1)).thenReturn(Optional.empty());
        assertCode(() -> service.moveIn(12L, USER, null), TowerEntryException.FLOOR_NOT_AVAILABLE);
        when(occupantRepository.findByUserId("auto"))
                .thenReturn(
                        Optional.of(
                                occupant(8L, 11L, 1, "auto", TowerOccupantKind.AUTO_SEATED, true)));
        assertCode(() -> service.moveIn(12L, "auto", null), TowerEntryException.NOT_IN_INSTANCE);
    }

    private static void assertCode(Runnable call, String code) {
        assertThatThrownBy(call::run)
                .isInstanceOf(TowerEntryException.class)
                .extracting("code")
                .isEqualTo(code);
    }
}
