package io.pinkspider.leveluptogethermvp.towerservice.scheduler;

import io.pinkspider.leveluptogethermvp.towerservice.application.queue.TowerQueueService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * LUT-701: 입장 대기열 진행 — 마감 지난 입장 신호·조회가 끊긴 대기표를 버리고 남는 자리만큼 다음 순번에 입장 신호를 준다. 대기 화면 조회도 같은 계산을 하므로 이
 * 스케줄러는 아무도 조회하지 않을 때를 위한 안전망이다. 상한이 없으면 아무것도 하지 않는다. 2초, ShedLock.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TowerQueueScheduler {

    private final TowerQueueService queueService;

    @Scheduled(
            fixedDelayString = "${tower.queue.process-delay-ms:2000}",
            initialDelayString = "${tower.queue.process-initial-delay-ms:30000}")
    @SchedulerLock(
            name = "TowerQueueScheduler_process",
            lockAtMostFor = "PT10S",
            lockAtLeastFor = "PT1S")
    public void process() {
        try {
            queueService.admitPending();
        } catch (Exception e) {
            log.error("[tower] 대기열 스케줄러 실패", e);
        }
    }
}
