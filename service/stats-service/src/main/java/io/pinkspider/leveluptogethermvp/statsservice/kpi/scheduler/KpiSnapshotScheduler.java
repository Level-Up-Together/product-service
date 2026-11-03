package io.pinkspider.leveluptogethermvp.statsservice.kpi.scheduler;

import io.pinkspider.leveluptogethermvp.statsservice.kpi.application.KpiRebuildService;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.KpiDates;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.dto.KpiRebuildResponse;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * LUT-601: 매일 00:40 KST — 활동 flush(00:20, LUT-598) 뒤에 어제 KPI 스냅샷과 코호트를 계산한다. 멱등이라 재실행·수동 rebuild 와
 * 충돌하지 않는다.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class KpiSnapshotScheduler {

    private final KpiRebuildService rebuildService;

    @Scheduled(cron = "0 40 0 * * *", zone = "Asia/Seoul")
    @SchedulerLock(
            name = "KpiSnapshotScheduler_buildDailySnapshot",
            lockAtMostFor = "PT30M",
            lockAtLeastFor = "PT1M")
    public void buildDailySnapshot() {
        buildDailySnapshot(KpiDates.today());
    }

    void buildDailySnapshot(LocalDate today) {
        try {
            KpiRebuildResponse result = rebuildService.runDaily(today);
            log.info("KPI 일별 스냅샷 완료: {}", result);
        } catch (Exception e) {
            log.error("KPI 일별 스냅샷 실패: today={}", today, e);
        }
    }
}
