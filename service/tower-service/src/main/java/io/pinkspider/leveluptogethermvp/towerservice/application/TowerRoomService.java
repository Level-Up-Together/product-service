package io.pinkspider.leveluptogethermvp.towerservice.application;

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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * LUT-738: 같은 층 안의 방 목록·방 지정 이동. 처음 입장은 지금처럼 자동 배정(TowerEntryService)이고, 들어온 뒤에만(직접 입장·방문객 점유자) 옮길
 * 수 있다. 이동은 10초에 한 번, 미션 중 상태·자리(좌표)는 그대로, 이전 방에 LEAVE·새 방에 ENTER. 가득 찬 방은 다른 방으로 대신 보내지 않고 거절한다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(transactionManager = "towerTransactionManager", readOnly = true)
public class TowerRoomService {

    private final TowerFloorRepository floorRepository;
    private final TowerInstanceRepository instanceRepository;
    private final TowerOccupantRepository occupantRepository;
    private final TowerInstanceAllocator allocator;
    private final TowerPresenceService presenceService;
    private final TowerPopulationService populationService;
    private final TowerRoomMoveLimiter moveLimiter;

    /** 방 번호 순. 그 층에 들어와 있는 사람(로그인 유저 또는 방문객 키)만 볼 수 있다 — 아니면 403 150205 */
    public TowerRoomListResponse list(
            int floorNumber, @Nullable String userId, @Nullable String visitorKey) {
        TowerOccupant me = requireOccupant(userId, visitorKey);
        if (me.getFloorNumber() != floorNumber) {
            throw TowerEntryException.notInInstance();
        }
        TowerFloor floor =
                floorRepository
                        .findByFloorNumber(floorNumber)
                        .orElseThrow(TowerEntryException::floorNotAvailable);
        int capacity = floor.getInstanceCapacity();
        List<TowerInstance> instances =
                instanceRepository.findAllByFloorNumberOrderByIdAsc(floorNumber);
        Map<Long, Integer> numbers = TowerInstance.displayNumbers(instances);
        Map<Long, InstanceLoad> loads = new HashMap<>();
        for (InstanceLoad load :
                occupantRepository.loadsByFloor(floorNumber, TowerOccupantKind.AUTO_SEATED)) {
            loads.put(load.instanceId(), load);
        }
        List<TowerRoomView> rooms = new ArrayList<>();
        long floorTotal = 0;
        for (TowerInstance instance : instances) {
            InstanceLoad load =
                    loads.getOrDefault(
                            instance.getId(), new InstanceLoad(instance.getId(), 0L, 0L));
            floorTotal += load.total();
            rooms.add(
                    new TowerRoomView(
                            instance.getId(),
                            numbers.get(instance.getId()),
                            load.counted(),
                            load.total(),
                            capacity,
                            load.counted() >= capacity,
                            instance.getId().equals(me.getInstanceId())));
        }
        rooms.sort(Comparator.comparing(TowerRoomView::instanceNo));
        return new TowerRoomListResponse(floorNumber, floorTotal, capacity, rooms);
    }

    /**
     * 같은 층의 다른 방으로. 거절: 없는 방 404 150210 · 다른 층의 방 400 150209 · 10초 안 재이동 429 150208(남은 초) · 가득 참
     * 409 150207. 같은 방이면 아무것도 하지 않고 지금 자리를 돌려준다. 판정은 층 행 잠금 안에서 해 입장·자동 착석과 겹치지 않는다.
     */
    @Transactional(transactionManager = "towerTransactionManager")
    public TowerEnterResponse moveIn(
            Long instanceId, @Nullable String userId, @Nullable String visitorKey) {
        TowerOccupant me = requireOccupant(userId, visitorKey);
        TowerInstance target =
                instanceRepository
                        .findById(instanceId)
                        .orElseThrow(TowerEntryException::roomNotFound);
        if (!target.getFloorNumber().equals(me.getFloorNumber())) {
            throw TowerEntryException.roomOtherFloor();
        }
        TowerFloor floor =
                floorRepository
                        .findByFloorNumberForUpdate(me.getFloorNumber())
                        .orElseThrow(TowerEntryException::floorNotAvailable);
        if (me.getInstanceId().equals(instanceId)) {
            return response(me, floor);
        }
        String subject = userId != null ? "u:" + userId : "v:" + visitorKey;
        long wait = moveLimiter.remainingSeconds(subject);
        if (wait > 0) {
            throw TowerEntryException.roomMoveTooSoon(wait);
        }
        if (allocator.countedOccupants(instanceId) >= floor.getInstanceCapacity()) {
            throw TowerEntryException.roomFull();
        }
        LocalDateTime now = LocalDateTime.now();
        Long from = me.getInstanceId();
        me.moveTo(instanceId);
        me.touch(now);
        presenceService.moveInstance(me, from, instanceId); // LEAVE(이전 방)·ENTER(새 방), 상태·좌표 그대로
        allocator.closeIfEmpty(from);
        allocator.relocateAutoSeatedOverflow(floor, instanceId, now);
        moveLimiter.markMoved(subject);
        log.info(
                "[tower] 방 이동: floor={} {} {} -> {} mission={}",
                floor.getFloorNumber(),
                userId != null ? "userId=" + userId : "visitor",
                from,
                instanceId,
                me.isMissionActive());
        return response(me, floor);
    }

    /** 탑 화면에 들어와 있는 점유자(직접 입장·방문객) — 자동 착석만 한 유저·없는 사람은 403 150205 */
    private TowerOccupant requireOccupant(@Nullable String userId, @Nullable String visitorKey) {
        Optional<TowerOccupant> occupant =
                userId != null
                        ? occupantRepository.findByUserId(userId)
                        : (visitorKey == null || visitorKey.isBlank()
                                ? Optional.empty()
                                : occupantRepository.findByVisitorKey(visitorKey));
        return occupant.filter(o -> !o.isAutoSeated())
                .orElseThrow(TowerEntryException::notInInstance);
    }

    private TowerEnterResponse response(TowerOccupant me, TowerFloor floor) {
        return new TowerEnterResponse(
                floor.getFloorNumber(),
                me.getInstanceId(),
                me.getId(),
                true,
                me.isMissionActive(),
                me.getVisitorKey(),
                floor.getEntryPointX(),
                floor.getEntryPointY(),
                me.getSeatX(),
                me.getSeatY(),
                populationService.of(floor.getFloorNumber()),
                me.getVisitorNo(),
                false,
                false,
                null,
                null);
    }
}
