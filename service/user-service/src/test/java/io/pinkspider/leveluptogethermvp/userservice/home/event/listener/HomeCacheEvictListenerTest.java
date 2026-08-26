package io.pinkspider.leveluptogethermvp.userservice.home.event.listener;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.global.event.ItemEquippedEvent;
import io.pinkspider.global.event.TitleEquippedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

@ExtendWith(MockitoExtension.class)
class HomeCacheEvictListenerTest {

    @Mock
    private CacheManager redisCacheManager;

    @Mock
    private Cache cache;

    @InjectMocks
    private HomeCacheEvictListener listener;

    @Test
    @DisplayName("아이템 장착 이벤트 수신 시 todayPlayers·todayPlayersByCategory·seasonMvpData를 비운다 (LUT-427)")
    void handleItemEquipped_evictsMvpAndSeasonCaches() {
        when(redisCacheManager.getCache(anyString())).thenReturn(cache);

        listener.handleItemEquipped(new ItemEquippedEvent("user-1", 3L, true));

        verify(redisCacheManager).getCache("todayPlayers");
        verify(redisCacheManager).getCache("todayPlayersByCategory");
        verify(redisCacheManager).getCache("seasonMvpData");
        verify(cache, times(3)).clear();
    }

    @Test
    @DisplayName("칭호 장착 이벤트는 기존대로 MVP 캐시만 비운다 (시즌 캐시 미대상)")
    void handleTitleEquipped_evictsMvpCachesOnly() {
        when(redisCacheManager.getCache(anyString())).thenReturn(cache);

        listener.handleTitleEquipped(new TitleEquippedEvent("user-1", "용감한 전사", null, null));

        verify(redisCacheManager).getCache("todayPlayers");
        verify(redisCacheManager).getCache("todayPlayersByCategory");
        verify(redisCacheManager, never()).getCache("seasonMvpData");
    }

    @Test
    @DisplayName("캐시 clear 실패는 예외를 전파하지 않는다")
    void handleItemEquipped_clearFailure_swallowed() {
        when(redisCacheManager.getCache(anyString())).thenReturn(cache);
        doThrow(new RuntimeException("redis down")).when(cache).clear();

        listener.handleItemEquipped(new ItemEquippedEvent("user-1", 3L, false)); // 예외 전파 없음
    }
}
