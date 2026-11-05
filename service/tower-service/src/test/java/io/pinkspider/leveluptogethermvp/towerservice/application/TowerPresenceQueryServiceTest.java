package io.pinkspider.leveluptogethermvp.towerservice.application;

import static io.pinkspider.leveluptogethermvp.towerservice.TowerTestFixtures.occupant;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.global.facade.GamificationQueryFacade;
import io.pinkspider.leveluptogethermvp.towerservice.TowerTestFixtures;
import io.pinkspider.leveluptogethermvp.towerservice.application.exception.TowerEntryException;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerNpcView;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerPresenceSnapshotResponse;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerTraceView;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerNpc;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerNpcCard;
import io.pinkspider.leveluptogethermvp.towerservice.domain.enums.TowerNpcCardLinkType;
import io.pinkspider.leveluptogethermvp.towerservice.domain.enums.TowerOccupantKind;
import io.pinkspider.leveluptogethermvp.towerservice.infrastructure.TowerFloorRepository;
import io.pinkspider.leveluptogethermvp.towerservice.infrastructure.TowerNpcRepository;
import io.pinkspider.leveluptogethermvp.towerservice.infrastructure.TowerOccupantRepository;
import io.pinkspider.leveluptogethermvp.towerservice.presence.TowerPresenceService;
import io.pinkspider.leveluptogethermvp.towerservice.presence.TowerPresenceView;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TowerPresenceQueryServiceTest {

    private final TowerOccupantRepository occupantRepository = mock(TowerOccupantRepository.class);
    private final TowerPresenceService presenceService = mock(TowerPresenceService.class);
    private final TowerFloorRepository floorRepository = mock(TowerFloorRepository.class);
    private final TowerNpcRepository npcRepository = mock(TowerNpcRepository.class);
    private final TowerSettingService settingService = mock(TowerSettingService.class);
    private final TowerTraceService traceService = mock(TowerTraceService.class);
    private final GamificationQueryFacade gamificationQueryFacade =
            mock(GamificationQueryFacade.class);
    private final TowerPresenceQueryService service =
            new TowerPresenceQueryService(
                    occupantRepository,
                    presenceService,
                    floorRepository,
                    npcRepository,
                    settingService,
                    traceService,
                    gamificationQueryFacade);

    {
        when(settingService.getNpcExposurePercent()).thenReturn(100);
        when(traceService.today(org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(TowerTraceView.EMPTY);
    }

    @Test
    @DisplayName("그 방의 점유자(로그인 유저·방문객 키)만 스냅샷을 받는다, 아니면 403 150205")
    void snapshot() {
        when(occupantRepository.findByUserId("u1"))
                .thenReturn(
                        Optional.of(occupant(7L, 9L, 2, "u1", TowerOccupantKind.DIRECT, false)));
        when(occupantRepository.findByVisitorKey("k1"))
                .thenReturn(
                        Optional.of(occupant(8L, 9L, 2, "k1", TowerOccupantKind.VISITOR, false)));
        List<TowerPresenceView> views =
                List.of(
                        new TowerPresenceView(
                                7L, "u1", "DIRECT", null, "닉", null, 3, List.of(), 1, 1, "REST",
                                null, null));
        when(presenceService.snapshot(9L, "u1")).thenReturn(views);
        when(presenceService.snapshot(9L, null)).thenReturn(views);

        TowerPresenceSnapshotResponse user = service.snapshot(9L, "u1", null);
        assertThat(user.me()).isEqualTo(7L);
        assertThat(user.floorNumber()).isEqualTo(2);
        assertThat(user.occupants()).isEqualTo(views);
        assertThat(service.snapshot(9L, null, "k1").me()).isEqualTo(8L);

        assertThatThrownBy(() -> service.snapshot(10L, "u1", null))
                .isInstanceOf(TowerEntryException.class)
                .extracting("code")
                .isEqualTo(TowerEntryException.NOT_IN_INSTANCE);
        when(occupantRepository.findByUserId("nobody")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.snapshot(9L, "nobody", null))
                .isInstanceOf(TowerEntryException.class);
        assertThatThrownBy(() -> service.snapshot(9L, null, " "))
                .isInstanceOf(TowerEntryException.class);
        assertThatThrownBy(() -> service.snapshot(9L, null, null))
                .isInstanceOf(TowerEntryException.class);
    }

    @Test
    @DisplayName("LUT-693: 공개 NPC 전원과 오늘 흔적을 함께 내린다 · 노출 비율 0 이면 NPC 없이 npc_shown=false")
    void npcsAndTraces() {
        when(occupantRepository.findByUserId("u1"))
                .thenReturn(
                        Optional.of(occupant(7L, 9L, 2, "u1", TowerOccupantKind.DIRECT, false)));
        when(presenceService.snapshot(9L, "u1")).thenReturn(List.of());
        when(floorRepository.findByFloorNumber(2))
                .thenReturn(Optional.of(TowerTestFixtures.floor(20L, 2, null, true, true)));
        when(npcRepository.findAllByFloorIdAndIsPublicTrueOrderBySortOrderAscIdAsc(20L))
                .thenReturn(
                        List.of(
                                TowerTestFixtures.npc(1L, 20L, true),
                                TowerTestFixtures.npc(2L, 20L, true)));
        TowerTraceView trace = new TowerTraceView(3, List.of(new TowerTraceView.Point(5, 6)));
        when(traceService.today(2)).thenReturn(trace);

        TowerPresenceSnapshotResponse shown = service.snapshot(9L, "u1", null);
        assertThat(shown.npcShown()).isTrue();
        assertThat(shown.npcs()).extracting(TowerNpcView::npcId).containsExactly(1L, 2L);
        assertThat(shown.npcs().get(0).lines()).hasSize(2);
        assertThat(shown.traces()).isEqualTo(trace);

        when(settingService.getNpcExposurePercent()).thenReturn(0);
        TowerPresenceSnapshotResponse hidden = service.snapshot(9L, "u1", null);
        assertThat(hidden.npcShown()).isFalse();
        assertThat(hidden.npcs()).isEmpty();
        assertThat(hidden.traces()).isEqualTo(trace);

        // 층 행이 없으면(삭제 직후 등) NPC 없음
        when(settingService.getNpcExposurePercent()).thenReturn(100);
        when(floorRepository.findByFloorNumber(2)).thenReturn(Optional.empty());
        assertThat(service.snapshot(9L, "u1", null).npcs()).isEmpty();
    }

    @Test
    @DisplayName("LUT-693 A/B: 같은 유저는 늘 같은 그룹, 비율만큼 나뉜다 · 대상이 없으면 숨김")
    void npcGroup() {
        when(settingService.getNpcExposurePercent()).thenReturn(50);
        int shown = 0;
        for (int i = 0; i < 1000; i++) {
            String user = "user-" + i;
            boolean first = service.isNpcShown(user);
            assertThat(service.isNpcShown(user)).isEqualTo(first);
            if (first) shown++;
        }
        assertThat(shown).isBetween(400, 600);
        assertThat(service.isNpcShown(null)).isFalse();
        when(settingService.getNpcExposurePercent()).thenReturn(100);
        assertThat(service.isNpcShown(null)).isTrue();
    }

    private static TowerNpc npcWithCard(long id, TowerNpcCard card) {
        TowerNpc npc = TowerTestFixtures.npc(id, 20L, true);
        npc.updateCard(card);
        return npc;
    }

    @Test
    @DisplayName("LUT-733 창 묶음: 기간 밖·판매 중 아닌 아이템·없는 이벤트면 뺀다 · 대상 확인은 종류별 한 번 · 실패하면 그 종류만 뺀다")
    void cards() {
        LocalDate today = LocalDate.of(2026, 11, 5);
        TowerNpc shopOk =
                npcWithCard(
                        1L,
                        TowerNpcCard.builder()
                                .imageUrl("https://cdn/card1.png")
                                .linkType(TowerNpcCardLinkType.SHOP_ITEM)
                                .linkTargetId(15L)
                                .linkLabel("상점")
                                .startsOn(today)
                                .endsOn(today)
                                .build());
        TowerNpc shopOff =
                npcWithCard(
                        2L,
                        TowerNpcCard.builder()
                                .linkType(TowerNpcCardLinkType.SHOP_ITEM)
                                .linkTargetId(16L)
                                .build());
        TowerNpc eventGone =
                npcWithCard(
                        3L,
                        TowerNpcCard.builder()
                                .imageUrl("https://cdn/card3.png")
                                .linkType(TowerNpcCardLinkType.EVENT)
                                .linkTargetId(4L)
                                .build());
        TowerNpc ended =
                npcWithCard(
                        4L,
                        TowerNpcCard.builder()
                                .imageUrl("https://cdn/card4.png")
                                .endsOn(today.minusDays(1))
                                .build());
        TowerNpc notYet =
                npcWithCard(
                        5L,
                        TowerNpcCard.builder()
                                .linkType(TowerNpcCardLinkType.EVENT)
                                .linkTargetId(9L)
                                .startsOn(today.plusDays(1))
                                .build());
        TowerNpc path =
                npcWithCard(
                        6L,
                        TowerNpcCard.builder()
                                .linkType(TowerNpcCardLinkType.PATH)
                                .linkPath("/event")
                                .build());
        TowerNpc none = TowerTestFixtures.npc(7L, 20L, true);
        when(gamificationQueryFacade.findOnSaleShopItemIds(Set.of(15L, 16L)))
                .thenReturn(Set.of(15L));
        when(gamificationQueryFacade.findExistingEventIds(Set.of(4L))).thenReturn(Set.of());

        List<TowerNpcView> views =
                service.withCards(
                        List.of(shopOk, shopOff, eventGone, ended, notYet, path, none), today);

        assertThat(views.get(0).card().imageUrl()).isEqualTo("https://cdn/card1.png");
        assertThat(views.get(0).card().link().targetId()).isEqualTo(15L);
        assertThat(views.get(0).card().linkLabel()).isEqualTo("상점");
        assertThat(views.get(1).card()).isNull();
        assertThat(views.get(2).card()).isNull();
        assertThat(views.get(3).card()).isNull();
        assertThat(views.get(4).card()).isNull();
        assertThat(views.get(5).card().link().path()).isEqualTo("/event");
        assertThat(views.get(6).card()).isNull();
        // 기간 밖 이벤트(9)는 묻지 않는다
        verify(gamificationQueryFacade).findExistingEventIds(Set.of(4L));

        // 대상 확인이 실패하면 그 종류의 링크 창만 뺀다
        when(gamificationQueryFacade.findOnSaleShopItemIds(Set.of(15L, 16L)))
                .thenThrow(new IllegalStateException("db down"));
        List<TowerNpcView> failed = service.withCards(List.of(shopOk, shopOff, path), today);
        assertThat(failed.get(0).card()).isNull();
        assertThat(failed.get(2).card()).isNotNull();

        // 링크 대상이 없으면 조회하지 않는다
        org.mockito.Mockito.clearInvocations(gamificationQueryFacade);
        service.withCards(List.of(path, none), today);
        org.mockito.Mockito.verifyNoInteractions(gamificationQueryFacade);
    }
}
