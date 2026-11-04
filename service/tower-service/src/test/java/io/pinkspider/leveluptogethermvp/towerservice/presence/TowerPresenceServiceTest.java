package io.pinkspider.leveluptogethermvp.towerservice.presence;

import static io.pinkspider.leveluptogethermvp.towerservice.TowerTestFixtures.floor;
import static io.pinkspider.leveluptogethermvp.towerservice.TowerTestFixtures.occupant;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.global.enums.TitleRarity;
import io.pinkspider.global.facade.GamificationQueryFacade;
import io.pinkspider.global.facade.UserQueryFacade;
import io.pinkspider.global.facade.dto.UserItemDto;
import io.pinkspider.global.facade.dto.UserProfileInfo;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerInstanceEvent;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerFloor;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerOccupant;
import io.pinkspider.leveluptogethermvp.towerservice.domain.enums.TowerOccupantKind;
import io.pinkspider.leveluptogethermvp.towerservice.infrastructure.TowerFloorRepository;
import io.pinkspider.leveluptogethermvp.towerservice.infrastructure.TowerOccupantRepository;
import io.pinkspider.leveluptogethermvp.towerservice.realtime.TowerRealtimePublisher;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

/** LUT-631: presence 등록·퇴장·이동(마스크 보정)·재접속 복원·레벨 변경·스냅샷 차단 필터 */
class TowerPresenceServiceTest {

    private final TowerPresenceStore store = mock(TowerPresenceStore.class);
    private final TowerRealtimePublisher publisher = mock(TowerRealtimePublisher.class);
    private final TowerFloorMaskCache maskCache = mock(TowerFloorMaskCache.class);
    private final TowerFloorRepository floorRepository = mock(TowerFloorRepository.class);
    private final TowerOccupantRepository occupantRepository = mock(TowerOccupantRepository.class);
    private final UserQueryFacade userFacade = mock(UserQueryFacade.class);
    private final GamificationQueryFacade gamificationFacade = mock(GamificationQueryFacade.class);

    private TowerPresenceService service;
    private final TowerFloor f1 = floor(1L, 1, null, true, true); // 입장 지점 (100,200)
    private WalkableMask mask;

    @BeforeEach
    void setUp() {
        service =
                new TowerPresenceService(
                        store,
                        publisher,
                        maskCache,
                        floorRepository,
                        occupantRepository,
                        userFacade,
                        gamificationFacade,
                        50);
        BufferedImage img = new BufferedImage(400, 400, BufferedImage.TYPE_INT_ARGB);
        for (int x = 50; x < 300; x++) {
            for (int y = 150; y < 300; y++) {
                img.setRGB(x, y, new Color(255, 0, 0, 255).getRGB());
            }
        }
        mask = new WalkableMask(img, 400, 400);
        when(floorRepository.findByFloorNumber(1)).thenReturn(Optional.of(f1));
        when(maskCache.get(f1)).thenReturn(Optional.of(mask));
        when(store.find(any(), any())).thenReturn(Optional.empty());
    }

    private TowerInstanceEvent published(Long instanceId, int index) {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(publisher, org.mockito.Mockito.atLeast(index + 1))
                .publishToInstance(eq(instanceId), captor.capture());
        return (TowerInstanceEvent) captor.getAllValues().get(index);
    }

    @Test
    @DisplayName("등록: 로그인 유저는 프로필(썸네일·닉네임)·레벨·장착 아이템을 읽어 presence 에 넣고 ENTER 를 보낸다 · 조회 실패는 기본값")
    void registerUser() {
        TowerOccupant o = occupant(7L, 9L, 1, "u1", TowerOccupantKind.DIRECT, true);
        when(userFacade.getUserProfile("u1"))
                .thenReturn(new UserProfileInfo("u1", "닉", "pic.png", 3, null, null, null));
        when(gamificationFacade.getUserLevel("u1")).thenReturn(12);
        when(gamificationFacade.getEquippedItemsByUserId("u1"))
                .thenReturn(
                        List.of(
                                new UserItemDto(
                                        1L,
                                        "u1",
                                        5L,
                                        "날개",
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        "BASIC",
                                        TitleRarity.RARE,
                                        "wing.png",
                                        "BACK",
                                        null,
                                        true,
                                        null)));

        TowerPresence p = service.register(o, false);

        assertThat(p.nickname()).isEqualTo("닉");
        assertThat(p.picture()).isEqualTo("pic.png");
        assertThat(p.level()).isEqualTo(12);
        assertThat(p.items())
                .containsExactly(new TowerAvatarItem("BASIC", "wing.png", "BACK", null, "RARE"));
        assertThat(p.state()).isEqualTo(TowerPresence.STATE_MISSION);
        assertThat(p.isConnected()).isFalse();
        assertThat(p.x()).isEqualTo(100);
        verify(store).save(p);
        TowerInstanceEvent enter = published(9L, 0);
        assertThat(enter.type()).isEqualTo(TowerInstanceEvent.TYPE_ENTER);
        assertThat(enter.occupant().nickname()).isEqualTo("닉");
        assertThat(enter.occupant().level()).isEqualTo(12);

        when(userFacade.getUserProfile("u2")).thenThrow(new RuntimeException("down"));
        when(gamificationFacade.getUserLevel("u2")).thenThrow(new RuntimeException("down"));
        when(gamificationFacade.getEquippedItemsByUserId("u2"))
                .thenThrow(new RuntimeException("down"));
        TowerPresence fallback =
                service.register(occupant(8L, 9L, 1, "u2", TowerOccupantKind.DIRECT, false), true);
        assertThat(fallback.level()).isEqualTo(1);
        assertThat(fallback.items()).isEmpty();
        assertThat(fallback.state()).isEqualTo(TowerPresence.STATE_REST);
    }

    @Test
    @DisplayName("등록: 방문객은 프로필 조회 없이 visitor_no 만 — 레벨 없음")
    void registerVisitor() {
        TowerOccupant v = TowerOccupant.visitor(9L, 1, "k", 4821, 100, 200, LocalDateTime.now());
        ReflectionTestUtils.setField(v, "id", 3L);
        TowerPresence p = service.register(v, true);
        assertThat(p.level()).isNull();
        assertThat(p.visitorNo()).isEqualTo(4821);
        assertThat(p.isVisitor()).isTrue();
        assertThat(p.toView().userId()).isNull();
        verify(userFacade, never()).getUserProfile(any());
        // LUT-705: 방문객 세션 태그(키는 내리지 않는다) — 이동해도 유지
        String tag =
                io.pinkspider.leveluptogethermvp.towerservice.application.visitor.TowerVisitorTag
                        .of("k");
        assertThat(p.visitorTag()).isEqualTo(tag);
        assertThat(p.toView().visitorTag()).isEqualTo(tag);
        assertThat(p.withPosition(1, 2, LocalDateTime.now()).visitorTag()).isEqualTo(tag);
    }

    @Test
    @DisplayName("퇴장: presence·세션을 지우고 LEAVE · 자리 양보: 옛 방 LEAVE + 새 방 ENTER")
    void unregisterAndMoveInstance() {
        TowerOccupant o = occupant(7L, 9L, 1, "u1", TowerOccupantKind.AUTO_SEATED, true);
        service.unregister(o);
        verify(store).delete(9L, 7L);
        verify(store).unbindOccupant(7L);
        assertThat(published(9L, 0).type()).isEqualTo(TowerInstanceEvent.TYPE_LEAVE);

        TowerPresence existing =
                new TowerPresence(
                        7L,
                        9L,
                        1,
                        "u1",
                        "AUTO_SEATED",
                        null,
                        "닉",
                        null,
                        3,
                        List.of(),
                        120,
                        220,
                        "MISSION",
                        false,
                        true,
                        LocalDateTime.now(),
                        null);
        when(store.find(9L, 7L)).thenReturn(Optional.of(existing));
        service.moveInstance(o, 9L, 11L);
        verify(store).save(existing.withInstance(11L));
        assertThat(published(11L, 0).type()).isEqualTo(TowerInstanceEvent.TYPE_ENTER);
        assertThat(published(11L, 0).occupant().x()).isEqualTo(120);

        when(store.find(9L, 7L)).thenReturn(Optional.empty());
        service.moveInstance(o, 9L, 12L); // presence 가 없으면 새로 만든다
        assertThat(published(12L, 0).type()).isEqualTo(TowerInstanceEvent.TYPE_ENTER);
    }

    @Test
    @DisplayName("이동: 마스크 안은 그대로, 밖은 가장 가까운 안쪽으로 보정해 MOVE 로 알린다 · 너무 멀면 무시 · presence 없으면 무시")
    void move() {
        TowerPresence p =
                new TowerPresence(
                        7L,
                        9L,
                        1,
                        "u1",
                        "DIRECT",
                        null,
                        "닉",
                        null,
                        3,
                        List.of(),
                        100,
                        200,
                        "REST",
                        true,
                        false,
                        LocalDateTime.now(),
                        null);
        when(store.find(9L, 7L)).thenReturn(Optional.of(p));

        assertThat(service.move(9L, 7L, 200, 250)).contains(new WalkableMask.Point(200, 250));
        TowerInstanceEvent move = published(9L, 0);
        assertThat(move.type()).isEqualTo(TowerInstanceEvent.TYPE_MOVE);
        assertThat(move.x()).isEqualTo(200);

        Optional<WalkableMask.Point> snapped = service.move(9L, 7L, 320, 250); // 오른쪽 밖(300 경계)
        assertThat(snapped).isPresent();
        assertThat(mask.isWalkable(snapped.get().x(), snapped.get().y())).isTrue();
        assertThat(snapped.get().x()).isLessThan(300);

        assertThat(service.move(9L, 7L, 399, 10)).isEmpty(); // 반지름 50 밖
        assertThat(service.move(9L, 99L, 200, 250)).isEmpty();
        when(floorRepository.findByFloorNumber(1)).thenReturn(Optional.empty());
        assertThat(service.move(9L, 7L, 200, 250)).isEmpty();
    }

    @Test
    @DisplayName("재접속 join: 마지막 위치를 마스크로 재검증해 복원(MOVE), 밖이면 입장 지점 · presence 가 없으면 다시 만든다")
    void join() {
        TowerOccupant o = occupant(7L, 9L, 1, "u1", TowerOccupantKind.DIRECT, true);
        TowerPresence last =
                new TowerPresence(
                        7L,
                        9L,
                        1,
                        "u1",
                        "DIRECT",
                        null,
                        "닉",
                        null,
                        3,
                        List.of(),
                        250,
                        280,
                        "MISSION",
                        false,
                        false,
                        LocalDateTime.now(),
                        null);
        when(store.find(9L, 7L)).thenReturn(Optional.of(last));

        TowerPresence joined = service.join(o);
        assertThat(joined.isConnected()).isTrue();
        assertThat(joined.x()).isEqualTo(250);
        TowerInstanceEvent move = published(9L, 0);
        assertThat(move.type()).isEqualTo(TowerInstanceEvent.TYPE_MOVE);
        assertThat(move.x()).isEqualTo(250);

        TowerPresence outside = last.withPosition(10, 10, LocalDateTime.now()); // 마스크 밖
        when(store.find(9L, 7L)).thenReturn(Optional.of(outside));
        TowerPresence restored = service.join(o);
        assertThat(restored.x()).isEqualTo(100);
        assertThat(restored.y()).isEqualTo(200);

        when(store.find(9L, 7L)).thenReturn(Optional.empty());
        when(userFacade.getUserProfile("u1")).thenReturn(null);
        TowerPresence rebuilt = service.join(o);
        assertThat(rebuilt.occupantId()).isEqualTo(7L);
        assertThat(rebuilt.isConnected()).isTrue();

        when(floorRepository.findByFloorNumber(1)).thenReturn(Optional.empty());
        assertThat(service.join(o).x()).isEqualTo(100);
    }

    @Test
    @DisplayName("레벨 변경: 탑에 있으면 presence 갱신 + LEVEL 브로드캐스트, 없으면 false · 연결 여부·상태·끊김 표시")
    void levelAndConnected() {
        TowerOccupant o = occupant(7L, 9L, 1, "u1", TowerOccupantKind.DIRECT, true);
        when(occupantRepository.findByUserId("u1")).thenReturn(Optional.of(o));
        TowerPresence p =
                new TowerPresence(
                        7L,
                        9L,
                        1,
                        "u1",
                        "DIRECT",
                        null,
                        "닉",
                        null,
                        3,
                        List.of(),
                        100,
                        200,
                        "REST",
                        true,
                        false,
                        LocalDateTime.now(),
                        null);
        when(store.find(9L, 7L)).thenReturn(Optional.of(p));

        assertThat(service.levelChanged("u1", 13)).isTrue();
        verify(store).save(p.withLevel(13));
        TowerInstanceEvent level = published(9L, 0);
        assertThat(level.type()).isEqualTo(TowerInstanceEvent.TYPE_LEVEL);
        assertThat(level.level()).isEqualTo(13);
        assertThat(service.isConnected("u1")).isTrue();

        when(occupantRepository.findByUserId("nobody")).thenReturn(Optional.empty());
        assertThat(service.levelChanged("nobody", 2)).isFalse();
        assertThat(service.isConnected("nobody")).isFalse();

        service.updateState(o, TowerPresence.STATE_MISSION);
        verify(store).save(p.withState(TowerPresence.STATE_MISSION));
        service.markDisconnected(9L, 7L);
        ArgumentCaptor<TowerPresence> saved = ArgumentCaptor.forClass(TowerPresence.class);
        verify(store, org.mockito.Mockito.atLeast(3)).save(saved.capture());
        assertThat(saved.getAllValues().get(saved.getAllValues().size() - 1).isConnected())
                .isFalse();
    }

    @Test
    @DisplayName("스냅샷: 요청자와 차단 관계인 유저는 빠지고 나 자신·방문객은 남는다 · 연결·자동 착석 여부는 내리지 않는다")
    void snapshotFiltersBlocked() {
        TowerPresence me =
                new TowerPresence(
                        1L, 9L, 1, "me", "DIRECT", null, "나", null, 3, List.of(), 1, 1, "REST",
                        true, false, null, null);
        TowerPresence blocked =
                new TowerPresence(
                        2L, 9L, 1, "bad", "DIRECT", null, "차단", null, 3, List.of(), 2, 2, "REST",
                        true, false, null, null);
        TowerPresence visitor =
                new TowerPresence(
                        3L, 9L, 1, null, "VISITOR", 4821, null, null, null, List.of(), 3, 3, "REST",
                        true, false, null, null);
        TowerPresence other =
                new TowerPresence(
                        4L,
                        9L,
                        1,
                        "ok",
                        "AUTO_SEATED",
                        null,
                        "남",
                        null,
                        5,
                        List.of(),
                        4,
                        4,
                        "MISSION",
                        false,
                        true,
                        null,
                        null);
        when(store.listByInstance(9L)).thenReturn(List.of(me, blocked, visitor, other));
        when(userFacade.isBlockedBetween("me", "bad")).thenReturn(true);
        when(userFacade.isBlockedBetween("me", "ok")).thenReturn(false);

        List<TowerPresenceView> views = service.snapshot(9L, "me");
        assertThat(views).extracting(TowerPresenceView::occupantId).containsExactly(1L, 3L, 4L);
        assertThat(views.get(1).visitorNo()).isEqualTo(4821);
        assertThat(views.get(2).state()).isEqualTo("MISSION");

        assertThat(service.snapshot(9L, null)).hasSize(4); // 방문객 요청자는 필터 없음
        when(userFacade.isBlockedBetween("me", "bad")).thenThrow(new RuntimeException("down"));
        assertThat(service.snapshot(9L, "me")).hasSize(4); // 차단 조회 실패는 숨기지 않음
        assertThat(service.listRaw(9L)).hasSize(4);

        service.deleteOrphan(blocked);
        verify(store).delete(9L, 2L);
        assertThat(published(9L, 0).type()).isEqualTo(TowerInstanceEvent.TYPE_LEAVE);
    }

    @Test
    @DisplayName("마스크를 못 읽으면 이동은 화면 밖만 막고 그대로, 복원은 그대로")
    void withoutMask() {
        when(maskCache.get(f1)).thenReturn(Optional.empty());
        TowerPresence p =
                new TowerPresence(
                        7L,
                        9L,
                        1,
                        "u1",
                        "DIRECT",
                        null,
                        "닉",
                        null,
                        3,
                        List.of(),
                        100,
                        200,
                        "REST",
                        true,
                        false,
                        LocalDateTime.now(),
                        null);
        when(store.find(9L, 7L)).thenReturn(Optional.of(p));
        assertThat(service.move(9L, 7L, 5, 5)).contains(new WalkableMask.Point(5, 5));
        assertThat(service.move(9L, 7L, -1, 5)).isEmpty();
        assertThat(service.join(occupant(7L, 9L, 1, "u1", TowerOccupantKind.DIRECT, false)).x())
                .isEqualTo(100);
    }
}
