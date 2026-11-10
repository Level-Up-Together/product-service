package io.pinkspider.leveluptogethermvp.towerservice.application.visitor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class TowerVisitorSessionStoreTest {
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);

    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);

    private TowerVisitorSessionStore store;

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(values);
        store =
                new TowerVisitorSessionStore(
                        redis, new ObjectMapper(), new TowerVisitorProperties());
    }

    @Test
    @DisplayName("LUT-705: 세션은 JSON 으로 24시간 저장·조회 · 없거나 깨졌거나 Redis 오류면 없음 · 저장 오류는 삼킨다")
    void roundTrip() {
        TowerVisitorSession session =
                new TowerVisitorSession("vk", 4821, "1.2.3.4", "d1", "ua", 5L);
        store.save(session);
        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(values)
                .set(eq("tower:visitor:session:vk"), json.capture(), eq(Duration.ofHours(24)));
        assertThat(json.getValue())
                .contains("\"visitor_no\":4821")
                .contains("\"device_id\":\"d1\"");

        when(values.get("tower:visitor:session:vk")).thenReturn(json.getValue());
        assertThat(store.find("vk")).contains(session);
        assertThat(store.find(null)).isEmpty();
        assertThat(store.find(" ")).isEmpty();
        when(values.get("tower:visitor:session:none")).thenReturn(null);
        assertThat(store.find("none")).isEmpty();
        when(values.get("tower:visitor:session:bad")).thenReturn("{not json");
        assertThat(store.find("bad")).isEmpty();
        when(values.get("tower:visitor:session:err")).thenThrow(new IllegalStateException("redis"));
        assertThat(store.find("err")).isEmpty();

        doThrow(new IllegalStateException("redis"))
                .when(values)
                .set(anyString(), anyString(), any(Duration.class));
        store.save(session);
    }
}
