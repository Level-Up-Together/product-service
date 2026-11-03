package io.pinkspider.leveluptogethermvp.towerservice.application;

import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerFloor;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerInstance;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerOccupant;
import io.pinkspider.leveluptogethermvp.towerservice.domain.enums.TowerOccupantKind;
import io.pinkspider.leveluptogethermvp.towerservice.infrastructure.InstanceLoad;
import io.pinkspider.leveluptogethermvp.towerservice.infrastructure.TowerInstanceRepository;
import io.pinkspider.leveluptogethermvp.towerservice.infrastructure.TowerOccupantRepository;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

/**
 * LUT-630: 인스턴스(방) 고르기·열기·닫기. 호출자는 층 행을 잠근 트랜잭션({@code towerTransactionManager}) 안에서 부른다.
 *
 * <ul>
 *   <li>직접 입장·방문객: 정원이 남은 방 중 <b>사람이 가장 많은 곳</b>(같으면 먼저 연 방). 전부 차면 새 방. 균등 분산 금지.
 *   <li>자동 착석: 전체 인원이 상한 미만인 방 중 <b>사람이 가장 적은 곳</b>. 자동 착석만 있는 방도 열린 방이다.
 *   <li>서버는 유저 의사 없이 방을 합치거나 유저를 옮기지 않는다. 유일한 예외는 직접 입장자가 들어와 방 전체 인원이 상한을 넘을 때 자동 착석자를 같은 층의 다른
 *       방(없으면 새 방)으로 보내는 자리 양보다.
 *   <li>아무도 없는 방(자리 유지 중인 유저도 없음)은 바로 닫는다(행 삭제).
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TowerInstanceAllocator {

    private final TowerInstanceRepository towerInstanceRepository;
    private final TowerOccupantRepository towerOccupantRepository;

    public TowerInstance pickForDirectEntry(TowerFloor floor, LocalDateTime now) {
        int capacity = floor.getInstanceCapacity();
        Optional<TowerInstance> best =
                loads(floor).entrySet().stream()
                        .filter(e -> e.getValue().counted() < capacity)
                        .max(
                                Comparator.comparingLong(
                                                (Map.Entry<TowerInstance, InstanceLoad> e) ->
                                                        e.getValue().counted())
                                        .thenComparing(
                                                e -> e.getKey().getId(), Comparator.reverseOrder()))
                        .map(Map.Entry::getKey);
        return best.orElseGet(() -> open(floor, now));
    }

    public TowerInstance pickForAutoSeat(
            TowerFloor floor, @Nullable Long excludeInstanceId, LocalDateTime now) {
        int capacity = floor.getInstanceCapacity();
        Optional<TowerInstance> best =
                loads(floor).entrySet().stream()
                        .filter(e -> !e.getKey().getId().equals(excludeInstanceId))
                        .filter(e -> e.getValue().total() < capacity)
                        .min(
                                Comparator.comparingLong(
                                                (Map.Entry<TowerInstance, InstanceLoad> e) ->
                                                        e.getValue().total())
                                        .thenComparing(e -> e.getKey().getId()))
                        .map(Map.Entry::getKey);
        return best.orElseGet(() -> open(floor, now));
    }

    /** 정원에 세는 인원(직접 입장·방문객, 자리 유지 포함 — 자동 착석 제외) */
    public long countedOccupants(Long instanceId) {
        return towerOccupantRepository.countByInstanceIdAndKindIn(
                instanceId, EnumSet.of(TowerOccupantKind.DIRECT, TowerOccupantKind.VISITOR));
    }

    /** 점유자를 지우고, 방이 비면 닫는다 */
    public void remove(TowerOccupant occupant) {
        Long instanceId = occupant.getInstanceId();
        towerOccupantRepository.delete(occupant);
        closeIfEmpty(instanceId);
    }

    public void closeIfEmpty(Long instanceId) {
        if (towerOccupantRepository.countByInstanceId(instanceId) == 0) {
            towerInstanceRepository.deleteById(instanceId);
            log.info("[tower] 빈 인스턴스 닫음: instanceId={}", instanceId);
        }
    }

    /**
     * 직접 입장자가 들어온 방의 전체 인원(자동 착석 포함)이 상한을 넘으면 자동 착석자를 다른 방으로 보낸다 — 직접 입장자가 자동 착석자 때문에 새 방으로 밀리지 않게.
     * 최근에 앉은 자동 착석자부터.
     */
    public void relocateAutoSeatedOverflow(TowerFloor floor, Long instanceId, LocalDateTime now) {
        int capacity = floor.getInstanceCapacity();
        while (towerOccupantRepository.countByInstanceId(instanceId) > capacity) {
            List<TowerOccupant> autoSeated =
                    towerOccupantRepository.findAllByInstanceIdAndKindOrderByEnteredAtDesc(
                            instanceId, TowerOccupantKind.AUTO_SEATED);
            if (autoSeated.isEmpty()) {
                return;
            }
            TowerOccupant mover = autoSeated.get(0);
            TowerInstance target = pickForAutoSeat(floor, instanceId, now);
            mover.moveTo(target.getId());
            log.info(
                    "[tower] 자동 착석자 자리 양보: userId={} {} -> {}",
                    mover.getUserId(),
                    instanceId,
                    target.getId());
        }
    }

    private TowerInstance open(TowerFloor floor, LocalDateTime now) {
        TowerInstance created =
                towerInstanceRepository.save(TowerInstance.open(floor.getFloorNumber(), now));
        log.info("[tower] 새 인스턴스: floor={} instanceId={}", floor.getFloorNumber(), created.getId());
        return created;
    }

    private Map<TowerInstance, InstanceLoad> loads(TowerFloor floor) {
        Map<Long, InstanceLoad> byId = new HashMap<>();
        for (InstanceLoad load :
                towerOccupantRepository.loadsByFloor(
                        floor.getFloorNumber(), TowerOccupantKind.AUTO_SEATED)) {
            byId.put(load.instanceId(), load);
        }
        Map<TowerInstance, InstanceLoad> result = new HashMap<>();
        for (TowerInstance instance :
                towerInstanceRepository.findAllByFloorNumberOrderByIdAsc(floor.getFloorNumber())) {
            result.put(
                    instance,
                    byId.getOrDefault(
                            instance.getId(), new InstanceLoad(instance.getId(), 0L, 0L)));
        }
        return result;
    }
}
