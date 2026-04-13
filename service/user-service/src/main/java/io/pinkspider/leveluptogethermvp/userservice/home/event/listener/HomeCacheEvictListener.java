package io.pinkspider.leveluptogethermvp.userservice.home.event.listener;

import io.pinkspider.global.event.TitleEquippedEvent;
import io.pinkspider.global.event.UserLevelUpEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 칭호 장착 변경, 레벨업 시 Home MVP 캐시를 즉시 무효화.
 * 경험치 변동은 빈도가 높으므로 TTL(2분)로 자연 갱신.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class HomeCacheEvictListener {

    private final CacheManager redisCacheManager;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleTitleEquipped(TitleEquippedEvent event) {
        evictMvpCaches();
        log.debug("칭호 장착 변경으로 MVP 캐시 무효화: userId={}", event.userId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleUserLevelUp(UserLevelUpEvent event) {
        evictMvpCaches();
        log.debug("레벨업으로 MVP 캐시 무효화: userId={}, newLevel={}", event.userId(), event.newLevel());
    }

    private void evictMvpCaches() {
        evictCache("todayPlayers");
        evictCache("todayPlayersByCategory");
    }

    private void evictCache(String cacheName) {
        try {
            var cache = redisCacheManager.getCache(cacheName);
            if (cache != null) {
                cache.clear();
            }
        } catch (Exception e) {
            log.warn("캐시 무효화 실패: cacheName={}", cacheName, e);
        }
    }
}
