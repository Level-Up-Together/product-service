package io.pinkspider.leveluptogethermvp.towerservice.application;

import io.pinkspider.leveluptogethermvp.towerservice.application.exception.TowerFloorException;
import io.pinkspider.leveluptogethermvp.towerservice.application.image.TowerImageStorageService;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerFloorAdminRequest;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerFloorAdminResponse;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerFloor;
import io.pinkspider.leveluptogethermvp.towerservice.infrastructure.TowerFloorRepository;
import io.pinkspider.leveluptogethermvp.towerservice.presence.TowerFloorMaskCache;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** LUT-629: 어드민 층 CRUD. 저장 규칙은 {@link TowerFloorRuleValidator}. 수정은 즉시 반영(안에 있는 유저는 다음 입장부터). */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(transactionManager = "towerTransactionManager", readOnly = true)
public class TowerFloorAdminService {

    private final TowerFloorRepository towerFloorRepository;
    private final TowerFloorRuleValidator ruleValidator;
    private final TowerFloorMaskCache maskCache;
    private final TowerImageStorageService imageStorage;

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
        floor.update(request.toEntity());
        List<TowerFloor> after = towerFloorRepository.findAllByOrderByFloorNumberAsc();
        if (after.stream().noneMatch(f -> f == floor)) {
            after = new ArrayList<>(after);
            after.add(floor);
        }
        ruleValidator.validate(after, floor);
        maskCache.evict(floor); // LUT-631: 마스크·배경이 바뀌었을 수 있다
        scheduleImageCleanup(
                Set.of(previousBackground, previousMask),
                Set.of(floor.getBackgroundImageUrl(), floor.getFloorMaskUrl()));
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
        towerFloorRepository.delete(floor);
        maskCache.evict(floor);
        scheduleImageCleanup(
                Set.of(floor.getBackgroundImageUrl(), floor.getFloorMaskUrl()), Set.of());
        log.info("탑 층 삭제: id={}, floor={}", id, floor.getFloorNumber());
    }

    /**
     * LUT-635: 수정·삭제로 더는 쓰지 않는 이미지(우리 저장소가 발급한 것만)를 커밋 뒤에 지운다. 다른 층이 같은 URL 을 쓰면 남긴다. 롤백되면 아무것도 지우지
     * 않는다(트랜잭션 밖이면 즉시).
     */
    private void scheduleImageCleanup(Set<String> previous, Set<String> stillUsed) {
        Set<String> candidates = new LinkedHashSet<>();
        for (String url : previous) {
            if (url != null && !stillUsed.contains(url) && imageStorage.isOwned(url)) {
                candidates.add(url);
            }
        }
        if (candidates.isEmpty()) {
            return;
        }
        Runnable cleanup =
                () -> {
                    for (String url : candidates) {
                        if (towerFloorRepository.countByBackgroundImageUrlOrFloorMaskUrl(url, url)
                                > 0) {
                            log.info("탑 층 이미지 유지(다른 층이 사용): {}", url);
                            continue;
                        }
                        imageStorage.delete(url);
                    }
                };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            cleanup.run();
                        }
                    });
        } else {
            cleanup.run();
        }
    }

    private TowerFloor find(Long id) {
        return towerFloorRepository.findById(id).orElseThrow(TowerFloorException::notFound);
    }
}
