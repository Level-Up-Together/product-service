package io.pinkspider.leveluptogethermvp.userservice.oauth.scheduler;

import io.pinkspider.leveluptogethermvp.userservice.oauth.application.MultiDeviceTokenService;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
@RequiredArgsConstructor
@Slf4j
public class TokenMaintenanceScheduler {

    private final StringRedisTemplate redisTemplate;
    private final MultiDeviceTokenService tokenService;

    // 매일 새벽 2시(KST)에 만료된 세션 정리
    // 만료 판정은 MultiDeviceTokenService 에 위임 — QA-231 해시 저장 세션은 refreshExpiresAt
    // 메타데이터로 판정해야 하며, 스케줄러가 자체 구현(JWT 파싱 검증)을 중복 보유하다가
    // 해시 세션 전체를 매일 오삭제한 사고(LUT-323)의 재발을 막는다.
    @Scheduled(cron = "0 0 2 * * *", zone = "Asia/Seoul")
    @SchedulerLock(name = "TokenMaintenanceScheduler_cleanupExpiredSessions", lockAtMostFor = "PT15M", lockAtLeastFor = "PT1M")
    public void cleanupExpiredSessions() {
        try {
            int cleanedCount = tokenService.cleanupExpiredSessions();
            log.info("Cleaned up {} expired sessions", cleanedCount);
        } catch (Exception e) {
            log.error("Failed to cleanup expired sessions", e);
        }
    }

    // 블랙리스트는 TTL로 자동 만료되므로 별도 정리 불필요
    // 대신 고아 세션(user_sessions에만 남아있는 세션) 정리
    @Scheduled(cron = "0 30 2 * * *", zone = "Asia/Seoul") // 매일 새벽 2시 30분(KST)
    @SchedulerLock(name = "TokenMaintenanceScheduler_cleanupOrphanedUserSessions", lockAtMostFor = "PT15M", lockAtLeastFor = "PT1M")
    public void cleanupOrphanedUserSessions() {
        try {
            Set<String> userSessionKeys = redisTemplate.keys("userSessions:*");
            int cleanedCount = 0;

            if (userSessionKeys != null) {
                for (String userSessionKey : userSessionKeys) {
                    Set<String> sessionKeys = redisTemplate.opsForSet().members(userSessionKey);

                    if (sessionKeys != null) {
                        for (String sessionKey : sessionKeys) {
                            // 세션이 더 이상 존재하지 않으면 user_sessions에서 제거
                            if (Boolean.FALSE.equals(redisTemplate.hasKey(sessionKey))) {
                                redisTemplate.opsForSet().remove(userSessionKey, sessionKey);
                                cleanedCount++;
                            }
                        }
                    }

                    // user_sessions가 비어있으면 키 자체 삭제
                    Long size = redisTemplate.opsForSet().size(userSessionKey);
                    if (size != null && size == 0) {
                        redisTemplate.delete(userSessionKey);
                    }
                }
            }

            if (cleanedCount > 0) {
                log.info("Cleaned up {} orphaned session references", cleanedCount);
            }

        } catch (Exception e) {
            log.error("Failed to cleanup orphaned user sessions", e);
        }
    }
}
