package io.pinkspider.leveluptogethermvp.towerservice.scheduler;

import io.pinkspider.leveluptogethermvp.towerservice.application.TowerSeatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * LUT-632: 미션 중으로 기록된 탑 점유자를 실제 미션 상태와 대조해 끝난 유저를 정리한다. 자정 일괄 MISSED 처리처럼 종료 이벤트가 없는 경로와 이벤트 유실을
 * 메운다. 주기 {@code tower.seat.reconcile-delay-ms}(기본 5분).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TowerSeatReconcileScheduler {

    private final TowerSeatService towerSeatService;

    @Scheduled(
            fixedDelayString = "${tower.seat.reconcile-delay-ms:300000}",
            initialDelayString = "${tower.seat.reconcile-initial-delay-ms:120000}")
    @SchedulerLock(
            name = "TowerSeatReconcileScheduler_reconcile",
            lockAtMostFor = "PT4M",
            lockAtLeastFor = "PT30S")
    public void reconcile() {
        try {
            towerSeatService.reconcile();
        } catch (Exception e) {
            log.error("[tower] 자리 대조 스케줄러 실패", e);
        }
    }
}
