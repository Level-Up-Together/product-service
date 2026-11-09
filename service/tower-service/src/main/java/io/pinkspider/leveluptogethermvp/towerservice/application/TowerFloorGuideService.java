package io.pinkspider.leveluptogethermvp.towerservice.application;

import io.pinkspider.leveluptogethermvp.towerservice.application.exception.TowerFloorException;
import io.pinkspider.leveluptogethermvp.towerservice.application.exception.TowerFloorGuideException;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerFloorGuideAdminRequest;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerFloorGuideAdminResponse;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerFloorGuideView;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerNpcLineDto;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerFloor;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerFloorGuide;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerNpc;
import io.pinkspider.leveluptogethermvp.towerservice.infrastructure.TowerFloorGuideRepository;
import io.pinkspider.leveluptogethermvp.towerservice.infrastructure.TowerFloorRepository;
import io.pinkspider.leveluptogethermvp.towerservice.infrastructure.TowerFloorVisitRepository;
import io.pinkspider.leveluptogethermvp.towerservice.infrastructure.TowerNpcRepository;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * LUT-734: 층별 첫 방문 안내 — 어드민 저장, 유저 층 목록의 안내, 입장 때 "아직 안 본 안내인가" 판정. 안내가 살아 있는 조건은 켜짐 + 안내 NPC 가 그
 * 층의 공개 NPC + 문장 1개 이상. 본 버전은 유저×층 방문 행({@code tower_floor_visit.guide_seen_version})에 남겨 기기를 바꿔도
 * 다시 뜨지 않는다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(transactionManager = "towerTransactionManager", readOnly = true)
public class TowerFloorGuideService {

    private final TowerFloorGuideRepository guideRepository;
    private final TowerFloorRepository floorRepository;
    private final TowerNpcRepository npcRepository;
    private final TowerFloorVisitRepository visitRepository;

    public TowerFloorGuideAdminResponse get(Long floorId) {
        findFloor(floorId);
        return guideRepository
                .findByFloorId(floorId)
                .map(TowerFloorGuideAdminResponse::from)
                .orElseGet(() -> TowerFloorGuideAdminResponse.empty(floorId));
    }

    @Transactional(transactionManager = "towerTransactionManager")
    public TowerFloorGuideAdminResponse save(Long floorId, TowerFloorGuideAdminRequest request) {
        TowerFloor floor = findFloor(floorId);
        validate(floorId, request);
        TowerFloorGuide source = request.toEntity(floorId);
        boolean reshow = Boolean.TRUE.equals(request.reshow());
        TowerFloorGuide guide =
                guideRepository
                        .findByFloorId(floorId)
                        .map(
                                existing -> {
                                    existing.update(source, reshow);
                                    return existing;
                                })
                        .orElseGet(() -> guideRepository.save(source));
        log.info(
                "탑 첫 방문 안내 저장: floor={}, enabled={}, npc={}, lines={}, version={}, reshow={}",
                floor.getFloorNumber(),
                guide.isEnabledGuide(),
                guide.getNpcId(),
                guide.getLines().size(),
                guide.getVersion(),
                reshow);
        return TowerFloorGuideAdminResponse.from(guide);
    }

    /** 유저 층 목록 — 층 ID → 살아 있는 안내(없는 층은 빠진다) */
    public Map<Long, TowerFloorGuideView> activeByFloorIds(Collection<Long> floorIds) {
        if (floorIds.isEmpty()) {
            return Map.of();
        }
        List<TowerFloorGuide> guides =
                guideRepository.findAllByFloorIdIn(floorIds).stream()
                        .filter(g -> g.isEnabledGuide() && g.getNpcId() != null)
                        .toList();
        if (guides.isEmpty()) {
            return Map.of();
        }
        Map<Long, TowerNpc> npcs =
                npcRepository
                        .findAllById(guides.stream().map(TowerFloorGuide::getNpcId).toList())
                        .stream()
                        .collect(Collectors.toMap(TowerNpc::getId, Function.identity()));
        Map<Long, TowerFloorGuideView> result = new HashMap<>();
        for (TowerFloorGuide guide : guides) {
            if (isActive(guide, npcs.get(guide.getNpcId()))) {
                result.put(guide.getFloorId(), TowerFloorGuideView.from(guide));
            }
        }
        return result;
    }

    /**
     * 입장 — 로그인 유저가 이 층의 지금 안내(버전)를 아직 안 봤으면 본 것으로 표시하고 true. 안내가 없거나 이미 봤으면 false. 입장이 방문 행을 먼저 넣은
     * 뒤에 부른다.
     */
    @Transactional(transactionManager = "towerTransactionManager")
    public boolean markSeenIfNew(String userId, TowerFloor floor, LocalDateTime now) {
        return guideRepository
                .findByFloorId(floor.getId())
                .filter(g -> g.isEnabledGuide() && g.getNpcId() != null)
                .filter(g -> isActive(g, npcRepository.findById(g.getNpcId()).orElse(null)))
                .map(
                        g ->
                                visitRepository.markGuideSeen(
                                                userId, floor.getFloorNumber(), g.getVersion(), now)
                                        > 0)
                .orElse(false);
    }

    static boolean isActive(TowerFloorGuide guide, TowerNpc npc) {
        return guide.isEnabledGuide()
                && npc != null
                && npc.isPublicNpc()
                && Objects.equals(npc.getFloorId(), guide.getFloorId())
                && !guide.getLines().isEmpty();
    }

    private void validate(Long floorId, TowerFloorGuideAdminRequest request) {
        boolean enabled = Boolean.TRUE.equals(request.enabled());
        if (request.npcId() != null) {
            boolean onFloor =
                    npcRepository.findByIdAndFloorId(request.npcId(), floorId).isPresent();
            if (!onFloor) {
                throw TowerFloorGuideException.npcInvalid();
            }
        } else if (enabled) {
            throw TowerFloorGuideException.npcInvalid();
        }
        if (enabled && request.safeLines().isEmpty()) {
            throw TowerFloorGuideException.linesRequired();
        }
        for (TowerNpcLineDto line : request.safeLines()) {
            if (!TowerNpcTextRule.fits(line.text())
                    || !TowerNpcTextRule.fits(line.textEn())
                    || !TowerNpcTextRule.fits(line.textJa())
                    || !TowerNpcTextRule.fits(line.textAr())) {
                throw TowerFloorGuideException.lineTooLong();
            }
        }
    }

    private TowerFloor findFloor(Long floorId) {
        return floorRepository.findById(floorId).orElseThrow(TowerFloorException::notFound);
    }
}
