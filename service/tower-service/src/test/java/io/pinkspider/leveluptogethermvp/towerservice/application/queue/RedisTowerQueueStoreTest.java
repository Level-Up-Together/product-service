package io.pinkspider.leveluptogethermvp.towerservice.application.queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.leveluptogethermvp.towerservice.presence.TowerPresenceStore;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;

class RedisTowerQueueStoreTest {

    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);

    @SuppressWarnings("unchecked")
    private final HashOperations<String, Object, Object> hash = mock(HashOperations.class);

    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> value = mock(ValueOperations.class);

    @SuppressWarnings("unchecked")
    private final ZSetOperations<String, String> zset = mock(ZSetOperations.class);

    private final TowerPresenceStore presence = mock(TowerPresenceStore.class);
    private RedisTowerQueueStore store;

    private final TowerQueueTicket user = new TowerQueueTicket("t-1", "u1", 2, 100L, 150L, null);
    private final TowerQueueTicket visitor = new TowerQueueTicket("t-2", null, 1, 200L, 210L, null);

    @BeforeEach
    void setUp() {
        when(redis.opsForHash()).thenReturn(hash);
        when(redis.opsForValue()).thenReturn(value);
        when(redis.opsForZSet()).thenReturn(zset);
        store = new RedisTowerQueueStore(redis, presence);
    }

    @Test
    @DisplayName("줄 서기: 표 해시(TTL 1시간) + 줄(점수 = 줄 선 시각) + 로그인 유저 → 표 연결 · 방문객은 유저 연결 없음")
    void enqueue() {
        store.enqueue(user);
        verify(hash).putAll(eq("tower:queue:ticket:t-1"), anyMap());
        verify(redis).expire("tower:queue:ticket:t-1", Duration.ofHours(1));
        verify(zset).add("tower:queue:wait:user", "t-1", 100L);
        verify(value).set("tower:queue:user:u1", "t-1", Duration.ofHours(1));

        store.enqueue(visitor);
        verify(zset).add("tower:queue:wait:visitor", "t-2", 200L);
        verify(value, never()).set(eq("tower:queue:user:null"), any(), any(Duration.class));
    }

    @Test
    @DisplayName("표 읽기: 해시 → 표(빈 값 = null) · 없거나 깨졌으면 empty")
    void find() {
        when(hash.entries("tower:queue:ticket:t-1"))
                .thenReturn(
                        Map.of(
                                "user", "u1", "floor", "2", "enq", "100", "seen", "150", "until",
                                ""));
        assertThat(store.find("t-1")).contains(user);
        when(hash.entries("tower:queue:ticket:t-3"))
                .thenReturn(
                        Map.of("user", "", "floor", "1", "enq", "1", "seen", "2", "until", "99"));
        assertThat(store.find("t-3").orElseThrow().admittedUntil()).isEqualTo(99L);
        assertThat(store.find("t-3").orElseThrow().userId()).isNull();
        when(hash.entries("tower:queue:ticket:bad")).thenReturn(Map.of("floor", "x"));
        assertThat(store.find("bad")).isEmpty();
        when(hash.entries("tower:queue:ticket:none")).thenReturn(Map.of());
        assertThat(store.find("none")).isEmpty();
    }

    @Test
    @DisplayName("지우기: 줄·입장 가능·해시 · 유저 연결은 그 표일 때만")
    void delete() {
        when(value.get("tower:queue:user:u1")).thenReturn("t-1");
        store.delete(user);
        verify(zset).remove("tower:queue:wait:user", "t-1");
        verify(zset).remove("tower:queue:wait:visitor", "t-1");
        verify(zset).remove("tower:queue:admitted", "t-1");
        verify(redis).delete("tower:queue:ticket:t-1");
        verify(redis).delete("tower:queue:user:u1");

        when(value.get("tower:queue:user:u1")).thenReturn("newer");
        store.delete(user);
        verify(redis).delete("tower:queue:user:u1"); // 한 번만
        store.delete(visitor);
    }

    @Test
    @DisplayName("줄 조회·입장 가능 옮기기·마감 계산·잠금·연결 수")
    void queries() {
        when(presence.connectedCount()).thenReturn(5L);
        assertThat(store.connectedCount()).isEqualTo(5L);
        when(value.get("tower:queue:user:u1")).thenReturn("t-1");
        assertThat(store.ticketOfUser("u1")).contains("t-1");
        when(zset.zCard("tower:queue:wait:user")).thenReturn(3L);
        when(zset.zCard("tower:queue:wait:visitor")).thenReturn(null);
        assertThat(store.waitingCount(false)).isEqualTo(3);
        assertThat(store.waitingCount(true)).isZero();
        when(zset.rank("tower:queue:wait:visitor", "t-2")).thenReturn(1L);
        assertThat(store.waitingRank(visitor)).contains(1L);
        Set<String> head = new LinkedHashSet<>(List.of("a", "b"));
        when(zset.range("tower:queue:wait:user", 0, 1)).thenReturn(head);
        assertThat(store.waitingHead(false, 2)).containsExactly("a", "b");
        assertThat(store.waitingHead(false, 0)).isEmpty();
        when(zset.range("tower:queue:wait:visitor", 0, -1)).thenReturn(null);
        assertThat(store.waitingAll(true)).isEmpty();
        when(zset.range("tower:queue:wait:user", 0, -1)).thenReturn(head);
        assertThat(store.waitingAll(false)).containsExactly("a", "b");
        when(zset.range("tower:queue:wait:visitor", 0, 0)).thenReturn(null);
        assertThat(store.waitingHead(true, 1)).isEmpty();

        store.markAdmitted(user, 999L);
        verify(zset).remove("tower:queue:wait:user", "t-1");
        verify(zset).add("tower:queue:admitted", "t-1", 999L);
        when(zset.count("tower:queue:admitted", 500, Double.POSITIVE_INFINITY)).thenReturn(2L);
        assertThat(store.admittedCount(500)).isEqualTo(2);
        when(zset.count("tower:queue:admitted", 600, Double.POSITIVE_INFINITY)).thenReturn(null);
        assertThat(store.admittedCount(600)).isZero();
        when(zset.rangeByScore("tower:queue:admitted", 0, 499)).thenReturn(Set.of("x"));
        assertThat(store.admittedExpired(500)).containsExactly("x");
        when(zset.rangeByScore("tower:queue:admitted", 0, 599)).thenReturn(null);
        assertThat(store.admittedExpired(600)).isEmpty();

        when(value.setIfAbsent("tower:queue:lock", "1", Duration.ofSeconds(2))).thenReturn(true);
        assertThat(store.tryLock(Duration.ofSeconds(2))).isTrue();
        when(value.setIfAbsent("tower:queue:lock", "1", Duration.ofSeconds(3))).thenReturn(null);
        assertThat(store.tryLock(Duration.ofSeconds(3))).isFalse();
        store.unlock();
        verify(redis).delete("tower:queue:lock");
    }
}
