package io.pinkspider.leveluptogethermvp.towerservice.presence;

import static io.pinkspider.leveluptogethermvp.towerservice.TowerTestFixtures.instance;
import static io.pinkspider.leveluptogethermvp.towerservice.TowerTestFixtures.occupant;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.leveluptogethermvp.towerservice.application.TowerInstanceAllocator;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerOccupant;
import io.pinkspider.leveluptogethermvp.towerservice.domain.enums.TowerOccupantKind;
import io.pinkspider.leveluptogethermvp.towerservice.infrastructure.TowerInstanceRepository;
import io.pinkspider.leveluptogethermvp.towerservice.infrastructure.TowerOccupantRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** LUT-631: join 본인 확인·끊김 규칙·방문객 무조작 정리 */
class TowerConnectionServiceTest {

    private final TowerPresenceStore store = mock(TowerPresenceStore.class);
    private final TowerPresenceService presenceService = mock(TowerPresenceService.class);
    private final TowerInstanceAllocator allocator = mock(TowerInstanceAllocator.class);
    private final TowerOccupantRepository occupantRepository = mock(TowerOccupantRepository.class);
    private final TowerInstanceRepository instanceRepository = mock(TowerInstanceRepository.class);

    private TowerConnectionService service;

    private final TowerOccupant user = occupant(7L, 9L, 1, "u1", TowerOccupantKind.DIRECT, false);
    private final TowerOccupant visitor =
            occupant(8L, 9L, 1, "key-1", TowerOccupantKind.VISITOR, false);

    @BeforeEach
    void setUp() {
        service =
                new TowerConnectionService(
                        store,
                        presenceService,
                        allocator,
                        occupantRepository,
                        instanceRepository,
                        10);
        when(occupantRepository.findById(7L)).thenReturn(Optional.of(user));
        when(occupantRepository.findById(8L)).thenReturn(Optional.of(visitor));
        when(presenceService.join(any()))
                .thenAnswer(inv -> presenceOf((TowerOccupant) inv.getArgument(0)));
    }

    private static TowerPresence presenceOf(TowerOccupant o) {
        return new TowerPresence(
                o.getId(),
                o.getInstanceId(),
                o.getFloorNumber(),
                o.getUserId(),
                o.getKind().name(),
                null,
                null,
                null,
                null,
                List.of(),
                1,
                1,
                "REST",
                true,
                false,
                LocalDateTime.now(),
                null,
                null);
    }

    @Test
    @DisplayName("join: 로그인 유저는 principal 로, 방문객은 visitor_key 로 본인 확인 — 맞으면 세션을 묶고 presence join")
    void join() {
        assertThat(service.join("s1", 9L, 7L, "u1", null)).isPresent();
        verify(store).bindSession("s1", new TowerSessionBinding(9L, 7L));
        verify(presenceService).join(user);

        assertThat(service.join("s2", 9L, 8L, null, "key-1")).isPresent();
        verify(store).bindSession("s2", new TowerSessionBinding(9L, 8L));
    }

    @Test
    @DisplayName("join 거절: 다른 유저·틀린 키·다른 방·없는 점유자·누락 값")
    void joinRejected() {
        assertThat(service.join("s1", 9L, 7L, "other", null)).isEmpty();
        assertThat(service.join("s1", 9L, 8L, null, "wrong")).isEmpty();
        assertThat(service.join("s1", 9L, 8L, "u1", null)).isEmpty(); // 방문객 자리에 로그인 유저
        assertThat(service.join("s1", 10L, 7L, "u1", null)).isEmpty(); // 다른 방
        when(occupantRepository.findById(99L)).thenReturn(Optional.empty());
        assertThat(service.join("s1", 9L, 99L, "u1", null)).isEmpty();
        assertThat(service.join(null, 9L, 7L, "u1", null)).isEmpty();
        verify(store, never()).bindSession(any(), any());
    }

    @Test
    @DisplayName("move: 이 세션에 묶인 점유자만, 그 방 안에서만")
    void move() {
        when(store.findBinding("s1")).thenReturn(Optional.of(new TowerSessionBinding(9L, 7L)));
        when(presenceService.move(9L, 7L, 10, 20))
                .thenReturn(Optional.of(new WalkableMask.Point(10, 20)));
        assertThat(service.move("s1", 9L, 10, 20)).contains(new WalkableMask.Point(10, 20));
        assertThat(service.move("s1", 10L, 10, 20)).isEmpty();
        when(store.findBinding("s9")).thenReturn(Optional.empty());
        assertThat(service.move("s9", 9L, 10, 20)).isEmpty();
    }

    @Test
    @DisplayName("끊김: 미션 중 로그인 유저는 자리 유지(표시만), 휴식 중 유저·방문객은 즉시 퇴장, 묶이지 않은 세션은 무시")
    void disconnect() {
        TowerOccupant inMission = occupant(7L, 9L, 1, "u1", TowerOccupantKind.DIRECT, true);
        when(occupantRepository.findById(7L)).thenReturn(Optional.of(inMission));
        when(store.unbindSession("s1")).thenReturn(Optional.of(new TowerSessionBinding(9L, 7L)));
        service.disconnect("s1");
        verify(presenceService).markDisconnected(9L, 7L);
        verify(allocator, never()).remove(inMission);

        when(store.unbindSession("s2")).thenReturn(Optional.of(new TowerSessionBinding(9L, 8L)));
        service.disconnect("s2");
        verify(allocator).remove(visitor);

        when(occupantRepository.findById(7L)).thenReturn(Optional.of(user));
        when(store.unbindSession("s3")).thenReturn(Optional.of(new TowerSessionBinding(9L, 7L)));
        service.disconnect("s3");
        verify(allocator).remove(user);

        when(store.unbindSession("s4")).thenReturn(Optional.empty());
        service.disconnect("s4");
        when(store.unbindSession("s5")).thenReturn(Optional.of(new TowerSessionBinding(9L, 99L)));
        when(occupantRepository.findById(99L)).thenReturn(Optional.empty());
        service.disconnect("s5");
        verify(store).delete(9L, 99L);
        service.disconnect(null);
    }

    @Test
    @DisplayName("정리: 10분 무조작 방문객 퇴장 · 고아 presence 삭제 · 끊긴 채 남은 휴식 유저 퇴장 · 미션 중 유저·최근 방문객은 유지")
    void cleanup() {
        when(instanceRepository.findAll()).thenReturn(List.of(instance(9L, 1)));
        LocalDateTime old = LocalDateTime.now().minusMinutes(11);
        LocalDateTime fresh = LocalDateTime.now().minusMinutes(1);
        TowerPresence idleVisitor =
                new TowerPresence(
                        8L, 9L, 1, null, "VISITOR", 1234, null, null, null, List.of(), 1, 1, "REST",
                        true, false, old, null, null);
        TowerPresence orphan =
                new TowerPresence(
                        99L, 9L, 1, "gone", "DIRECT", null, null, null, 1, List.of(), 1, 1, "REST",
                        true, false, old, null, null);
        TowerOccupant inMission = occupant(7L, 9L, 1, "u1", TowerOccupantKind.DIRECT, true);
        TowerPresence missionStale =
                new TowerPresence(
                        7L, 9L, 1, "u1", "DIRECT", null, null, null, 1, List.of(), 1, 1, "MISSION",
                        false, false, old, null, null);
        TowerOccupant resting = occupant(6L, 9L, 1, "u2", TowerOccupantKind.DIRECT, false);
        TowerPresence restStale =
                new TowerPresence(
                        6L, 9L, 1, "u2", "DIRECT", null, null, null, 1, List.of(), 1, 1, "REST",
                        false, false, old, null, null);
        TowerPresence freshVisitor =
                new TowerPresence(
                        5L, 9L, 1, null, "VISITOR", 5555, null, null, null, List.of(), 1, 1, "REST",
                        true, false, fresh, null, null);
        TowerOccupant freshVisitorRow = occupant(5L, 9L, 1, "k5", TowerOccupantKind.VISITOR, false);
        when(presenceService.listRaw(9L))
                .thenReturn(List.of(idleVisitor, orphan, missionStale, restStale, freshVisitor));
        when(occupantRepository.findById(7L)).thenReturn(Optional.of(inMission));
        when(occupantRepository.findById(6L)).thenReturn(Optional.of(resting));
        when(occupantRepository.findById(5L)).thenReturn(Optional.of(freshVisitorRow));
        when(occupantRepository.findById(99L)).thenReturn(Optional.empty());

        assertThat(service.cleanup()).isEqualTo(3);
        verify(allocator).remove(visitor);
        verify(presenceService).deleteOrphan(orphan);
        verify(allocator).remove(resting);
        verify(allocator, never()).remove(inMission);
        verify(allocator, never()).remove(freshVisitorRow);
    }

    @Test
    @DisplayName("LUT-701 정리: 연결 집합에 남은 점유자 행 없는 항목은 동시 접속 수에서 뺀다")
    void cleanupConnectedOrphans() {
        when(instanceRepository.findAll()).thenReturn(List.of());
        when(store.connectedOccupantIds()).thenReturn(java.util.Set.of(7L, 404L));
        when(occupantRepository.findById(404L)).thenReturn(Optional.empty());
        assertThat(service.cleanup()).isEqualTo(1);
        verify(store).removeConnected(404L);
        verify(store, never()).removeConnected(7L);
    }
}
