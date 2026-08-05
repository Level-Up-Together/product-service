package io.pinkspider.leveluptogethermvp.userservice.oauth.scheduler;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.leveluptogethermvp.userservice.oauth.application.MultiDeviceTokenService;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

@ExtendWith(MockitoExtension.class)
class TokenMaintenanceSchedulerTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private MultiDeviceTokenService tokenService;

    @Mock
    private SetOperations<String, String> setOperations;

    @InjectMocks
    private TokenMaintenanceScheduler scheduler;

    @Nested
    @DisplayName("cleanupExpiredSessions 테스트")
    class CleanupExpiredSessionsTest {

        // LUT-323 회귀 방지: 만료 판정을 스케줄러가 자체 구현(JWT 파싱 검증)하면
        // QA-231 해시 저장 세션을 전부 오삭제한다 — 반드시 서비스 로직에 위임해야 한다.
        @Test
        @DisplayName("만료 세션 정리를 MultiDeviceTokenService에 위임한다")
        void delegatesCleanupToTokenService() {
            // given
            when(tokenService.cleanupExpiredSessions()).thenReturn(3);

            // when
            scheduler.cleanupExpiredSessions();

            // then
            verify(tokenService).cleanupExpiredSessions();
        }

        @Test
        @DisplayName("정리 중 예외가 발생해도 스케줄러는 전파하지 않는다")
        void swallowsExceptionFromCleanup() {
            // given
            when(tokenService.cleanupExpiredSessions()).thenThrow(new RuntimeException("redis down"));

            // when - no exception
            scheduler.cleanupExpiredSessions();

            // then
            verify(tokenService).cleanupExpiredSessions();
        }
    }

    @Nested
    @DisplayName("cleanupOrphanedUserSessions 테스트")
    class CleanupOrphanedUserSessionsTest {

        @Test
        @DisplayName("고아 세션 참조를 정리한다")
        void cleansUpOrphanedSessions() {
            // given
            when(redisTemplate.keys("userSessions:*")).thenReturn(Set.of("userSessions:user-1"));
            when(redisTemplate.opsForSet()).thenReturn(setOperations);
            when(setOperations.members("userSessions:user-1")).thenReturn(Set.of("session:orphan"));
            when(redisTemplate.hasKey("session:orphan")).thenReturn(false);
            when(setOperations.size("userSessions:user-1")).thenReturn(0L);

            // when
            scheduler.cleanupOrphanedUserSessions();

            // then
            verify(setOperations).remove("userSessions:user-1", "session:orphan");
            verify(redisTemplate).delete("userSessions:user-1");
        }

        @Test
        @DisplayName("유효한 세션 참조는 유지한다")
        void keepsValidSessionReferences() {
            // given
            when(redisTemplate.keys("userSessions:*")).thenReturn(Set.of("userSessions:user-1"));
            when(redisTemplate.opsForSet()).thenReturn(setOperations);
            when(setOperations.members("userSessions:user-1")).thenReturn(Set.of("session:valid"));
            when(redisTemplate.hasKey("session:valid")).thenReturn(true);
            when(setOperations.size("userSessions:user-1")).thenReturn(1L);

            // when
            scheduler.cleanupOrphanedUserSessions();

            // then
            verify(setOperations, never()).remove(anyString(), anyString());
        }

        @Test
        @DisplayName("키가 없으면 아무것도 하지 않는다")
        void noOpWhenNoKeys() {
            // given
            when(redisTemplate.keys("userSessions:*")).thenReturn(null);

            // when
            scheduler.cleanupOrphanedUserSessions();

            // then - no exception
        }
    }
}
