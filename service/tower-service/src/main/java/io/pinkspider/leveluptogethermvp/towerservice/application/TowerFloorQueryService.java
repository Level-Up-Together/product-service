package io.pinkspider.leveluptogethermvp.towerservice.application;

import io.pinkspider.global.facade.GamificationQueryFacade;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerFloorListResponse;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerFloorResponse;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerFloor;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerUserState;
import io.pinkspider.leveluptogethermvp.towerservice.infrastructure.TowerFloorRepository;
import io.pinkspider.leveluptogethermvp.towerservice.infrastructure.TowerUserStateRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * LUT-629: 유저용 층 목록. 공개 층만 층 번호 순. 잠금은 서버가 판정한다(프론트만 막으면 우회된다) — 로그인 유저는 레벨 부족, 방문객은 비로그인 비허용 층. 기본
 * 입장 층 = 마지막으로 머문 층(입장 가능할 때), 아니면 입장 가능한 가장 낮은 층.
 */
@Service
@Slf4j
@Transactional(transactionManager = "towerTransactionManager", readOnly = true)
public class TowerFloorQueryService {

    private final TowerFloorRepository towerFloorRepository;
    private final TowerUserStateRepository towerUserStateRepository;
    private final GamificationQueryFacade gamificationQueryFacade;
    private final TowerPopulationService towerPopulationService;

    public TowerFloorQueryService(
            TowerFloorRepository towerFloorRepository,
            TowerUserStateRepository towerUserStateRepository,
            @Lazy GamificationQueryFacade gamificationQueryFacade,
            TowerPopulationService towerPopulationService) {
        this.towerFloorRepository = towerFloorRepository;
        this.towerUserStateRepository = towerUserStateRepository;
        this.gamificationQueryFacade = gamificationQueryFacade;
        this.towerPopulationService = towerPopulationService;
    }

    public TowerFloorListResponse listForUser(@Nullable String userId) {
        List<TowerFloor> floors = towerFloorRepository.findAllByIsPublicTrueOrderByFloorNumberAsc();
        Integer level = userId != null ? resolveLevel(userId) : null;
        Map<Integer, Long> population = towerPopulationService.byFloor();
        List<TowerFloorResponse> responses = new ArrayList<>(floors.size());
        Integer firstOpen = null;
        for (TowerFloor floor : floors) {
            boolean locked = isLocked(floor, userId, level);
            responses.add(
                    TowerFloorResponse.from(
                            floor, locked, population.getOrDefault(floor.getFloorNumber(), 0L)));
            if (!locked && firstOpen == null) {
                firstOpen = floor.getFloorNumber();
            }
        }

        Integer defaultFloor = firstOpen;
        if (userId != null) {
            Integer last =
                    towerUserStateRepository
                            .findById(userId)
                            .map(TowerUserState::getLastFloorNumber)
                            .orElse(null);
            if (last != null
                    && responses.stream()
                            .anyMatch(r -> r.floorNumber().equals(last) && !r.locked())) {
                defaultFloor = last;
            }
        }
        return new TowerFloorListResponse(responses, defaultFloor);
    }

    static boolean isLocked(TowerFloor floor, @Nullable String userId, @Nullable Integer level) {
        if (userId == null) {
            return !floor.isGuestAllowed();
        }
        return floor.getEntryLevel() != null && (level == null || level < floor.getEntryLevel());
    }

    private Integer resolveLevel(String userId) {
        try {
            return gamificationQueryFacade.getUserLevel(userId);
        } catch (Exception e) {
            log.warn("탑 층 목록 레벨 조회 실패 — 레벨 1 로 판정: userId={}, error={}", userId, e.getMessage());
            return 1;
        }
    }
}
