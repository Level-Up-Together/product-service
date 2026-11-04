package io.pinkspider.leveluptogethermvp.towerservice.presence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class TowerPresenceStoreTest {

    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);

    @SuppressWarnings("unchecked")
    private final HashOperations<String, Object, Object> hash = mock(HashOperations.class);

    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> value = mock(ValueOperations.class);

    @SuppressWarnings("unchecked")
    private final org.springframework.data.redis.core.SetOperations<String, String> set =
            mock(org.springframework.data.redis.core.SetOperations.class);

    private final ObjectMapper om = new ObjectMapper().registerModule(new JavaTimeModule());
    private TowerPresenceStore store;

    private final TowerPresence presence =
            new TowerPresence(
                    7L,
                    9L,
                    1,
                    "u1",
                    "DIRECT",
                    null,
                    "닉",
                    null,
                    3,
                    List.of(),
                    10,
                    20,
                    "REST",
                    true,
                    false,
                    LocalDateTime.of(2026, 11, 3, 12, 0),
                    null);

    @BeforeEach
    void setUp() {
        when(redis.opsForHash()).thenReturn(hash);
        when(redis.opsForValue()).thenReturn(value);
        when(redis.opsForSet()).thenReturn(set);
        store = new TowerPresenceStore(redis, om);
    }

    @Test
    @DisplayName("presence 는 인스턴스 해시에 점유자 ID 필드로 저장·조회·삭제·목록")
    void hashRoundTrip() {
        store.save(presence);
        ArgumentCaptor<Object> json = ArgumentCaptor.forClass(Object.class);
        verify(hash).put(eq("tower:presence:9"), eq("7"), json.capture());
        assertThat(json.getValue().toString())
                .contains("\"occupant_id\":7")
                .contains("\"nickname\":\"닉\"");

        when(hash.get("tower:presence:9", "7")).thenReturn(json.getValue());
        assertThat(store.find(9L, 7L)).contains(presence);
        when(hash.get("tower:presence:9", "8")).thenReturn(null);
        assertThat(store.find(9L, 8L)).isEmpty();

        when(hash.entries("tower:presence:9"))
                .thenReturn(Map.of("7", json.getValue(), "8", "not json"));
        assertThat(store.listByInstance(9L)).containsExactly(presence);

        store.delete(9L, 7L);
        verify(hash).delete("tower:presence:9", "7");
    }

    @Test
    @DisplayName("세션 묶기·풀기: 점유자의 현재 세션일 때만 점유자 키를 지우고 묶음을 돌려준다 (재접속 뒤 옛 세션 끊김 무시)")
    void sessionBinding() {
        store.bindSession("s1", new TowerSessionBinding(9L, 7L));
        verify(value).set(eq("tower:ws:session:s1"), eq("9:7"), any());
        verify(value).set(eq("tower:ws:occupant:7"), eq("s1"), any());

        when(value.get("tower:ws:session:s1")).thenReturn("9:7");
        when(value.get("tower:ws:occupant:7")).thenReturn("s1");
        assertThat(store.findBinding("s1")).contains(new TowerSessionBinding(9L, 7L));
        assertThat(store.findSessionOfOccupant(7L)).contains("s1");
        assertThat(store.unbindSession("s1")).contains(new TowerSessionBinding(9L, 7L));
        verify(redis).delete("tower:ws:occupant:7");

        when(value.get("tower:ws:session:old")).thenReturn("9:7");
        when(value.get("tower:ws:occupant:7")).thenReturn("s2");
        assertThat(store.unbindSession("old")).isEmpty();

        when(value.get("tower:ws:session:none")).thenReturn(null);
        assertThat(store.unbindSession("none")).isEmpty();
        assertThat(TowerSessionBinding.decode("bad")).isNull();
        assertThat(TowerSessionBinding.decode("a:b")).isNull();
        assertThat(TowerSessionBinding.decode(null)).isNull();

        store.unbindOccupant(7L);
        verify(redis).delete("tower:ws:session:s2");
    }

    @Test
    @DisplayName("Redis 장애는 로그만 — 빈 결과(fail-open)")
    void redisDown() {
        when(hash.get(anyString(), any())).thenThrow(new RuntimeException("down"));
        when(hash.entries(anyString())).thenThrow(new RuntimeException("down"));
        when(value.get(anyString())).thenThrow(new RuntimeException("down"));
        assertThat(store.find(9L, 7L)).isEmpty();
        assertThat(store.listByInstance(9L)).isEmpty();
        assertThat(store.findBinding("s")).isEmpty();
        assertThat(store.findSessionOfOccupant(7L)).isEmpty();
        assertThat(store.unbindSession("s")).isEmpty();
        store.unbindOccupant(7L);
        store.delete(9L, 7L);
        store.bindSession("s", new TowerSessionBinding(9L, 7L));
    }

    @Test
    @DisplayName("LUT-701: 세션을 묶으면 연결 집합에 넣고, 이 세션이 현재 세션일 때만 풀며 빼고, 점유자 정리도 뺀다 · 연결 수·목록")
    void connectedSet() {
        store.bindSession("s1", new TowerSessionBinding(9L, 7L));
        verify(set).add("tower:ws:connected", "7");

        when(value.get("tower:ws:session:s1")).thenReturn("9:7");
        when(value.get("tower:ws:occupant:7")).thenReturn("s1");
        store.unbindSession("s1");
        verify(set).remove("tower:ws:connected", "7");

        store.unbindOccupant(8L);
        verify(set).remove("tower:ws:connected", "8");
        store.removeConnected(9L);
        verify(set).remove("tower:ws:connected", "9");

        when(set.size("tower:ws:connected")).thenReturn(4L);
        assertThat(store.connectedCount()).isEqualTo(4L);
        when(set.members("tower:ws:connected")).thenReturn(java.util.Set.of("1", "x", "2"));
        assertThat(store.connectedOccupantIds()).containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    @DisplayName("LUT-701: 연결 집합을 못 읽으면 0·빈 목록(fail-open), 비면 0")
    void connectedSetFailures() {
        when(set.size("tower:ws:connected")).thenReturn(null);
        assertThat(store.connectedCount()).isZero();
        when(set.members("tower:ws:connected")).thenReturn(null);
        assertThat(store.connectedOccupantIds()).isEmpty();
        when(set.size("tower:ws:connected")).thenThrow(new IllegalStateException("redis"));
        assertThat(store.connectedCount()).isZero();
        when(set.members("tower:ws:connected")).thenThrow(new IllegalStateException("redis"));
        assertThat(store.connectedOccupantIds()).isEmpty();
        when(set.remove(any(), any())).thenThrow(new IllegalStateException("redis"));
        store.removeConnected(1L);
    }
}
