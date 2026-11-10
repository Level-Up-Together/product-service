package io.pinkspider.leveluptogethermvp.towerservice.scheduler;

import io.pinkspider.leveluptogethermvp.towerservice.application.pixel.TowerPixelArtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * LUT-727: 픽셀아트 변환이 대기로 10분 넘게 남은 층(서버가 변환 도중 꺼짐·대기열 넘침)을 다시 보낸다. 주기 {@code
 * tower.pixel.recovery-delay-ms}(기본 10분).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TowerPixelArtRecoveryScheduler {

    private final TowerPixelArtService towerPixelArtService;

    @Scheduled(
            fixedDelayString = "${tower.pixel.recovery-delay-ms:600000}",
            initialDelayString = "${tower.pixel.recovery-initial-delay-ms:300000}")
    @SchedulerLock(
            name = "TowerPixelArtRecoveryScheduler_recover",
            lockAtMostFor = "PT5M",
            lockAtLeastFor = "PT30S")
    public void recover() {
        try {
            towerPixelArtService.recoverStale();
        } catch (Exception e) {
            log.error("[tower] 픽셀아트 대기 복구 실패", e);
        }
    }
}
