package io.pinkspider.leveluptogethermvp.towerservice.application;

import io.pinkspider.leveluptogethermvp.towerservice.application.exception.TowerFloorException;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerFloorAdminRequest;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerFloorAdminResponse;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerFloor;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerNpc;
import io.pinkspider.leveluptogethermvp.towerservice.infrastructure.TowerFloorRepository;
import io.pinkspider.leveluptogethermvp.towerservice.infrastructure.TowerNpcRepository;
import io.pinkspider.leveluptogethermvp.towerservice.presence.TowerFloorMaskCache;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** LUT-629: 어드민 층 CRUD. 저장 규칙은 {@link TowerFloorRuleValidator}. 수정은 즉시 반영(안에 있는 유저는 다음 입장부터). */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(transactionManager = "towerTransactionManager", readOnly = true)
public class TowerFloorAdminService {

    private final TowerFloorRepository towerFloorRepository;
    private final TowerFloorRuleValidator ruleValidator;
    private final TowerFloorMaskCache maskCache;
    private final TowerStoredFileCleaner fileCleaner;
    private final TowerNpcRepository towerNpcRepository;

    public List<TowerFloorAdminResponse> list() {
        return towerFloorRepository.findAllByOrderByFloorNumberAsc().stream()
                .map(TowerFloorAdminResponse::from)
                .toList();
    }

    public TowerFloorAdminResponse get(Long id) {
        return TowerFloorAdminResponse.from(find(id));
    }

    @Transactional(transactionManager = "towerTransactionManager")
    public TowerFloorAdminResponse create(TowerFloorAdminRequest request) {
        TowerFloor floor = request.toEntity();
        List<TowerFloor> after =
                new ArrayList<>(towerFloorRepository.findAllByOrderByFloorNumberAsc());
        after.add(floor);
        ruleValidator.validate(after, floor);
        TowerFloor saved = towerFloorRepository.save(floor);
        log.info(
                "탑 층 등록: id={}, floor={}, public={}",
                saved.getId(),
                saved.getFloorNumber(),
                saved.isPublicFloor());
        return TowerFloorAdminResponse.from(saved);
    }

    @Transactional(transactionManager = "towerTransactionManager")
    public TowerFloorAdminResponse update(Long id, TowerFloorAdminRequest request) {
        TowerFloor floor = find(id);
        String previousBackground = floor.getBackgroundImageUrl();
        String previousMask = floor.getFloorMaskUrl();
        String previousBgm = floor.getBgmUrl();
        floor.update(request.toEntity());
        List<TowerFloor> after = towerFloorRepository.findAllByOrderByFloorNumberAsc();
        if (after.stream().noneMatch(f -> f == floor)) {
            after = new ArrayList<>(after);
            after.add(floor);
        }
        ruleValidator.validate(after, floor);
        maskCache.evict(floor); // LUT-631: 마스크·배경이 바뀌었을 수 있다
        fileCleaner.scheduleCleanup(
                TowerStoredFileCleaner.urls(previousBackground, previousMask, previousBgm),
                TowerStoredFileCleaner.urls(
                        floor.getBackgroundImageUrl(), floor.getFloorMaskUrl(), floor.getBgmUrl()));
        log.info(
                "탑 층 수정: id={}, floor={}, public={}",
                id,
                floor.getFloorNumber(),
                floor.isPublicFloor());
        return TowerFloorAdminResponse.from(floor);
    }

    /** 삭제 뒤 남는 층끼리 규칙(공개·비로그인 연속, 레벨 순서)이 깨지면 거부한다 */
    @Transactional(transactionManager = "towerTransactionManager")
    public void delete(Long id) {
        TowerFloor floor = find(id);
        List<TowerFloor> remaining =
                towerFloorRepository.findAllByOrderByFloorNumberAsc().stream()
                        .filter(f -> !f.getId().equals(id))
                        .toList();
        ruleValidator.validate(remaining, null);
        // LUT-693: 층의 NPC 는 DB 가 함께 지운다(ON DELETE CASCADE) — 그 이미지도 정리 대상
        Set<String> files =
                TowerStoredFileCleaner.urls(
                        floor.getBackgroundImageUrl(), floor.getFloorMaskUrl(), floor.getBgmUrl());
        for (TowerNpc npc : towerNpcRepository.findAllByFloorId(id)) {
            files.add(npc.getProfileImageUrl());
        }
        towerFloorRepository.delete(floor);
        maskCache.evict(floor);
        fileCleaner.scheduleCleanup(files, Set.of());
        log.info("탑 층 삭제: id={}, floor={}", id, floor.getFloorNumber());
    }

    private TowerFloor find(Long id) {
        return towerFloorRepository.findById(id).orElseThrow(TowerFloorException::notFound);
    }
}
