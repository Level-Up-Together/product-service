package io.pinkspider.leveluptogethermvp.chatservice.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class DmPresenceServiceTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private DmPresenceService presenceService;

    private static final String USER_ID = "user-1";
    private static final String KEY = DmPresenceService.KEY_PREFIX + USER_ID;

    @BeforeEach
    void setUp() {
        presenceService = new DmPresenceService(stringRedisTemplate);
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("markViewing은 conversationId를 TTL과 함께 기록한다")
    void markViewing_setsWithTtl() {
        presenceService.markViewing(USER_ID, 10L);

        verify(valueOperations).set(KEY, "10", DmPresenceService.VIEWING_TTL);
    }

    @Test
    @DisplayName("isViewing은 기록된 대화방과 일치하면 true")
    void isViewing_matching_returnsTrue() {
        when(valueOperations.get(KEY)).thenReturn("10");

        assertThat(presenceService.isViewing(USER_ID, 10L)).isTrue();
    }

    @Test
    @DisplayName("isViewing은 다른 대화방이면 false")
    void isViewing_differentConversation_returnsFalse() {
        when(valueOperations.get(KEY)).thenReturn("99");

        assertThat(presenceService.isViewing(USER_ID, 10L)).isFalse();
    }

    @Test
    @DisplayName("isViewing은 기록이 없으면 false")
    void isViewing_noRecord_returnsFalse() {
        when(valueOperations.get(KEY)).thenReturn(null);

        assertThat(presenceService.isViewing(USER_ID, 10L)).isFalse();
    }

    @Test
    @DisplayName("isViewing은 Redis 장애 시 false (fail-open: 푸시 발송 유지)")
    void isViewing_redisError_returnsFalse() {
        when(valueOperations.get(KEY)).thenThrow(new RuntimeException("redis down"));

        assertThat(presenceService.isViewing(USER_ID, 10L)).isFalse();
    }

    @Test
    @DisplayName("clearViewing은 같은 대화방일 때만 삭제한다")
    void clearViewing_sameConversation_deletes() {
        when(valueOperations.get(KEY)).thenReturn("10");

        presenceService.clearViewing(USER_ID, 10L);

        verify(stringRedisTemplate).delete(KEY);
    }

    @Test
    @DisplayName("clearViewing은 이미 다른 방으로 이동했으면 삭제하지 않는다")
    void clearViewing_movedToOtherConversation_keeps() {
        when(valueOperations.get(KEY)).thenReturn("99");

        presenceService.clearViewing(USER_ID, 10L);

        verify(stringRedisTemplate, never()).delete(anyString());
    }

    @Test
    @DisplayName("대화방 무관 clearViewing은 무조건 삭제한다 (연결 종료)")
    void clearViewing_withoutConversation_deletesUnconditionally() {
        presenceService.clearViewing(USER_ID);

        verify(stringRedisTemplate).delete(KEY);
    }

    @Test
    @DisplayName("markViewing은 Redis 장애 시 예외를 던지지 않는다")
    void markViewing_redisError_noThrow() {
        org.mockito.Mockito.doThrow(new RuntimeException("redis down"))
            .when(valueOperations)
            .set(anyString(), anyString(), any(Duration.class));

        presenceService.markViewing(USER_ID, 10L);
    }
}
