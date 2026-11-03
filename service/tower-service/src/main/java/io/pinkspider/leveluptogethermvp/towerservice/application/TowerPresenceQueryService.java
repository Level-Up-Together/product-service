package io.pinkspider.leveluptogethermvp.towerservice.application;

import io.pinkspider.leveluptogethermvp.towerservice.application.exception.TowerEntryException;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerPresenceSnapshotResponse;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerOccupant;
import io.pinkspider.leveluptogethermvp.towerservice.infrastructure.TowerOccupantRepository;
import io.pinkspider.leveluptogethermvp.towerservice.presence.TowerPresenceService;
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
        return new TowerPresenceSnapshotResponse(
                instanceId,
                me.get().getFloorNumber(),
                me.get().getId(),
                presenceService.snapshot(instanceId, userId));
    }
}
