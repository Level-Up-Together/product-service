package io.pinkspider.leveluptogethermvp.towerservice.application;

import io.pinkspider.global.event.TowerFloorFirstEnteredEvent;
import io.pinkspider.global.facade.GamificationQueryFacade;
import io.pinkspider.global.facade.MissionQueryFacade;
import io.pinkspider.leveluptogethermvp.towerservice.application.exception.TowerEntryException;
import io.pinkspider.leveluptogethermvp.towerservice.application.queue.TowerQueueService;
import io.pinkspider.leveluptogethermvp.towerservice.application.visitor.TowerVisitorContext;
import io.pinkspider.leveluptogethermvp.towerservice.application.visitor.TowerVisitorGate;
import io.pinkspider.leveluptogethermvp.towerservice.application.visitor.TowerVisitorSession;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerEnterResponse;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerLeaveResponse;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerMySeatResponse;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerFloor;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerInstance;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerOccupant;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerUserState;
import io.pinkspider.leveluptogethermvp.towerservice.infrastructure.TowerFloorRepository;
import io.pinkspider.leveluptogethermvp.towerservice.infrastructure.TowerFloorVisitRepository;
import io.pinkspider.leveluptogethermvp.towerservice.infrastructure.TowerOccupantRepository;
import io.pinkspider.leveluptogethermvp.towerservice.infrastructure.TowerUserStateRepository;
import io.pinkspider.leveluptogethermvp.towerservice.presence.TowerPresenceService;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * LUT-630: 탑 입장·퇴장. 입장 가능 여부는 서버가 최종 판정한다 — 유저 레벨 ≥ 층 입장 레벨(제한 없음이면 레벨을 보지 않는다), 비로그인은 어드민이 허용한
 * 층만(제한 없는 층도 허용이 없으면 로그인 필요). 처음 진입은 무조건 자동 배정이라 인스턴스를 지정하는 파라미터가 없다.
 *
 * <p>복귀: 자리 유지 중(미션 중 DIRECT)이던 유저가 같은 층에 다시 들어오면 배정·대기열 없이 원래 방으로. 자동 착석 자리가 있는 유저는 그 자리에서 직접 입장으로
 * 이어진다(그 방이 직접 입장자로 차 있으면 일반 배정). 다른 층으로 들어오면 자리를 옮긴다(유저 의사). 4 locale 섞어 배정.
 *
 * <p>LUT-701(탑 전체 동시 접속 상한·대기열)은 복귀 판정 뒤·새 배정 앞에 끼운다 — 복귀는 대기열을 거치지 않는다.
 */
@Service
@Slf4j
public class TowerEntryService {

    private final TowerFloorRepository towerFloorRepository;
    private final TowerOccupantRepository towerOccupantRepository;
    private final TowerUserStateRepository towerUserStateRepository;
    private final TowerInstanceAllocator allocator;
    private final TowerPopulationService towerPopulationService;
    private final GamificationQueryFacade gamificationQueryFacade;
    private final MissionQueryFacade missionQueryFacade;
    private final TowerPresenceService presenceService;
    private final TowerFloorVisitRepository towerFloorVisitRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final TowerQueueService queueService;
    private final TowerVisitorGate visitorGate;

    public TowerEntryService(
            TowerFloorRepository towerFloorRepository,
            TowerOccupantRepository towerOccupantRepository,
            TowerUserStateRepository towerUserStateRepository,
            TowerInstanceAllocator allocator,
            TowerPopulationService towerPopulationService,
            @Lazy GamificationQueryFacade gamificationQueryFacade,
            @Lazy MissionQueryFacade missionQueryFacade,
            TowerPresenceService presenceService,
            TowerFloorVisitRepository towerFloorVisitRepository,
            ApplicationEventPublisher eventPublisher,
            TowerQueueService queueService,
            TowerVisitorGate visitorGate) {
        this.towerFloorRepository = towerFloorRepository;
        this.towerOccupantRepository = towerOccupantRepository;
        this.towerUserStateRepository = towerUserStateRepository;
        this.allocator = allocator;
        this.towerPopulationService = towerPopulationService;
        this.gamificationQueryFacade = gamificationQueryFacade;
        this.missionQueryFacade = missionQueryFacade;
        this.presenceService = presenceService;
        this.towerFloorVisitRepository = towerFloorVisitRepository;
        this.eventPublisher = eventPublisher;
        this.queueService = queueService;
        this.visitorGate = visitorGate;
    }

    @Transactional(transactionManager = "towerTransactionManager")
    public TowerEnterResponse enter(@Nullable String userId, int floorNumber) {
        return enter(userId, floorNumber, null);
    }

    /**
     * LUT-701: {@code queueTicket} = 대기열에서 받은 표(입장 가능 신호 뒤 다시 요청할 때). 상한에 닿았으면 배정 대신 순번을 돌려준다 — 층
     * 이동·자리 유지 중인 유저의 복귀(점유자가 직접 입장·방문객)는 대기열을 거치지 않는다.
     */
    @Transactional(transactionManager = "towerTransactionManager")
    public TowerEnterResponse enter(
            @Nullable String userId, int floorNumber, @Nullable String queueTicket) {
        return enter(userId, floorNumber, queueTicket, TowerVisitorContext.none());
    }

    /**
     * LUT-705: {@code visitor} = 비로그인 요청의 신원 단서(이전 방문객 키·기기 ID·IP). 방문객은 {@link TowerVisitorGate} 를
     * 지나며(제재·IP 빈도) 같은 세션이면 같은 키·이름 번호를 쓰고, 이미 안에 있으면(층 이동·재입장) 대기열을 거치지 않는다. 로그인 유저 요청에서는 무시한다.
     */
    @Transactional(transactionManager = "towerTransactionManager")
    public TowerEnterResponse enter(
            @Nullable String userId,
            int floorNumber,
            @Nullable String queueTicket,
            TowerVisitorContext visitor) {
        TowerFloor floor =
                towerFloorRepository
                        .findByFloorNumber(floorNumber)
                        .filter(TowerFloor::isPublicFloor)
                        .orElseThrow(TowerEntryException::floorNotAvailable);
        checkEntry(floor, userId);
        // LUT-632: 탑 밖에서 시작한 미션도 입장하면 미션 중으로 — 잠그기 전에 미션 DB 를 읽는다
        boolean inMission = userId != null && hasInProgressMission(userId);

        // LUT-705: 방문객은 세션·제재·IP 빈도를 먼저 본다(새 세션이면 키·이름 번호를 여기서 받는다)
        TowerVisitorSession visitorSession = userId == null ? visitorGate.admit(visitor) : null;
        // LUT-701: 이미 탑 안(직접 입장·자리 유지·같은 세션의 방문객)이면 층 이동·복귀라 대기열을 거치지 않는다. 자동 착석만 한 유저는 새 입장으로 본다
        boolean alreadyInside =
                userId != null
                        ? towerOccupantRepository
                                .findByUserId(userId)
                                .filter(o -> !o.isAutoSeated())
                                .isPresent()
                        : towerOccupantRepository
                                .findByVisitorKey(visitorSession.visitorKey())
                                .isPresent();
        if (!alreadyInside) {
            TowerQueueService.Gate gate = queueService.check(userId, floorNumber, queueTicket);
            if (!gate.pass()) {
                return TowerEnterResponse.queued(floorNumber, gate.waiting());
            }
        }

        LocalDateTime now = LocalDateTime.now();
        // 층 행 잠금 — 같은 층의 배정을 직렬화한다 (레벨 조회는 위에서 끝났다)
        TowerFloor locked =
                towerFloorRepository
                        .findByFloorNumberForUpdate(floorNumber)
                        .orElseThrow(TowerEntryException::floorNotAvailable);

        TowerOccupant occupant = null;
        boolean resumed = false;
        boolean autoSeatContinued = false;
        boolean missionActive = inMission;
        if (userId != null) {
            Optional<TowerOccupant> existing = towerOccupantRepository.findByUserId(userId);
            if (existing.isPresent()) {
                TowerOccupant current = existing.get();
                missionActive = current.isMissionActive() || inMission;
                if (current.getFloorNumber() == floorNumber) {
                    if (!current.isAutoSeated()) {
                        current.touch(now);
                        occupant = current;
                        resumed = true;
                    } else if (allocator.countedOccupants(current.getInstanceId())
                            < locked.getInstanceCapacity()) {
                        current.convertToDirect(now);
                        occupant = current;
                        resumed = true;
                        autoSeatContinued = true;
                    } else {
                        allocator.remove(current); // 직접 입장자로 찬 방 — 일반 배정
                    }
                } else {
                    allocator.remove(current); // 층 이동(유저 의사) — 미션 중 상태는 가져간다
                }
            }
        } else {
            // LUT-705: 같은 세션의 방문객 — 같은 층이면 그 자리로, 다른 층이면 옮긴다(키·이름 번호는 그대로)
            Optional<TowerOccupant> existing =
                    towerOccupantRepository.findByVisitorKey(visitorSession.visitorKey());
            if (existing.isPresent()) {
                TowerOccupant current = existing.get();
                if (current.getFloorNumber() == floorNumber) {
                    current.touch(now);
                    occupant = current;
                    resumed = true;
                } else {
                    allocator.remove(current);
                }
            }
        }

        String visitorKey = visitorSession != null ? visitorSession.visitorKey() : null;
        if (occupant == null) {
            // LUT-701: 탑 전체 동시 접속 상한 판정 지점 (복귀는 위에서 끝나 대기열을 거치지 않는다)
            TowerInstance instance = allocator.pickForDirectEntry(locked, now);
            if (userId != null) {
                occupant =
                        TowerOccupant.direct(
                                instance.getId(),
                                floorNumber,
                                userId,
                                missionActive,
                                locked.getEntryPointX(),
                                locked.getEntryPointY(),
                                now);
            } else {
                occupant =
                        TowerOccupant.visitor(
                                instance.getId(),
                                floorNumber,
                                visitorKey,
                                visitorSession.visitorNo(),
                                locked.getEntryPointX(),
                                locked.getEntryPointY(),
                                now);
            }
            occupant = towerOccupantRepository.save(occupant);
            presenceService.register(occupant, false); // LUT-631: 같은 방에 ENTER, 연결은 웹소켓 join 때
            allocator.relocateAutoSeatedOverflow(locked, instance.getId(), now);
        }

        if (userId != null) {
            recordVisit(userId, floorNumber, now);
        }
        log.info(
                "[tower] 입장: floor={} instanceId={} {} resumed={}",
                floorNumber,
                occupant.getInstanceId(),
                userId != null ? "userId=" + userId : "visitor",
                resumed);
        return new TowerEnterResponse(
                floorNumber,
                occupant.getInstanceId(),
                occupant.getId(),
                resumed,
                occupant.isMissionActive(),
                visitorKey,
                locked.getEntryPointX(),
                locked.getEntryPointY(),
                occupant.getSeatX(),
                occupant.getSeatY(),
                towerPopulationService.of(floorNumber),
                occupant.getVisitorNo(),
                autoSeatContinued,
                false,
                null);
    }

    /** LUT-713: 내 자리 — 로그인 유저의 점유자(직접 입장·자동 착석·자리 유지)가 있으면 그 층·종류, 없으면 자리 없음 */
    @Transactional(transactionManager = "towerTransactionManager", readOnly = true)
    public TowerMySeatResponse mySeat(String userId) {
        return towerOccupantRepository
                .findByUserId(userId)
                .map(TowerMySeatResponse::of)
                .orElseGet(TowerMySeatResponse::none);
    }

    /**
     * 퇴장(멱등). 미션 중인 로그인 유저는 자리를 남긴다(연결이 끊겨도 유지, LUT-631 — 미션 종료 때 정리는 LUT-632). 자동 착석 자리도 남긴다. 방문객은
     * {@code visitorKey} 로 본인을 댄다.
     */
    @Transactional(transactionManager = "towerTransactionManager")
    public TowerLeaveResponse leave(@Nullable String userId, @Nullable String visitorKey) {
        Optional<TowerOccupant> found;
        if (userId != null) {
            found = towerOccupantRepository.findByUserId(userId);
        } else {
            if (visitorKey == null || visitorKey.isBlank()) {
                throw TowerEntryException.visitorKeyRequired();
            }
            found = towerOccupantRepository.findByVisitorKey(visitorKey);
        }
        if (found.isEmpty()) {
            return new TowerLeaveResponse(false, false);
        }
        TowerOccupant occupant = found.get();
        if (occupant.isMissionActive()) {
            log.info("[tower] 퇴장 — 미션 중이라 자리 유지: userId={}", userId);
            return new TowerLeaveResponse(false, true);
        }
        allocator.remove(occupant);
        log.info(
                "[tower] 퇴장: floor={} instanceId={} {}",
                occupant.getFloorNumber(),
                occupant.getInstanceId(),
                userId != null ? "userId=" + userId : "visitor");
        return new TowerLeaveResponse(true, false);
    }

    private void checkEntry(TowerFloor floor, @Nullable String userId) {
        if (userId == null) {
            if (!floor.isGuestAllowed()) {
                throw TowerEntryException.loginRequired();
            }
            return;
        }
        if (floor.getEntryLevel() == null) {
            return;
        }
        if (resolveLevel(userId) < floor.getEntryLevel()) {
            throw TowerEntryException.levelInsufficient();
        }
    }

    private boolean hasInProgressMission(String userId) {
        try {
            return missionQueryFacade.findInProgressMission(userId).isPresent();
        } catch (Exception e) {
            log.warn("탑 입장 미션 상태 조회 실패 — 휴식 중으로 판정: userId={}, error={}", userId, e.getMessage());
            return false;
        }
    }

    private int resolveLevel(String userId) {
        try {
            Integer level = gamificationQueryFacade.getUserLevel(userId);
            return level == null ? 1 : level;
        } catch (Exception e) {
            log.warn("탑 입장 레벨 조회 실패 — 레벨 1 로 판정: userId={}, error={}", userId, e.getMessage());
            return 1;
        }
    }

    private void recordVisit(String userId, int floorNumber, LocalDateTime now) {
        towerUserStateRepository
                .findById(userId)
                .ifPresentOrElse(
                        state -> state.visit(floorNumber, now),
                        () ->
                                towerUserStateRepository.save(
                                        TowerUserState.builder()
                                                .userId(userId)
                                                .lastFloorNumber(floorNumber)
                                                .lastEnteredAt(now)
                                                .build()));
        // LUT-634: 층 첫 입장 — 유저×층 유니크라 두 번째부터는 0행. 커밋 뒤 gamification 이 TOWER_SERVICE 업적을 재판정한다
        if (towerFloorVisitRepository.insertIfAbsent(userId, floorNumber, now) > 0) {
            int visited = (int) towerFloorVisitRepository.countByUserId(userId);
            eventPublisher.publishEvent(
                    new TowerFloorFirstEnteredEvent(userId, floorNumber, visited));
            log.info(
                    "[tower] 층 첫 입장: userId={} floor={} visitedFloors={}",
                    userId,
                    floorNumber,
                    visited);
        }
    }
}
