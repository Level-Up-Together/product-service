package io.pinkspider.leveluptogethermvp.towerservice.application;

import io.pinkspider.global.facade.GamificationQueryFacade;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerSeatResult;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerFloor;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerInstance;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerOccupant;
import io.pinkspider.leveluptogethermvp.towerservice.infrastructure.TowerFloorRepository;
import io.pinkspider.leveluptogethermvp.towerservice.infrastructure.TowerOccupantRepository;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * LUT-630: 미션 시작·종료에 따른 자리. 호출은 LUT-632(미션 이벤트 리스너 — 유저 설정 "탑 자동 착석" 확인도 그쪽)가 한다.
 *
 * <p><b>자동 착석</b>(탑에 들어오지 않고 미션을 시작한 로그인 유저): 입장할 수 있는 공개 층 중 <b>사람이 가장 적은 층</b>(층 전체 인원, NPC 제외,
 * 같으면 낮은 층) → 그 층에서 <b>사람이 가장 적은 방</b>. 자리는 입장 지점. 한 번 앉은 층은 미션이 끝날 때까지 바꾸지 않는다. 입장 레벨이 부족한 층에는 비어
 * 있어도 앉히지 않는다. 방문객은 대상이 아니다.
 */
@Service
@Slf4j
public class TowerSeatService {

    private final TowerFloorRepository towerFloorRepository;
    private final TowerOccupantRepository towerOccupantRepository;
    private final TowerInstanceAllocator allocator;
    private final TowerPopulationService towerPopulationService;
    private final GamificationQueryFacade gamificationQueryFacade;

    public TowerSeatService(
            TowerFloorRepository towerFloorRepository,
            TowerOccupantRepository towerOccupantRepository,
            TowerInstanceAllocator allocator,
            TowerPopulationService towerPopulationService,
            @Lazy GamificationQueryFacade gamificationQueryFacade) {
        this.towerFloorRepository = towerFloorRepository;
        this.towerOccupantRepository = towerOccupantRepository;
        this.allocator = allocator;
        this.towerPopulationService = towerPopulationService;
        this.gamificationQueryFacade = gamificationQueryFacade;
    }

    /** 미션 시작 — 이미 탑에 있으면 그 자리를 미션 중으로, 없으면 자동 착석. 앉힐 층이 없으면 empty */
    @Transactional(transactionManager = "towerTransactionManager")
    public Optional<TowerSeatResult> onMissionStarted(String userId) {
        LocalDateTime now = LocalDateTime.now();
        Optional<TowerOccupant> existing = towerOccupantRepository.findByUserId(userId);
        if (existing.isPresent()) {
            TowerOccupant current = existing.get();
            current.startMission(now);
            return Optional.of(
                    new TowerSeatResult(current.getFloorNumber(), current.getInstanceId(), false));
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
        TowerOccupant seated =
                towerOccupantRepository.save(
                        TowerOccupant.autoSeated(
                                instance.getId(),
                                locked.getFloorNumber(),
                                userId,
                                locked.getEntryPointX(),
                                locked.getEntryPointY(),
                                now));
        log.info(
                "[tower] 자동 착석: userId={} floor={} instanceId={}",
                userId,
                seated.getFloorNumber(),
                seated.getInstanceId());
        return Optional.of(
                new TowerSeatResult(seated.getFloorNumber(), seated.getInstanceId(), true));
    }

    /** 미션 종료(완료·중단·자동 종료 모두) — 자동 착석자는 퇴장, 직접 입장자는 연결돼 있으면 휴식 중으로 남고 끊겨 있으면 퇴장. 반환 = 자리를 비웠는가 */
    @Transactional(transactionManager = "towerTransactionManager")
    public boolean onMissionEnded(String userId, boolean connected) {
        Optional<TowerOccupant> existing = towerOccupantRepository.findByUserId(userId);
        if (existing.isEmpty()) {
            return false;
        }
        TowerOccupant current = existing.get();
        if (current.isAutoSeated() || !connected) {
            allocator.remove(current);
            log.info(
                    "[tower] 미션 종료 퇴장: userId={} floor={} autoSeated={}",
                    userId,
                    current.getFloorNumber(),
                    current.isAutoSeated());
            return true;
        }
        current.endMission();
        return false;
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
