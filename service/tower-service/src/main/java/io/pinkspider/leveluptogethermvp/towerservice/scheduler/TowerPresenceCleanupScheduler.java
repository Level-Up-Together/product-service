package io.pinkspider.leveluptogethermvp.towerservice.scheduler;

import io.pinkspider.leveluptogethermvp.towerservice.presence.TowerConnectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** LUT-631: 방문객 무조작(10분) 퇴장·고아 presence·끊김 유실 정리. 매분, ShedLock */
@Component
@RequiredArgsConstructor
@Slf4j
public class TowerPresenceCleanupScheduler {

    private final TowerConnectionService connectionService;

    @Scheduled(
            fixedDelayString = "${tower.presence.cleanup-delay-ms:60000}",
            initialDelayString = "${tower.presence.cleanup-initial-delay-ms:90000}")
    @SchedulerLock(
            name = "TowerPresenceCleanupScheduler_cleanup",
            lockAtMostFor = "PT50S",
            lockAtLeastFor = "PT10S")
    public void cleanup() {
        try {
            connectionService.cleanup();
        } catch (Exception e) {
            log.error("[tower] presence 정리 스케줄러 실패", e);
        }
    }
}
