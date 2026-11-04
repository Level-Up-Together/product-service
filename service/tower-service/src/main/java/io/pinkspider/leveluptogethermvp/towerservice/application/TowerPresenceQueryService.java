package io.pinkspider.leveluptogethermvp.towerservice.application;

import io.pinkspider.leveluptogethermvp.towerservice.application.exception.TowerEntryException;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerNpcView;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerPresenceSnapshotResponse;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerOccupant;
import io.pinkspider.leveluptogethermvp.towerservice.infrastructure.TowerFloorRepository;
import io.pinkspider.leveluptogethermvp.towerservice.infrastructure.TowerNpcRepository;
import io.pinkspider.leveluptogethermvp.towerservice.infrastructure.TowerOccupantRepository;
import io.pinkspider.leveluptogethermvp.towerservice.presence.TowerPresenceService;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** LUT-631: 입장 스냅샷 — 그 방의 점유자(로그인 유저 또는 방문객 키)만 받을 수 있다 */
@Service
@RequiredArgsConstructor
@Transactional(transactionManager = "towerTransactionManager", readOnly = true)
public class TowerPresenceQueryService {

    private final TowerOccupantRepository occupantRepository;
    private final TowerPresenceService presenceService;
    private final TowerFloorRepository towerFloorRepository;
    private final TowerNpcRepository towerNpcRepository;
    private final TowerSettingService settingService;
    private final TowerTraceService traceService;

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
                                towerNpcRepository
                                        .findAllByFloorIdAndIsPublicTrueOrderBySortOrderAscIdAsc(
                                                floor.getId())
                                        .stream()
                                        .map(TowerNpcView::from)
                                        .toList())
                .orElse(List.of());
    }
}
