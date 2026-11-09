package io.pinkspider.leveluptogethermvp.towerservice.application;

import io.pinkspider.leveluptogethermvp.towerservice.application.exception.TowerFloorException;
import io.pinkspider.leveluptogethermvp.towerservice.application.pixel.TowerPixelArtService;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerFloorAdminRequest;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerFloorAdminResponse;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerPixelArtAdminDto;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerFloor;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerFloorPixelArt;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerNpc;
import io.pinkspider.leveluptogethermvp.towerservice.infrastructure.TowerFloorRepository;
import io.pinkspider.leveluptogethermvp.towerservice.infrastructure.TowerNpcRepository;
import io.pinkspider.leveluptogethermvp.towerservice.presence.TowerFloorMaskCache;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
    private final TowerPixelArtService pixelArtService;

    public List<TowerFloorAdminResponse> list() {
        List<TowerFloor> floors = towerFloorRepository.findAllByOrderByFloorNumberAsc();
        Map<Long, TowerFloorPixelArt> pixel =
                pixelArtService.byFloorIds(floors.stream().map(TowerFloor::getId).toList());
        return floors.stream()
                .map(f -> TowerFloorAdminResponse.from(f, pixel.get(f.getId())))
                .toList();
    }

    public TowerFloorAdminResponse get(Long id) {
        TowerFloor floor = find(id);
        return TowerFloorAdminResponse.from(floor, pixelOf(id));
    }

    /** LUT-727: 층의 픽셀아트 행 (아직 요청 전이면 null) */
    private TowerFloorPixelArt pixelOf(Long floorId) {
        return floorId == null ? null : pixelArtService.byFloorIds(List.of(floorId)).get(floorId);
    }

    /** LUT-727/728: 픽셀아트 재생성 (설정은 넘긴 것만 바꾼다) */
    @Transactional(transactionManager = "towerTransactionManager")
    public TowerPixelArtAdminDto regeneratePixelArt(Long id, Integer colors, Integer sharpness) {
        TowerFloor floor = find(id);
        return TowerPixelArtAdminDto.from(
                pixelArtService.regenerate(id, colors, sharpness), floor.getBackgroundImageUrl());
    }

    @Transactional(transactionManager = "towerTransactionManager")
    public TowerFloorAdminResponse create(TowerFloorAdminRequest request) {
        TowerFloor floor = request.toEntity();
        List<TowerFloor> after =
                new ArrayList<>(towerFloorRepository.findAllByOrderByFloorNumberAsc());
        after.add(floor);
        ruleValidator.validate(after, floor);
        TowerFloor saved = towerFloorRepository.save(floor);
        // LUT-727: 원본을 등록하면 커밋 뒤 픽셀 배경 2종을 비동기로 만든다
        pixelArtService.ensureFor(saved);
        log.info(
                "탑 층 등록: id={}, floor={}, public={}",
                saved.getId(),
                saved.getFloorNumber(),
                saved.isPublicFloor());
        return TowerFloorAdminResponse.from(saved, pixelOf(saved.getId()));
    }

    @Transactional(transactionManager = "towerTransactionManager")
    public TowerFloorAdminResponse update(Long id, TowerFloorAdminRequest request) {
        TowerFloor floor = find(id);
        String previousBackground = floor.getBackgroundImageUrl();
        String previousMask = floor.getFloorMaskUrl();
        String previousBgm = floor.getBgmUrl();
        Set<String> previousEffectMasks = effectMasks(floor);
        TowerFloor source = request.toEntity();
        floor.update(source);
        // LUT-725: 효과·앞 가림은 보낸 것만 바꾼다(null = 그대로 — 구 어드민·패스스루 호환)
        if (request.occlusionMaskUrl() != null) {
            floor.updateOcclusionMask(source.getOcclusionMaskUrl());
        }
        if (request.lights() != null) {
            floor.replaceLights(source.getLights());
        }
        if (request.effectLayers() != null) {
            floor.replaceEffectLayers(source.getEffectLayers());
        }
        List<TowerFloor> after = towerFloorRepository.findAllByOrderByFloorNumberAsc();
        if (after.stream().noneMatch(f -> f == floor)) {
            after = new ArrayList<>(after);
            after.add(floor);
        }
        ruleValidator.validate(after, floor);
        maskCache.evict(floor); // LUT-631: 마스크·배경이 바뀌었을 수 있다
        pixelArtService.ensureFor(floor); // LUT-727: 원본이 바뀌었으면(또는 결과가 없으면) 다시 만든다
        Set<String> previous =
                TowerStoredFileCleaner.urls(previousBackground, previousMask, previousBgm);
        previous.addAll(previousEffectMasks);
        Set<String> stillUsed =
                TowerStoredFileCleaner.urls(
                        floor.getBackgroundImageUrl(), floor.getFloorMaskUrl(), floor.getBgmUrl());
        stillUsed.addAll(effectMasks(floor));
        fileCleaner.scheduleCleanup(previous, stillUsed);
        log.info(
                "탑 층 수정: id={}, floor={}, public={}",
                id,
                floor.getFloorNumber(),
                floor.isPublicFloor());
        return TowerFloorAdminResponse.from(floor, pixelOf(id));
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
        files.addAll(effectMasks(floor)); // LUT-725: 앞 가림·효과 마스크도
        files.addAll(pixelArtService.imageUrlsOf(id)); // LUT-727: 픽셀 배경(행은 DB 가 함께 지운다)
        for (TowerNpc npc : towerNpcRepository.findAllByFloorId(id)) {
            files.add(npc.getProfileImageUrl());
            if (npc.getCard() != null && npc.getCard().getImageUrl() != null) {
                files.add(npc.getCard().getImageUrl()); // LUT-733: 창 이미지도
            }
        }
        towerFloorRepository.delete(floor);
        maskCache.evict(floor);
        fileCleaner.scheduleCleanup(files, Set.of());
        log.info("탑 층 삭제: id={}, floor={}", id, floor.getFloorNumber());
    }

    /** LUT-725: 층이 쓰는 앞 가림·효과 레이어 마스크 */
    static Set<String> effectMasks(TowerFloor floor) {
        Set<String> masks = TowerStoredFileCleaner.urls(floor.getOcclusionMaskUrl());
        floor.getEffectLayers().forEach(layer -> masks.add(layer.getMaskUrl()));
        return masks;
    }

    private TowerFloor find(Long id) {
        return towerFloorRepository.findById(id).orElseThrow(TowerFloorException::notFound);
    }
}
