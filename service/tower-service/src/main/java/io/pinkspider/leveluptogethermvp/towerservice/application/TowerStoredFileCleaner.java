package io.pinkspider.leveluptogethermvp.towerservice.application;

import io.pinkspider.leveluptogethermvp.towerservice.application.image.TowerImageStorageService;
import io.pinkspider.leveluptogethermvp.towerservice.infrastructure.TowerFloorRepository;
import io.pinkspider.leveluptogethermvp.towerservice.infrastructure.TowerNpcRepository;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * LUT-635/637/693/725: 층·NPC 수정·삭제로 더는 쓰지 않는 파일(우리 저장소가 발급한 배경·마스크·배경 음악·NPC 이미지만)을 커밋 뒤에 지운다. 다른
 * 층이나 NPC 가 같은 URL 을 쓰면 남긴다. 롤백되면 아무것도 지우지 않는다(트랜잭션 밖이면 즉시).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TowerStoredFileCleaner {

    private final TowerImageStorageService imageStorage;
    private final TowerFloorRepository towerFloorRepository;
    private final TowerNpcRepository towerNpcRepository;

    /** null(배경 음악 없음 등)은 뺀다 — Set.of 는 null 을 못 받는다 */
    public static Set<String> urls(String... values) {
        Set<String> set = new LinkedHashSet<>();
        for (String v : values) {
            if (v != null) set.add(v);
        }
        return set;
    }

    public void scheduleCleanup(Set<String> previous, Set<String> stillUsed) {
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
                        if (isStillReferenced(url)) {
                            log.info("탑 파일 유지(다른 층·NPC 가 사용): {}", url);
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

    private boolean isStillReferenced(String url) {
        return towerFloorRepository.countByBackgroundImageUrlOrFloorMaskUrlOrBgmUrl(url, url, url)
                        > 0
                || towerFloorRepository.countByOcclusionMaskUrl(url) > 0
                || towerFloorRepository.countEffectLayersByMaskUrl(url) > 0
                || towerNpcRepository.countByProfileImageUrl(url) > 0;
    }
}
