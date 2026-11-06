package io.pinkspider.leveluptogethermvp.towerservice.application;

import io.pinkspider.global.facade.GamificationQueryFacade;
import io.pinkspider.global.facade.MissionQueryFacade;
import io.pinkspider.global.facade.UserQueryFacade;
import io.pinkspider.leveluptogethermvp.towerservice.application.chat.TowerCheerWindowStore;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerInstanceEvent;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerMissionInfo;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerSeatResult;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerFloor;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerInstance;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerMissionSeat;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerOccupant;
import io.pinkspider.leveluptogethermvp.towerservice.infrastructure.TowerFloorRepository;
import io.pinkspider.leveluptogethermvp.towerservice.infrastructure.TowerMissionSeatRepository;
import io.pinkspider.leveluptogethermvp.towerservice.infrastructure.TowerOccupantRepository;
import io.pinkspider.leveluptogethermvp.towerservice.presence.TowerPresenceService;
import io.pinkspider.leveluptogethermvp.towerservice.presence.WalkableMask;
import io.pinkspider.leveluptogethermvp.towerservice.realtime.TowerRealtimePublisher;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * LUT-630/632: 미션 시작·종료에 따른 자리. 호출은 {@code TowerMissionEventListener}(미션 수행 시작·종료 이벤트)와 주기 대조 스케줄러가
 * 한다.
 *
 * <p><b>미션 시작</b>: 이미 탑에 있으면 그 자리를 미션 중으로 바꾸고 같은 방에 상태를 브로드캐스트한다. 없으면 유저 설정 "탑 자동 착석"(기본 켬)이 켜진 로그인
 * 유저만 <b>자동 착석</b> — 입장할 수 있는 공개 층 중 <b>사람이 가장 적은 층</b>(층 전체 인원, NPC 제외, 같으면 낮은 층) → 그 층에서 <b>사람이
 * 가장 적은 방</b>. 자리는 그 층 바닥 마스크 안 랜덤({@link TowerAutoSeatPicker}, LUT-757 — 같은 방 점유자·NPC 와 거리를 두고,
 * 마스크가 없으면 입장 지점). 한 번 앉은 층은 미션이 끝날 때까지 바꾸지 않는다. 입장 레벨이 부족한 층은 비어 있어도 제외. 방문객은 대상이 아니다.
 *
 * <p><b>미션 종료</b>(완료·취소·자동 종료 전부): 완료면 완료 연출 이벤트(10초 응원 창)를 먼저 보내고, 자동 착석자와 연결이 끊긴 유저는 퇴장, 연결된 유저는
 * 휴식 중으로. 미션 단위 자리 기록({@code tower_mission_seat}: 층·자동 착석·응원 수)은 완료 응답에 실린다.
 */
@Service
@Slf4j
public class TowerSeatService {

    private final TowerFloorRepository towerFloorRepository;
    private final TowerOccupantRepository towerOccupantRepository;
    private final TowerMissionSeatRepository towerMissionSeatRepository;
    private final TowerInstanceAllocator allocator;
    private final TowerAutoSeatPicker seatPicker;
    private final TowerPopulationService towerPopulationService;
    private final TowerRealtimePublisher realtimePublisher;
    private final TowerPresenceGateway presenceGateway;
    private final TowerPresenceService presenceService;
    private final GamificationQueryFacade gamificationQueryFacade;
    private final UserQueryFacade userQueryFacade;
    private final MissionQueryFacade missionQueryFacade;
    private final TowerCheerWindowStore cheerWindowStore;

    public TowerSeatService(
            TowerFloorRepository towerFloorRepository,
            TowerOccupantRepository towerOccupantRepository,
            TowerMissionSeatRepository towerMissionSeatRepository,
            TowerInstanceAllocator allocator,
            TowerAutoSeatPicker seatPicker,
            TowerPopulationService towerPopulationService,
            TowerRealtimePublisher realtimePublisher,
            TowerPresenceGateway presenceGateway,
            TowerPresenceService presenceService,
            @Lazy GamificationQueryFacade gamificationQueryFacade,
            @Lazy UserQueryFacade userQueryFacade,
            @Lazy MissionQueryFacade missionQueryFacade,
            TowerCheerWindowStore cheerWindowStore) {
        this.cheerWindowStore = cheerWindowStore;
        this.towerFloorRepository = towerFloorRepository;
        this.towerOccupantRepository = towerOccupantRepository;
        this.towerMissionSeatRepository = towerMissionSeatRepository;
        this.allocator = allocator;
        this.seatPicker = seatPicker;
        this.towerPopulationService = towerPopulationService;
        this.realtimePublisher = realtimePublisher;
        this.presenceGateway = presenceGateway;
        this.presenceService = presenceService;
        this.gamificationQueryFacade = gamificationQueryFacade;
        this.userQueryFacade = userQueryFacade;
        this.missionQueryFacade = missionQueryFacade;
    }

    /** 미션 시작 — 이미 탑에 있으면 그 자리를 미션 중으로, 없으면(설정이 켜진 유저만) 자동 착석. 앉히지 않으면 empty */
    @Transactional(transactionManager = "towerTransactionManager")
    public Optional<TowerSeatResult> onMissionStarted(
            String userId, @Nullable TowerMissionInfo mission) {
        LocalDateTime now = LocalDateTime.now();
        Optional<TowerOccupant> existing = towerOccupantRepository.findByUserId(userId);
        if (existing.isPresent()) {
            TowerOccupant current = existing.get();
            current.startMission(now);
            openSeatRecord(current, mission, false, now);
            presenceService.updateState(
                    current,
                    TowerInstanceEvent.STATE_MISSION,
                    mission != null ? mission.toBroadcast() : null); // LUT-631·749
            if (!current.isAutoSeated()) {
                realtimePublisher.publishToInstance(
                        current.getInstanceId(),
                        TowerInstanceEvent.missionState(
                                userId, mission != null ? mission.toBroadcast() : null, now));
            }
            return Optional.of(
                    new TowerSeatResult(current.getFloorNumber(), current.getInstanceId(), false));
        }
        if (!isAutoSeatEnabled(userId)) {
            log.debug("[tower] 자동 착석 꺼짐: userId={}", userId);
            return Optional.empty();
        }

        int level = resolveLevel(userId);
        List<TowerFloor> enterable =
                towerFloorRepository.findAllByIsPublicTrueOrderByFloorNumberAsc().stream()
                        .filter(f -> f.getEntryLevel() == null || level >= f.getEntryLevel())
                        .toList();
        if (enterable.isEmpty()) {
            return Optional.empty();
        }
        Map<Integer, Long> population = towerPopulationService.byFloorFresh();
        TowerFloor chosen =
                enterable.stream()
                        .min(
                                Comparator.comparingLong(
                                                (TowerFloor f) ->
                                                        population.getOrDefault(
                                                                f.getFloorNumber(), 0L))
                                        .thenComparingInt(TowerFloor::getFloorNumber))
                        .orElseThrow();

        TowerFloor locked =
                towerFloorRepository
                        .findByFloorNumberForUpdate(chosen.getFloorNumber())
                        .orElseThrow();
        TowerInstance instance = allocator.pickForAutoSeat(locked, null, now);
        // LUT-757: 바닥 안 랜덤 자리(같은 방 점유자·NPC 와 거리 두기) — 입장 지점에 겹쳐 서지 않게
        WalkableMask.Point seat = seatPicker.pick(locked, instance.getId());
        TowerOccupant seated =
                towerOccupantRepository.save(
                        TowerOccupant.autoSeated(
                                instance.getId(),
                                locked.getFloorNumber(),
                                userId,
                                seat.x(),
                                seat.y(),
                                now));
        openSeatRecord(seated, mission, true, now);
        // LUT-631: 아바타 데이터와 함께 같은 방에 ENTER · LUT-749: 공개 미션이면 미션명도
        presenceService.register(seated, false, mission != null ? mission.toBroadcast() : null);
        log.info(
                "[tower] 자동 착석: userId={} floor={} instanceId={} seat=({},{})",
                userId,
                seated.getFloorNumber(),
                seated.getInstanceId(),
                seat.x(),
                seat.y());
        return Optional.of(
                new TowerSeatResult(seated.getFloorNumber(), seated.getInstanceId(), true));
    }

    /**
     * 미션 종료(완료·취소·자동 종료 모두). 완료면 완료 연출 이벤트(10초 응원 창)를 먼저 보낸다. 자동 착석자·연결 끊긴 유저는 퇴장, 연결된 유저는 휴식 중.
     *
     * @return 자리를 비웠는가
     */
    @Transactional(transactionManager = "towerTransactionManager")
    public boolean onMissionEnded(String userId, boolean completed) {
        LocalDateTime now = LocalDateTime.now();
        Optional<TowerOccupant> existing = towerOccupantRepository.findByUserId(userId);
        if (completed) {
            // LUT-693 실제 흔적: 완료한 자리를 남긴다(누구인지는 내리지 않는다)
            int[] spot = existing.map(this::currentSpot).orElse(null);
            completeSeatRecords(
                    userId, now, spot == null ? null : spot[0], spot == null ? null : spot[1]);
        } else {
            closeSeatRecords(userId, now);
        }
        if (existing.isEmpty()) {
            return false;
        }
        TowerOccupant current = existing.get();
        if (completed) {
            realtimePublisher.publishToInstance(
                    current.getInstanceId(), TowerInstanceEvent.missionCompleted(userId, now));
            cheerWindowStore.open(current.getInstanceId(), userId); // LUT-633: 10초 응원 창
        }
        boolean connected = !current.isAutoSeated() && presenceGateway.isConnected(userId);
        if (!connected) {
            allocator.remove(current);
            log.info(
                    "[tower] 미션 종료 퇴장: userId={} floor={} autoSeated={}",
                    userId,
                    current.getFloorNumber(),
                    current.isAutoSeated());
            return true;
        }
        current.endMission();
        presenceService.updateState(current, TowerInstanceEvent.STATE_REST, null); // LUT-631
        realtimePublisher.publishToInstance(
                current.getInstanceId(), TowerInstanceEvent.restState(userId, now));
        return false;
    }

    /** 유저가 직접 자리를 비운다(LUT-713) — 미션은 계속 진행되고 아바타만 퇴장. 자동 착석 자리도 같다 */
    @Transactional(transactionManager = "towerTransactionManager")
    public boolean vacate(String userId) {
        LocalDateTime now = LocalDateTime.now();
        closeSeatRecords(userId, now);
        Optional<TowerOccupant> existing = towerOccupantRepository.findByUserId(userId);
        if (existing.isEmpty()) {
            return false;
        }
        allocator.remove(existing.get());
        log.info("[tower] 자리 비움: userId={} floor={}", userId, existing.get().getFloorNumber());
        return true;
    }

    /** 응원 1개(LUT-633 리액션이 호출) — 열린 자리 기록이 없으면 -1 */
    @Transactional(transactionManager = "towerTransactionManager")
    public int addCheer(String targetUserId) {
        Optional<TowerMissionSeat> open =
                towerMissionSeatRepository.findFirstByUserIdAndEndedAtIsNullOrderBySeatedAtDesc(
                        targetUserId);
        if (open.isEmpty()) {
            return -1;
        }
        towerMissionSeatRepository.incrementCheer(open.get().getId());
        return open.get().getCheerCount() + 1;
    }

    /**
     * 주기 대조 — 이벤트를 놓쳐도 아바타가 영구히 남지 않도록 미션 중으로 기록된 점유자의 실제 미션 상태를 확인해 끝난 유저를 정리한다.
     *
     * @return 정리한 유저 수
     */
    @Transactional(transactionManager = "towerTransactionManager")
    public int reconcile() {
        List<TowerOccupant> active = towerOccupantRepository.findAllByMissionActiveTrue();
        if (active.isEmpty()) {
            return 0;
        }
        List<String> userIds = active.stream().map(TowerOccupant::getUserId).toList();
        Map<String, ?> inProgress;
        try {
            inProgress = missionQueryFacade.findInProgressMissions(userIds);
        } catch (Exception e) {
            log.warn("[tower] 미션 상태 대조 실패 — 이번 주기는 건너뜀: error={}", e.getMessage());
            return 0;
        }
        int cleaned = 0;
        for (TowerOccupant occupant : active) {
            if (!inProgress.containsKey(occupant.getUserId())) {
                onMissionEnded(occupant.getUserId(), false);
                cleaned++;
            }
        }
        if (cleaned > 0) {
            log.info("[tower] 미션 종료 누락 정리: {}명 (대상 {}명)", cleaned, active.size());
        }
        return cleaned;
    }

    private void openSeatRecord(
            TowerOccupant occupant,
            @Nullable TowerMissionInfo mission,
            boolean autoSeated,
            LocalDateTime now) {
        if (mission == null || mission.executionId() == null) {
            return;
        }
        boolean exists =
                towerMissionSeatRepository
                        .findFirstByUserIdAndExecutionKindAndExecutionIdOrderBySeatedAtDesc(
                                occupant.getUserId(),
                                mission.executionKind(),
                                mission.executionId())
                        .filter(TowerMissionSeat::isOpen)
                        .isPresent();
        if (exists) {
            return;
        }
        towerMissionSeatRepository.save(
                TowerMissionSeat.open(
                        occupant.getUserId(),
                        mission.missionId(),
                        mission.executionId(),
                        mission.executionKind(),
                        occupant.getFloorNumber(),
                        occupant.getInstanceId(),
                        autoSeated,
                        now));
    }

    private void closeSeatRecords(String userId, LocalDateTime now) {
        for (TowerMissionSeat seat :
                towerMissionSeatRepository.findAllByUserIdAndEndedAtIsNull(userId)) {
            seat.close(now);
        }
    }

    private void completeSeatRecords(String userId, LocalDateTime now, Integer x, Integer y) {
        for (TowerMissionSeat seat :
                towerMissionSeatRepository.findAllByUserIdAndEndedAtIsNull(userId)) {
            seat.complete(now, x, y);
        }
    }

    /** 지금 서 있는 자리 — presence(Redis) 좌표, 못 읽으면 점유자 행의 자리 */
    private int[] currentSpot(TowerOccupant occupant) {
        try {
            Optional<int[]> live =
                    presenceService
                            .find(occupant)
                            .filter(p -> p.x() != null && p.y() != null)
                            .map(p -> new int[] {p.x(), p.y()});
            if (live.isPresent()) {
                return live.get();
            }
        } catch (RuntimeException e) {
            log.warn("탑 완료 자리 조회 실패 — 점유자 자리 사용: userId={}", occupant.getUserId());
        }
        if (occupant.getSeatX() == null || occupant.getSeatY() == null) {
            return null;
        }
        return new int[] {occupant.getSeatX(), occupant.getSeatY()};
    }

    private boolean isAutoSeatEnabled(String userId) {
        try {
            return userQueryFacade.isTowerAutoSeatEnabled(userId);
        } catch (Exception e) {
            log.warn("탑 자동 착석 설정 조회 실패 — 켬으로 판정: userId={}, error={}", userId, e.getMessage());
            return true;
        }
    }

    private int resolveLevel(String userId) {
        try {
            Integer level = gamificationQueryFacade.getUserLevel(userId);
            return level == null ? 1 : level;
        } catch (Exception e) {
            log.warn("탑 자동 착석 레벨 조회 실패 — 레벨 1 로 판정: userId={}, error={}", userId, e.getMessage());
            return 1;
        }
    }
}
