package io.pinkspider.leveluptogethermvp.userservice.oauth.scheduler;

import io.pinkspider.leveluptogethermvp.userservice.core.util.JwtUtil;
import io.pinkspider.leveluptogethermvp.userservice.oauth.application.MultiDeviceTokenService;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private final JwtUtil jwtUtil;

    // 매일 새벽 2시에 만료된 세션 정리
    @Scheduled(cron = "0 0 2 * * *")
    public void cleanupExpiredSessions() {
        try {
            Set<String> sessionKeys = redisTemplate.keys("session:*");
            int cleanedCount = 0;

            if (sessionKeys != null) {
                for (String sessionKey : sessionKeys) {
                    String refreshToken = (String) redisTemplate.opsForHash()
                        .get(sessionKey, "refreshToken");

                    if (refreshToken == null || !jwtUtil.validateToken(refreshToken)) {
                        // 만료된 세션 삭제
                        redisTemplate.delete(sessionKey);
                        cleanedCount++;
                    }
                }
            }

            log.info("Cleaned up {} expired sessions", cleanedCount);

        } catch (Exception e) {
            log.error("Failed to cleanup expired sessions", e);
        }
    }

    // 매시간 블랙리스트 정리
    @Scheduled(fixedRate = 3600000) // 1시간
    public void cleanupBlacklist() {
        try {
            Set<String> blacklistKeys = redisTemplate.keys("blacklist:*");
            int cleanedCount = 0;

            if (blacklistKeys != null) {
                for (String key : blacklistKeys) {
                    if (!redisTemplate.hasKey(key)) {
                        cleanedCount++;
                    }
                }
            }

            if (cleanedCount > 0) {
                log.info("Cleaned up {} expired blacklist entries", cleanedCount);
            }

        } catch (Exception e) {
            log.error("Failed to cleanup blacklist", e);
        }
    }
}
