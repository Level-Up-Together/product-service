package io.pinkspider.leveluptogethermvp.userservice.home.event.listener;

import io.pinkspider.global.event.ItemEquippedEvent;
import io.pinkspider.global.event.TitleEquippedEvent;
import io.pinkspider.global.event.UserLevelUpEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 칭호 장착 변경, 레벨업, 아이템 장착 변경(LUT-427) 시 Home MVP 캐시를 즉시 무효화.
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

    /**
     * LUT-427: 아이템 장착/해제 시 장착 아이템 희귀도(equipped_item_rarities)가 실리는 캐시를 즉시 무효화.
     * 시즌 MVP(seasonMvpData, TTL 10분)에도 같은 데이터가 실리므로 함께 비운다.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleItemEquipped(ItemEquippedEvent event) {
        evictMvpCaches();
        evictCache("seasonMvpData");
        log.debug("아이템 장착 변경으로 MVP·시즌 캐시 무효화: userId={}, shopItemId={}, equipped={}",
            event.userId(), event.shopItemId(), event.equipped());
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
