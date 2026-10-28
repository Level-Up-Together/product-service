package io.pinkspider.leveluptogethermvp.missionservice.scheduler;

import io.pinkspider.leveluptogethermvp.missionservice.application.MissionIdempotencyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** LUT-577: 보관 기간(24시간)이 지난 미션 생성 멱등 키를 매시 정리한다. */
@Component
@RequiredArgsConstructor
@Slf4j
public class MissionIdempotencyKeyCleanupScheduler {

    private final MissionIdempotencyService missionIdempotencyService;

    @Scheduled(cron = "0 15 * * * *", zone = "Asia/Seoul")
    @SchedulerLock(
            name = "MissionIdempotencyKeyCleanupScheduler_cleanupExpiredKeys",
            lockAtMostFor = "PT10M",
            lockAtLeastFor = "PT1M")
    public void cleanupExpiredKeys() {
        try {
            missionIdempotencyService.cleanupExpired();
        } catch (Exception e) {
            log.error("미션 생성 멱등 키 정리 실패", e);
        }
    }
}
