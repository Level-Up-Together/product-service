package io.pinkspider.leveluptogethermvp.userservice.oauth.scheduler;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.global.security.JwtUtil;
import io.pinkspider.leveluptogethermvp.userservice.oauth.application.MultiDeviceTokenService;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

@ExtendWith(MockitoExtension.class)
class TokenMaintenanceSchedulerTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private MultiDeviceTokenService tokenService;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    @Mock
    private SetOperations<String, String> setOperations;

    @InjectMocks
    private TokenMaintenanceScheduler scheduler;

    @Nested
    @DisplayName("cleanupExpiredSessions 테스트")
    class CleanupExpiredSessionsTest {

        @Test
        @DisplayName("만료된 세션을 정리한다")
        void cleansUpExpiredSessions() {
            // given
            when(redisTemplate.keys("session:*")).thenReturn(Set.of("session:abc"));
            when(redisTemplate.opsForHash()).thenReturn(hashOperations);
            when(hashOperations.get("session:abc", "refreshToken")).thenReturn("expired-token");
            when(jwtUtil.validateToken("expired-token")).thenReturn(false);
            when(hashOperations.get("session:abc", "userId")).thenReturn("user-1");

            // when
            scheduler.cleanupExpiredSessions();

            // then
            verify(redisTemplate).delete("session:abc");
        }

        @Test
        @DisplayName("유효한 세션은 삭제하지 않는다")
        void doesNotDeleteValidSessions() {
            // given
            when(redisTemplate.keys("session:*")).thenReturn(Set.of("session:valid"));
            when(redisTemplate.opsForHash()).thenReturn(hashOperations);
            when(hashOperations.get("session:valid", "refreshToken")).thenReturn("valid-token");
            when(jwtUtil.validateToken("valid-token")).thenReturn(true);

            // when
            scheduler.cleanupExpiredSessions();

            // then
            verify(redisTemplate, never()).delete("session:valid");
        }

        @Test
        @DisplayName("세션 키가 없으면 아무것도 하지 않는다")
        void noOpWhenNoSessions() {
            // given
            when(redisTemplate.keys("session:*")).thenReturn(null);

            // when
            scheduler.cleanupExpiredSessions();

            // then
            verify(redisTemplate, never()).delete(anyString());
        }

        @Test
        @DisplayName("refreshToken이 null이면 세션을 삭제한다")
        void deletesSessionWithNullRefreshToken() {
            // given
            when(redisTemplate.keys("session:*")).thenReturn(Set.of("session:null-token"));
            when(redisTemplate.opsForHash()).thenReturn(hashOperations);
            when(hashOperations.get("session:null-token", "refreshToken")).thenReturn(null);
            when(hashOperations.get("session:null-token", "userId")).thenReturn(null);

            // when
            scheduler.cleanupExpiredSessions();

            // then
            verify(redisTemplate).delete("session:null-token");
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
