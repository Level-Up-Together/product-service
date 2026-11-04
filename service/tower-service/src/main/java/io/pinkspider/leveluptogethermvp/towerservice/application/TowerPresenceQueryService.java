package io.pinkspider.leveluptogethermvp.towerservice.application;

import io.pinkspider.global.facade.GamificationQueryFacade;
import io.pinkspider.leveluptogethermvp.towerservice.application.exception.TowerEntryException;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerNpcCardDto;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerNpcView;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerPresenceSnapshotResponse;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerNpc;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerNpcCard;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerOccupant;
import io.pinkspider.leveluptogethermvp.towerservice.domain.enums.TowerNpcCardLinkType;
import io.pinkspider.leveluptogethermvp.towerservice.infrastructure.TowerFloorRepository;
import io.pinkspider.leveluptogethermvp.towerservice.infrastructure.TowerNpcRepository;
import io.pinkspider.leveluptogethermvp.towerservice.infrastructure.TowerOccupantRepository;
import io.pinkspider.leveluptogethermvp.towerservice.presence.TowerPresenceService;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** LUT-631: 입장 스냅샷 — 그 방의 점유자(로그인 유저 또는 방문객 키)만 받을 수 있다 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(transactionManager = "towerTransactionManager", readOnly = true)
public class TowerPresenceQueryService {

    private final TowerOccupantRepository occupantRepository;
    private final TowerPresenceService presenceService;
    private final TowerFloorRepository towerFloorRepository;
    private final TowerNpcRepository towerNpcRepository;
    private final TowerSettingService settingService;
    private final TowerTraceService traceService;
    private final GamificationQueryFacade gamificationQueryFacade;

    public TowerPresenceSnapshotResponse snapshot(
            Long instanceId, @Nullable String userId, @Nullable String visitorKey) {
        Optional<TowerOccupant> me =
                userId != null
                        ? occupantRepository.findByUserId(userId)
                        : (visitorKey == null || visitorKey.isBlank()
                                ? Optional.empty()
                                : occupantRepository.findByVisitorKey(visitorKey));
        if (me.isEmpty() || !me.get().getInstanceId().equals(instanceId)) {
            throw TowerEntryException.notInInstance();
        }
        int floorNumber = me.get().getFloorNumber();
        boolean npcShown = isNpcShown(userId != null ? userId : visitorKey);
        return new TowerPresenceSnapshotResponse(
                instanceId,
                floorNumber,
                me.get().getId(),
                presenceService.snapshot(instanceId, userId),
                npcShown ? npcs(floorNumber) : List.of(),
                npcShown,
                traceService.today(floorNumber));
    }

    /**
     * LUT-693 A/B: 유저(방문객은 방문객 키) 단위로 고정된 0~99 버킷이 노출 비율보다 작으면 NPC 를 보여 준다. 같은 유저는 다시 들어와도 같은 그룹.
     */
    boolean isNpcShown(@Nullable String subject) {
        int percent = settingService.getNpcExposurePercent();
        if (percent >= 100) {
            return true;
        }
        if (percent <= 0 || subject == null) {
            return false;
        }
        return Math.floorMod(subject.hashCode(), 100) < percent;
    }

    /** 이 층의 공개 NPC — 인원과 관계없이 전원, 모든 방에 같은 자리(수정은 다음 스냅샷부터) */
    private List<TowerNpcView> npcs(int floorNumber) {
        return towerFloorRepository
                .findByFloorNumber(floorNumber)
                .map(
                        floor ->
                                withCards(
                                        towerNpcRepository
                                                .findAllByFloorIdAndIsPublicTrueOrderBySortOrderAscIdAsc(
                                                        floor.getId()),
                                        LocalDate.now(TowerTraceService.KST)))
                .orElse(List.of());
    }

    /**
     * LUT-733: 창 이미지·링크는 오늘(KST) 노출 기간 안이고 링크 대상(판매 중 아이템·있는 이벤트)이 있을 때만 내린다 — 아니면 창 묶음 전체를 뺀다(이미지는
     * 그 대상을 알리는 그림이라). 대상 확인은 층의 NPC 를 모아 종류별 한 번씩, 실패하면 그 종류의 링크 창을 뺀다.
     */
    List<TowerNpcView> withCards(List<TowerNpc> npcs, LocalDate today) {
        List<TowerNpcCard> active =
                npcs.stream()
                        .map(TowerNpc::getCard)
                        .filter(card -> card != null && card.isActiveOn(today))
                        .toList();
        Set<Long> onSaleItems =
                lookup(
                        active,
                        TowerNpcCardLinkType.SHOP_ITEM,
                        gamificationQueryFacade::findOnSaleShopItemIds);
        Set<Long> events =
                lookup(
                        active,
                        TowerNpcCardLinkType.EVENT,
                        gamificationQueryFacade::findExistingEventIds);
        return npcs.stream()
                .map(
                        npc ->
                                TowerNpcView.from(
                                        npc,
                                        visibleCard(npc.getCard(), today, onSaleItems, events)))
                .toList();
    }

    private static TowerNpcCardDto visibleCard(
            @Nullable TowerNpcCard card, LocalDate today, Set<Long> onSaleItems, Set<Long> events) {
        if (card == null || !card.isActiveOn(today)) {
            return null;
        }
        if (card.getLinkType() == TowerNpcCardLinkType.SHOP_ITEM
                && !onSaleItems.contains(card.getLinkTargetId())) {
            return null;
        }
        if (card.getLinkType() == TowerNpcCardLinkType.EVENT
                && !events.contains(card.getLinkTargetId())) {
            return null;
        }
        return TowerNpcCardDto.from(card);
    }

    private static Set<Long> lookup(
            List<TowerNpcCard> cards,
            TowerNpcCardLinkType type,
            Function<Collection<Long>, Set<Long>> finder) {
        Set<Long> ids =
                cards.stream()
                        .filter(
                                card ->
                                        card.getLinkType() == type
                                                && card.getLinkTargetId() != null)
                        .map(TowerNpcCard::getLinkTargetId)
                        .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Set.of();
        }
        try {
            return finder.apply(ids);
        } catch (RuntimeException e) {
            log.warn(
                    "탑 NPC 창 링크 대상 확인 실패 — 그 링크 창은 뺀다: type={}, ids={}, error={}",
                    type,
                    ids,
                    e.getMessage());
            return Set.of();
        }
    }
}
