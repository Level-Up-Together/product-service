package io.pinkspider.leveluptogethermvp.statsservice.activity.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.leveluptogethermvp.statsservice.activity.domain.UserActivityKeys;
import java.time.Duration;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserActivityRecorder 테스트 (LUT-598)")
class UserActivityRecorderTest {

    private static final LocalDate DATE = LocalDate.of(2026, 10, 23);

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private SetOperations<String, String> setOperations;

    @InjectMocks private UserActivityRecorder recorder;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
    }

    @Test
    @DisplayName("새 멤버면 일별 키·기기 키에 넣고 둘 다 TTL 40일을 건다")
    void recordsNewMemberWithTtl() {
        when(setOperations.add(anyString(), eq("u1"))).thenReturn(1L);

        recorder.record("u1", "ios", DATE);

        verify(setOperations).add("active:2026-10-23", "u1");
        verify(setOperations).add("active:2026-10-23:ios", "u1");
        verify(redisTemplate).expire("active:2026-10-23", Duration.ofDays(40));
        verify(redisTemplate).expire("active:2026-10-23:ios", Duration.ofDays(40));
    }

    @Test
    @DisplayName("이미 들어 있는 멤버(0 반환)거나 결과가 null 이면 TTL 을 다시 걸지 않는다")
    void noTtlWhenAlreadyMember() {
        when(setOperations.add("active:2026-10-23", "u1")).thenReturn(0L);
        when(setOperations.add("active:2026-10-23:web", "u1")).thenReturn(null);

        recorder.record("u1", "web", DATE);

        verify(redisTemplate, never()).expire(anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("기기 종류가 없거나 비어 있으면 일별 키만 기록한다")
    void dailyKeyOnlyWithoutDeviceType() {
        when(setOperations.add(anyString(), eq("u1"))).thenReturn(1L);

        recorder.record("u1", null, DATE);
        recorder.record("u1", " ", DATE);

        verify(setOperations, never()).add(startsWith("active:2026-10-23:"), anyString());
    }

    @Test
    @DisplayName("Redis 예외는 삼키고 로그만 남긴다")
    void swallowsRedisFailure() {
        when(setOperations.add(anyString(), anyString()))
                .thenThrow(new IllegalStateException("connection refused"));

        recorder.record("u1", "web", DATE);
    }

    @Test
    @DisplayName("recordAsync 는 KST 오늘 날짜 키에 기록한다")
    void recordAsyncUsesKstToday() {
        when(setOperations.add(anyString(), eq("u1"))).thenReturn(0L);
        String today = LocalDate.now(UserActivityKeys.KST).toString();

        recorder.recordAsync("u1", "android");

        verify(setOperations).add("active:" + today, "u1");
        verify(setOperations).add("active:" + today + ":android", "u1");
    }
}
