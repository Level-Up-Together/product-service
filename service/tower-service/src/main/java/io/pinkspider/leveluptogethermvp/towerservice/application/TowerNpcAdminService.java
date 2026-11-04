package io.pinkspider.leveluptogethermvp.towerservice.application;

import io.pinkspider.leveluptogethermvp.towerservice.application.exception.TowerFloorException;
import io.pinkspider.leveluptogethermvp.towerservice.application.exception.TowerNpcException;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerNpcAdminRequest;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerNpcAdminResponse;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerNpcLineDto;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerFloor;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerNpc;
import io.pinkspider.leveluptogethermvp.towerservice.infrastructure.TowerFloorRepository;
import io.pinkspider.leveluptogethermvp.towerservice.infrastructure.TowerNpcRepository;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * LUT-693: 어드민 NPC CRUD (층마다 등록). 저장 규칙 — 층당 최대 3명(150402) · 자리는 층 마스크 안(150403, 못 읽으면 150109) · 대사
 * 한 문장 2줄 이내({@link TowerNpcTextRule}, 150404). 수정은 다음 입장(스냅샷)부터 반영된다. 안 쓰게 된 프로필 이미지는 커밋 뒤 지운다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(transactionManager = "towerTransactionManager", readOnly = true)
public class TowerNpcAdminService {

    private final TowerFloorRepository towerFloorRepository;
    private final TowerNpcRepository towerNpcRepository;
    private final FloorMaskChecker floorMaskChecker;
    private final TowerStoredFileCleaner fileCleaner;

    @Value("${tower.floor.mask-check.enabled:true}")
    private boolean maskCheckEnabled = true;

    public List<TowerNpcAdminResponse> list(Long floorId) {
        findFloor(floorId);
        return towerNpcRepository.findAllByFloorIdOrderBySortOrderAscIdAsc(floorId).stream()
                .map(TowerNpcAdminResponse::from)
                .toList();
    }

    @Transactional(transactionManager = "towerTransactionManager")
    public TowerNpcAdminResponse create(Long floorId, TowerNpcAdminRequest request) {
        TowerFloor floor = findFloor(floorId);
        if (towerNpcRepository.countByFloorId(floorId) >= TowerNpc.MAX_PER_FLOOR) {
            throw TowerNpcException.limitExceeded();
        }
        validate(floor, request);
        TowerNpc saved = towerNpcRepository.save(request.toEntity(floorId));
        log.info(
                "탑 NPC 등록: id={}, floor={}, public={}",
                saved.getId(),
                floor.getFloorNumber(),
                saved.isPublicNpc());
        return TowerNpcAdminResponse.from(saved);
    }

    @Transactional(transactionManager = "towerTransactionManager")
    public TowerNpcAdminResponse update(Long floorId, Long npcId, TowerNpcAdminRequest request) {
        TowerFloor floor = findFloor(floorId);
        TowerNpc npc = findNpc(floorId, npcId);
        validate(floor, request);
        String previousImage = npc.getProfileImageUrl();
        npc.update(request.toEntity(floorId));
        fileCleaner.scheduleCleanup(
                TowerStoredFileCleaner.urls(previousImage),
                TowerStoredFileCleaner.urls(npc.getProfileImageUrl()));
        log.info(
                "탑 NPC 수정: id={}, floor={}, public={}",
                npcId,
                floor.getFloorNumber(),
                npc.isPublicNpc());
        return TowerNpcAdminResponse.from(npc);
    }

    @Transactional(transactionManager = "towerTransactionManager")
    public void delete(Long floorId, Long npcId) {
        TowerNpc npc = findNpc(floorId, npcId);
        towerNpcRepository.delete(npc);
        fileCleaner.scheduleCleanup(
                TowerStoredFileCleaner.urls(npc.getProfileImageUrl()), Set.of());
        log.info("탑 NPC 삭제: id={}, floorId={}", npcId, floorId);
    }

    private void validate(TowerFloor floor, TowerNpcAdminRequest request) {
        for (TowerNpcLineDto line : request.lines()) {
            if (!TowerNpcTextRule.fits(line.text())
                    || !TowerNpcTextRule.fits(line.textEn())
                    || !TowerNpcTextRule.fits(line.textJa())
                    || !TowerNpcTextRule.fits(line.textAr())) {
                throw TowerNpcException.lineTooLong();
            }
        }
        validateSeat(floor, request.seatX(), request.seatY());
    }

    private void validateSeat(TowerFloor floor, int x, int y) {
        if (!maskCheckEnabled) {
            log.warn(
                    "탑 NPC 자리 마스크 검사가 꺼져 있다(tower.floor.mask-check.enabled=false): floor={}",
                    floor.getFloorNumber());
            return;
        }
        boolean inside;
        try {
            inside =
                    floorMaskChecker.isWalkable(
                            floor.getFloorMaskUrl(), floor.getBackgroundImageUrl(), x, y);
        } catch (IOException | RuntimeException e) {
            log.warn(
                    "탑 NPC 자리 검사 중 마스크를 읽지 못함: floor={}, mask={}, error={}",
                    floor.getFloorNumber(),
                    floor.getFloorMaskUrl(),
                    e.getMessage());
            throw TowerFloorException.maskUnreadable();
        }
        if (!inside) {
            throw TowerNpcException.seatOutsideMask();
        }
    }

    private TowerFloor findFloor(Long floorId) {
        return towerFloorRepository.findById(floorId).orElseThrow(TowerFloorException::notFound);
    }

    private TowerNpc findNpc(Long floorId, Long npcId) {
        return towerNpcRepository
                .findByIdAndFloorId(npcId, floorId)
                .orElseThrow(TowerNpcException::notFound);
    }
}
